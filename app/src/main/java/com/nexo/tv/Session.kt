package com.nexo.tv

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Session {
    const val SERVER_NEXO = "https://nexo.fusionx.cl"
    const val SERVER_ELITE = "http://eliteplusec.com:8080"
    /** Servidor activo por defecto: ElitePlus (HTTP, estable en TV Box) */
    const val SERVER = SERVER_ELITE

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        val ctx = context.applicationContext
        prefs = try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                "nexo_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e("Session", "encrypted prefs failed, using plain", e)
            ctx.getSharedPreferences("nexo_session_plain", Context.MODE_PRIVATE)
        }
    }

    var username: String
        get() = prefs.getString("user", "") ?: ""
        set(value) { prefs.edit().putString("user", value).apply() }

    var password: String
        get() = prefs.getString("pass", "") ?: ""
        set(value) { prefs.edit().putString("pass", value).apply() }

    var server: String
        get() {
            val remotePrimary = com.nexo.tv.data.AppConfig.current.servers.primary.trim().trimEnd('/')
            val defaultFallback = if (remotePrimary.isNotBlank()) remotePrimary else SERVER
            val stored = prefs.getString("server", defaultFallback) ?: defaultFallback
            if (stored.contains("10.250.") || stored.contains("192.168.") || stored.contains("127.0.0.1")) {
                return defaultFallback
            }
            return stored.ifBlank { defaultFallback }
        }
        set(value) {
            val clean = if (value.contains("10.250.") || value.contains("192.168.")) SERVER else value
            prefs.edit().putString("server", clean.trimEnd('/')).apply()
        }

    val isLoggedIn: Boolean get() = username.isNotBlank() && password.isNotBlank()

    fun login(user: String, pass: String) {
        username = user
        password = pass
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
