package com.nexo.tv.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nexo.tv.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object XtreamClient {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36 VLC/3.0.18"

    private val hosts = listOf(
        Session.SERVER_ELITE,
        "http://eliteplusec.com",
        "http://eliteplusec.com:80",
        Session.SERVER_NEXO
    )

    private val gson = Gson()

    private fun apiUrl(base: String, action: String?, extra: Map<String, String>, u: String, p: String): String {
        return buildString {
            append(base.trimEnd('/'))
            append("/player_api.php?username=")
            append(u)
            append("&password=")
            append(p)
            if (!action.isNullOrBlank()) {
                append("&action=")
                append(action)
            }
            extra.forEach { (k, v) ->
                append("&")
                append(k)
                append("=")
                append(v)
            }
        }
    }

    private fun tryGet(url: String): String? {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "*/*").build()
        return try {
            Http.client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        } catch (e: Throwable) {
            android.util.Log.w("Xtream", "GET $url -> ${e.message}")
            null
        }
    }

    private suspend fun fetch(action: String? = null, extra: Map<String, String> = emptyMap()): String? =
        withContext(Dispatchers.IO) {
            val u = Session.username
            val p = Session.password
            if (u.isBlank() || p.isBlank()) return@withContext null
            val preferred = Session.server
            val order = (listOf(preferred) + hosts).distinct()
            for (base in order) {
                val body = tryGet(apiUrl(base, action, extra, u, p))
                if (!body.isNullOrBlank()) {
                    if (base != preferred) Session.server = base
                    return@withContext body
                }
            }
            null
        }

    suspend fun login(user: String, pass: String, preferredServer: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val prevU = Session.username
            val prevP = Session.password
            val prevS = Session.server
            Session.login(user, pass)
            if (!preferredServer.isNullOrBlank()) {
                Session.server = preferredServer
            }
            val json = fetch()
            if (json.isNullOrBlank()) {
                Session.login(prevU, prevP)
                Session.server = prevS
                return@withContext false
            }
            val res = runCatching { gson.fromJson(json, LoginResponse::class.java) }.getOrNull()
            val info = res?.userInfo
            val auth = info?.auth?.toString()?.trim()
            val status = info?.status?.trim()
            val badAuth = auth == "0" || auth.equals("false", true)
            val badStatus = status.equals("Disabled", true) ||
                status.equals("Banned", true) ||
                status.equals("Expired", true)
            if (info == null || badAuth || badStatus) {
                Session.login(prevU, prevP)
                Session.server = prevS
                false
            } else {
                android.util.Log.i("Xtream", "login OK server=${Session.server} user=$user")
                true
            }
        }

    suspend fun liveCategories(): List<LiveCategory> {
        val json = fetch("get_live_categories") ?: return emptyList()
        val type = object : TypeToken<List<LiveCategory>>() {}.type
        return runCatching { gson.fromJson<List<LiveCategory>>(json, type) }.getOrNull().orEmpty()
    }

    suspend fun liveChannels(): List<LiveChannel> {
        val json = fetch("get_live_streams") ?: return emptyList()
        val type = object : TypeToken<List<LiveChannel>>() {}.type
        return runCatching { gson.fromJson<List<LiveChannel>>(json, type) }.getOrNull().orEmpty()
    }

    suspend fun movies(): List<VodItem> {
        val json = fetch("get_vod_streams") ?: return emptyList()
        val type = object : TypeToken<List<VodItem>>() {}.type
        return runCatching { gson.fromJson<List<VodItem>>(json, type) }.getOrNull().orEmpty()
    }

    suspend fun series(): List<SeriesItem> {
        val json = fetch("get_series") ?: return emptyList()
        val type = object : TypeToken<List<SeriesItem>>() {}.type
        return runCatching { gson.fromJson<List<SeriesItem>>(json, type) }.getOrNull().orEmpty()
    }

    fun liveUrl(channelId: String): String {
        val u = Session.username
        val p = Session.password
        val id = channelId.substringBefore(".0")
        val base = Session.server.trimEnd('/')
        // ElitePlus / Xtream: .ts suele ir mejor por HTTP
        return "$base/live/$u/$p/$id.ts"
    }

    fun liveCandidates(channelId: String): List<String> {
        val u = Session.username
        val p = Session.password
        val id = channelId.substringBefore(".0")
        val base = Session.server.trimEnd('/')
        return listOf(
            "$base/live/$u/$p/$id.ts",
            "$base/live/$u/$p/$id.m3u8"
        )
    }

    fun movieUrl(id: String, ext: String): String {
        val u = Session.username
        val p = Session.password
        val e = ext.ifBlank { "mp4" }
        return "${Session.server.trimEnd('/')}/movie/$u/$p/${id.substringBefore(".0")}.$e"
    }
}
