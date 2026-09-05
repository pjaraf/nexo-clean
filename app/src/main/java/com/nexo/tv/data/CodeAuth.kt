package com.nexo.tv.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nexo.tv.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CodeAuthResult {
    data class Success(val clientName: String) : CodeAuthResult()
    data class Error(val message: String) : CodeAuthResult()
}

data class CodeSourceDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("serverId") val serverId: String? = null,
    @SerializedName("host") val host: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("m3uUrl") val m3uUrl: String? = null
)

data class AccessCodeDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("active") val active: Boolean? = true,
    @SerializedName("clientName") val clientName: String? = null,
    @SerializedName("clientPhone") val clientPhone: String? = null,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("host") val host: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("m3uUrl") val m3uUrl: String? = null,
    @SerializedName("sources") val sources: List<CodeSourceDto>? = null,
    @SerializedName("expirationDate") val expirationDate: String? = null,
    @SerializedName("isDemo") val isDemo: Boolean? = false,
    @SerializedName("demoExpiresAt") val demoExpiresAt: Any? = null,
    @SerializedName("allowTv") val allowTv: Boolean? = true,
    @SerializedName("allowMovies") val allowMovies: Boolean? = true,
    @SerializedName("allowSeries") val allowSeries: Boolean? = true
)

object CodeAuth {
    private const val TAG = "CodeAuth"
    private const val RTDB_BASE = "https://nexo-tv-d8766-default-rtdb.firebaseio.com"
    private val gson = Gson()

    suspend fun validateAndLogin(rawCode: String, context: Context): CodeAuthResult = withContext(Dispatchers.IO) {
        val clean = rawCode.trim().filter { it.isDigit() }
        if (clean.length != 6) {
            return@withContext CodeAuthResult.Error("El código debe tener exactamente 6 dígitos.")
        }

        val url = "$RTDB_BASE/codes/$clean.json"
        Log.i(TAG, "Consultando código $clean en $url")

        val docJson: String = try {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            Http.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    return@withContext CodeAuthResult.Error("Error al contactar el servidor (${res.code}).")
                }
                res.body?.string().orEmpty()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Fallo de red al verificar código: ${e.message}")
            return@withContext CodeAuthResult.Error("Sin conexión a internet. Revisa tu red.")
        }

        if (docJson.isBlank() || docJson == "null") {
            return@withContext CodeAuthResult.Error("Código no encontrado. Verifica los 6 dígitos.")
        }

        val data: AccessCodeDto = try {
            gson.fromJson(docJson, AccessCodeDto::class.java)
        } catch (e: Throwable) {
            Log.e(TAG, "Error deserializando código: ${e.message}")
            return@withContext CodeAuthResult.Error("Error al procesar los datos del código.")
        }

        // 1. Verificar si está activo
        if (data.active == false) {
            return@withContext CodeAuthResult.Error("Este código ha sido revocado o suspendido.")
        }

        // 2. Verificar fecha de vencimiento
        val exp = data.expirationDate?.trim().orEmpty()
        if (exp.isNotBlank() && exp != "null") {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                if (todayStr > exp) {
                    return@withContext CodeAuthResult.Error("Tu suscripción venció el $exp.")
                }
            } catch (_: Throwable) {}
        }

        // 3. Verificar demo
        if (data.isDemo == true && data.demoExpiresAt != null) {
            val expireMs: Long? = when (val d = data.demoExpiresAt) {
                is Number -> d.toLong()
                is Map<*, *> -> {
                    val sec = (d["_seconds"] as? Number)?.toLong() ?: (d["seconds"] as? Number)?.toLong()
                    sec?.times(1000L)
                }
                is String -> {
                    try {
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(d)?.time
                    } catch (_: Throwable) { null }
                }
                else -> null
            }
            if (expireMs != null && System.currentTimeMillis() > expireMs) {
                return@withContext CodeAuthResult.Error("Esta cuenta demo ha expirado.")
            }
        }

        // 4. Extraer credenciales Xtream
        var host = data.host?.trim().orEmpty()
        var username = data.username?.trim().orEmpty()
        var password = data.password?.trim().orEmpty()

        if ((username.isBlank() || password.isBlank()) && !data.sources.isNullOrEmpty()) {
            val first = data.sources.first()
            if (host.isBlank()) host = first.host?.trim().orEmpty()
            if (username.isBlank()) username = first.username?.trim().orEmpty()
            if (password.isBlank()) password = first.password?.trim().orEmpty()
        }

        if (host.isBlank()) {
            host = AppConfig.current.servers.primary.ifBlank { Session.SERVER_ELITE }
        }

        if (username.isBlank() || password.isBlank()) {
            return@withContext CodeAuthResult.Error("El código no tiene credenciales de acceso asignadas.")
        }

        // 5. Iniciar sesión en el servidor Xtream
        Log.i(TAG, "Conectando Xtream con servidor=$host, usuario=$username")
        val ok = XtreamClient.login(username, password, preferredServer = host)
        if (!ok) {
            return@withContext CodeAuthResult.Error("No se pudo iniciar sesión en el servidor. Intenta de nuevo.")
        }

        // 6. Guardar sesión
        Session.loginWithCode(
            code = clean,
            user = username,
            pass = password,
            targetServer = host,
            client = data.clientName.orEmpty(),
            expiration = data.expirationDate.orEmpty(),
            tv = data.allowTv ?: true,
            movies = data.allowMovies ?: true,
            series = data.allowSeries ?: true
        )

        // 7. Notificar dispositivo en línea a Firebase en background
        reportDeviceActivity(clean, context)

        CodeAuthResult.Success(data.clientName.orEmpty())
    }

    private fun reportDeviceActivity(code: String, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "tv-box"
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val nowMs = System.currentTimeMillis()
                val jsonType = "application/json; charset=utf-8".toMediaType()

                // Actualizar timestamp de actividad
                val actUrl = "$RTDB_BASE/codes/$code/deviceActivity/$deviceId.json"
                val actReq = Request.Builder()
                    .url(actUrl)
                    .put(nowMs.toString().toRequestBody(jsonType))
                    .build()
                Http.client.newCall(actReq).execute().close()

                // Actualizar nombre del dispositivo
                val nameUrl = "$RTDB_BASE/codes/$code/deviceNames/$deviceId.json"
                val nameReq = Request.Builder()
                    .url(nameUrl)
                    .put("\"$deviceName\"".toRequestBody(jsonType))
                    .build()
                Http.client.newCall(nameReq).execute().close()

                Log.i(TAG, "Dispositivo $deviceName ($deviceId) registrado para código $code")
            } catch (e: Throwable) {
                Log.w(TAG, "No se pudo reportar actividad del dispositivo: ${e.message}")
            }
        }
    }
}
