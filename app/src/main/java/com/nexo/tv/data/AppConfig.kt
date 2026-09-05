package com.nexo.tv.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

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

    private val gson = Gson()

    private val CONFIG_URLS = listOf(
        "https://raw.githubusercontent.com/pjaraf/nexo-clean/main/nexo-config.json",
        "https://pjaraf.github.io/nexo-clean/nexo-config.json"
    )

    @Volatile
    var current: RemoteConfig = RemoteConfig()
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHED_CONFIG, null)
        if (!cached.isNullOrBlank()) {
            try {
                current = gson.fromJson(cached, RemoteConfig::class.java)
                Log.i(TAG, "Cargada configuración remota en caché (servidor primario=${current.servers.primary})")
            } catch (e: Throwable) {
                Log.w(TAG, "Error al deserializar config cacheada: ${e.message}")
            }
        }
    }

    suspend fun sync(context: Context): RemoteConfig = withContext(Dispatchers.IO) {
        for (url in CONFIG_URLS) {
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
                            current = parsed
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putString(KEY_CACHED_CONFIG, body)
                                .apply()
                            Log.i(TAG, "Configuración sincronizada exitosamente desde $url")
                            return@withContext parsed
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error sincronizando desde $url: ${e.message}")
            }
        }
        current
    }
}
