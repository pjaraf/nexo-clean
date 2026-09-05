package com.nexo.tv.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nexo.tv.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class RemoteConfig(
    @SerializedName("updatedAt") val updatedAt: String = "",
    @SerializedName("servers") val servers: ServersConfig = ServersConfig(),
    @SerializedName("maintenance") val maintenance: MaintenanceConfig = MaintenanceConfig(),
    @SerializedName("announcement") val announcement: AnnouncementConfig = AnnouncementConfig(),
    @SerializedName("settings") val settings: SettingsConfig = SettingsConfig()
)

data class ServersConfig(
    @SerializedName("primary") val primary: String = "http://eliteplusec.com:8080",
    @SerializedName("backup") val backup: String = "https://nexo.fusionx.cl",
    @SerializedName("fallbacks") val fallbacks: List<String> = listOf("http://eliteplusec.com", "http://eliteplusec.com:80")
)

data class MaintenanceConfig(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("title") val title: String = "Mantenimiento",
    @SerializedName("message") val message: String = "Estamos en mantenimiento."
)

data class AnnouncementConfig(
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("type") val type: String = "info"
)

data class SettingsConfig(
    @SerializedName("liveBufferMs") val liveBufferMs: Int = 350,
    @SerializedName("autoReconnect") val autoReconnect: Boolean = true,
    @SerializedName("featuredChannelId") val featuredChannelId: String = ""
)

object AppConfig {
    private const val TAG = "AppConfig"
    private const val PREFS_NAME = "nexo_remote_config"
    private const val KEY_CACHED_CONFIG = "cached_json"

    const val FIREBASE_URL = "https://nexo-tv-d8766-default-rtdb.firebaseio.com/config.json"

    private val GITHUB_FALLBACK_URLS = listOf(
        "https://raw.githubusercontent.com/pjaraf/nexo-clean/main/nexo-config.json",
        "https://pjaraf.github.io/nexo-clean/nexo-config.json"
    )

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(RemoteConfig) -> Unit>()
    private var listenerStarted = false

    private val sseClient by lazy {
        Http.client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    @Volatile
    var current: RemoteConfig = RemoteConfig()
        private set

    fun addListener(listener: (RemoteConfig) -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
        listener(current)
    }

    fun removeListener(listener: (RemoteConfig) -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHED_CONFIG, null)
        if (!cached.isNullOrBlank()) {
            try {
                current = gson.fromJson(cached, RemoteConfig::class.java)
                Log.i(TAG, "Configuración cargada desde caché local (servidor=${current.servers.primary})")
            } catch (e: Throwable) {
                Log.w(TAG, "Error deserializando caché: ${e.message}")
            }
        }
        startRealtimeListener(context.applicationContext)
    }

    suspend fun sync(context: Context): RemoteConfig = withContext(Dispatchers.IO) {
        // 1. Intentar Firebase Realtime Database
        try {
            val req = Request.Builder()
                .url(FIREBASE_URL)
                .header("Accept", "application/json")
                .build()
            Http.client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank() && body != "null") {
                        val parsed = gson.fromJson(body, RemoteConfig::class.java)
                        applyNewConfig(context, parsed, body, "Firebase REST")
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Fallo al sincronizar con Firebase: ${e.message}")
        }

        // 2. Fallbacks a GitHub
        for (url in GITHUB_FALLBACK_URLS) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .build()
                Http.client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string()
                        if (!body.isNullOrBlank()) {
                            val parsed = gson.fromJson(body, RemoteConfig::class.java)
                            applyNewConfig(context, parsed, body, "GitHub Fallback")
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Fallo fallback $url: ${e.message}")
            }
        }

        current
    }

    private fun applyNewConfig(context: Context, newConfig: RemoteConfig, rawJson: String, source: String) {
        current = newConfig
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHED_CONFIG, rawJson)
                .apply()
        } catch (_: Throwable) {}

        if (newConfig.servers.primary.isNotBlank() && Session.server != newConfig.servers.primary) {
            Session.server = newConfig.servers.primary
        }

        Log.i(TAG, "⚡ Nueva configuración aplicada desde $source (servidor: ${newConfig.servers.primary}, aviso: ${newConfig.announcement.enabled}, mantenimiento: ${newConfig.maintenance.enabled})")

        mainHandler.post {
            val listCopy = synchronized(listeners) { listeners.toList() }
            listCopy.forEach { l ->
                try { l(newConfig) } catch (t: Throwable) {
                    Log.w(TAG, "Error en listener de config: ${t.message}")
                }
            }
        }
    }

    /**
     * Conexión persistente en tiempo real (Server-Sent Events / SSE) con Firebase Realtime Database.
     * Cualquier cambio en el panel web se recibe en menos de 1 segundo sin polling.
     */
    private fun startRealtimeListener(context: Context) {
        if (listenerStarted) return
        listenerStarted = true

        scope.launch {
            while (isActive) {
                try {
                    val req = Request.Builder()
                        .url(FIREBASE_URL)
                        .header("Accept", "text/event-stream")
                        .build()

                    sseClient.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) {
                            Log.w(TAG, "SSE Firebase response code ${res.code}, reintentando en 4s...")
                            delay(4000)
                            return@use
                        }

                        val reader = BufferedReader(InputStreamReader(res.body?.byteStream(), Charsets.UTF_8))
                        var currentEvent: String? = null

                        while (isActive) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith("event:")) {
                                currentEvent = line.substring(6).trim()
                            } else if (line.startsWith("data:")) {
                                val dataStr = line.substring(5).trim()
                                if (currentEvent == "put" || currentEvent == "patch") {
                                    handleFirebaseEvent(context, dataStr)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "SSE Firebase desconectado (${e.message}), reconectando en 3s...")
                }
                delay(3000)
            }
        }
    }

    private fun handleFirebaseEvent(context: Context, dataStr: String) {
        try {
            val jsonTree = com.google.gson.JsonParser.parseString(dataStr).asJsonObject
            val path = jsonTree.get("path")?.asString.orEmpty()
            val dataElem = jsonTree.get("data")

            if (dataElem != null && !dataElem.isJsonNull) {
                if (path == "/" || path.isBlank()) {
                    val parsed = gson.fromJson(dataElem, RemoteConfig::class.java)
                    if (parsed != null && parsed.servers.primary.isNotBlank()) {
                        applyNewConfig(context, parsed, dataElem.toString(), "Firebase Live SSE")
                    }
                } else if (path.startsWith("/announcement")) {
                    val ann = gson.fromJson(dataElem, AnnouncementConfig::class.java)
                    val updated = current.copy(announcement = ann)
                    applyNewConfig(context, updated, gson.toJson(updated), "Firebase Live (aviso)")
                } else if (path.startsWith("/maintenance")) {
                    val mnt = gson.fromJson(dataElem, MaintenanceConfig::class.java)
                    val updated = current.copy(maintenance = mnt)
                    applyNewConfig(context, updated, gson.toJson(updated), "Firebase Live (mantenimiento)")
                } else if (path.startsWith("/servers")) {
                    val srv = gson.fromJson(dataElem, ServersConfig::class.java)
                    val updated = current.copy(servers = srv)
                    applyNewConfig(context, updated, gson.toJson(updated), "Firebase Live (servidores)")
                } else if (path.startsWith("/settings")) {
                    val st = gson.fromJson(dataElem, SettingsConfig::class.java)
                    val updated = current.copy(settings = st)
                    applyNewConfig(context, updated, gson.toJson(updated), "Firebase Live (ajustes)")
                } else {
                    scope.launch { sync(context) }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error procesando evento SSE: ${e.message}")
        }
    }
}
