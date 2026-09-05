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

    var accessCode: String
        get() = prefs.getString("access_code", "") ?: ""
        set(value) { prefs.edit().putString("access_code", value).apply() }

    var clientName: String
        get() = prefs.getString("client_name", "") ?: ""
        set(value) { prefs.edit().putString("client_name", value).apply() }

    var expirationDate: String
        get() = prefs.getString("expiration_date", "") ?: ""
        set(value) { prefs.edit().putString("expiration_date", value).apply() }

    var allowTv: Boolean
        get() = prefs.getBoolean("allow_tv", true)
        set(value) { prefs.edit().putBoolean("allow_tv", value).apply() }

    var allowMovies: Boolean
        get() = prefs.getBoolean("allow_movies", true)
        set(value) { prefs.edit().putBoolean("allow_movies", value).apply() }

    var allowSeries: Boolean
        get() = prefs.getBoolean("allow_series", true)
        set(value) { prefs.edit().putBoolean("allow_series", value).apply() }

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

    fun loginWithCode(
        code: String,
        user: String,
        pass: String,
        targetServer: String,
        client: String = "",
        expiration: String = "",
        tv: Boolean = true,
        movies: Boolean = true,
        series: Boolean = true
    ) {
        accessCode = code
        username = user
        password = pass
        if (targetServer.isNotBlank()) {
            server = targetServer
        }
        clientName = client
        expirationDate = expiration
        allowTv = tv
        allowMovies = movies
        allowSeries = series
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
