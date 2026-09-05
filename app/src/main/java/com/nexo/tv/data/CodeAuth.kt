package com.nexo.tv.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    private const val FIRESTORE_BASE = "https://firestore.googleapis.com/v1/projects/jetgo-f0127/databases/(default)/documents/access_codes"
    private val gson = Gson()

    suspend fun validateAndLogin(rawCode: String, context: Context): CodeAuthResult = withContext(Dispatchers.IO) {
        val clean = rawCode.trim().filter { it.isDigit() }
        if (clean.length != 6) {
            return@withContext CodeAuthResult.Error("El código debe tener exactamente 6 dígitos.")
        }

        Log.i(TAG, "Iniciando validación de código: $clean")

        // 1. Intentar primero en Firebase Realtime Database
        var data: AccessCodeDto? = fetchFromRtdb(clean)

        // 2. Si no está en RTDB, consultar en Firestore (proyecto JetGo / Nexo)
        if (data == null) {
            Log.i(TAG, "Código no encontrado en RTDB, buscando en Firestore...")
            data = fetchFromFirestore(clean)
            if (data != null) {
                // Sincronizar en background a RTDB para acceso instantáneo futuro
                val toSync = data
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val json = gson.toJson(toSync)
                        val req = Request.Builder()
                            .url("$RTDB_BASE/codes/$clean.json")
                            .put(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        Http.client.newCall(req).execute().close()
                    } catch (_: Throwable) {}
                }
            }
        }

        if (data == null) {
            return@withContext CodeAuthResult.Error("Código no encontrado. Verifica los 6 dígitos.")
        }

        // 3. Verificar si está activo
        if (data.active == false) {
            return@withContext CodeAuthResult.Error("Este código ha sido revocado o suspendido.")
        }

        // 4. Verificar fecha de vencimiento
        val exp = data.expirationDate?.trim().orEmpty()
        if (exp.isNotBlank() && exp != "null") {
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                if (todayStr > exp) {
                    return@withContext CodeAuthResult.Error("Tu suscripción venció el $exp.")
                }
            } catch (_: Throwable) {}
        }

        // 5. Verificar demo
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

        // 6. Extraer credenciales Xtream (priorizando fuentes con usuario y contraseña)
        var host = data.host?.trim().orEmpty()
        var username = data.username?.trim().orEmpty()
        var password = data.password?.trim().orEmpty()

        if (username.isBlank() || password.isBlank()) {
            val sources = data.sources.orEmpty()
            // Primero buscar fuente explícitamente Xtream con credenciales
            val xtreamSrc = sources.firstOrNull {
                (it.type == null || it.type.equals("xtream", ignoreCase = true)) &&
                        !it.username.isNullOrBlank() && !it.password.isNullOrBlank()
            } ?: sources.firstOrNull {
                !it.username.isNullOrBlank() && !it.password.isNullOrBlank()
            }

            if (xtreamSrc != null) {
                if (username.isBlank()) username = xtreamSrc.username.orEmpty().trim()
                if (password.isBlank()) password = xtreamSrc.password.orEmpty().trim()
                if (host.isBlank()) host = xtreamSrc.host.orEmpty().trim()
            }
        }

        if (host.isBlank()) {
            host = AppConfig.current.servers.primary.ifBlank { Session.SERVER_ELITE }
        }

        if (username.isBlank() || password.isBlank()) {
            return@withContext CodeAuthResult.Error("El código no tiene credenciales de acceso asignadas.")
        }

        // 7. Iniciar sesión en el servidor Xtream
        Log.i(TAG, "Conectando al servidor Xtream ($host)")
        val ok = XtreamClient.login(username, password, preferredServer = host)
        if (!ok) {
            return@withContext CodeAuthResult.Error("No se pudo conectar al servidor. Intenta nuevamente.")
        }

        // 8. Guardar sesión completa en almacenamiento seguro
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

        // 9. Notificar actividad del dispositivo en background
        reportDeviceActivity(clean, context)

        CodeAuthResult.Success(data.clientName.orEmpty())
    }

    private fun fetchFromRtdb(code: String): AccessCodeDto? {
        return try {
            val req = Request.Builder()
                .url("$RTDB_BASE/codes/$code.json")
                .header("Accept", "application/json")
                .build()
            Http.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string().orEmpty()
                if (body.isBlank() || body == "null") return null
                gson.fromJson(body, AccessCodeDto::class.java)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error consultando RTDB: ${e.message}")
            null
        }
    }

    private fun fetchFromFirestore(code: String): AccessCodeDto? {
        return try {
            val req = Request.Builder()
                .url("$FIRESTORE_BASE/$code")
                .header("Accept", "application/json")
                .build()
            Http.client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val body = res.body?.string().orEmpty()
                if (body.isBlank() || body == "null") return null

                val root = JsonParser.parseString(body).asJsonObject
                val fields = root.getAsJsonObject("fields") ?: return null

                fun str(name: String): String? =
                    fields.getAsJsonObject(name)?.get("stringValue")?.asString?.takeIf { it.isNotBlank() }

                fun bool(name: String, default: Boolean = true): Boolean =
                    fields.getAsJsonObject(name)?.get("booleanValue")?.asBoolean ?: default

                val sources = mutableListOf<CodeSourceDto>()
                val sourcesArr = fields.getAsJsonObject("sources")
                    ?.getAsJsonObject("arrayValue")
                    ?.getAsJsonArray("values")

                sourcesArr?.forEach { elem ->
                    val sFields = elem.asJsonObject.getAsJsonObject("mapValue")?.getAsJsonObject("fields")
                    if (sFields != null) {
                        fun sStr(n: String): String? =
                            sFields.getAsJsonObject(n)?.get("stringValue")?.asString?.takeIf { it.isNotBlank() }

                        sources.add(
                            CodeSourceDto(
                                type = sStr("type"),
                                serverId = sStr("serverId"),
                                host = sStr("host"),
                                username = sStr("username"),
                                password = sStr("password"),
                                m3uUrl = sStr("m3uUrl")
                            )
                        )
                    }
                }

                AccessCodeDto(
                    code = code,
                    active = bool("active", true),
                    clientName = str("clientName"),
                    clientPhone = str("clientPhone"),
                    mode = str("mode"),
                    host = str("host"),
                    username = str("username"),
                    password = str("password"),
                    m3uUrl = str("m3uUrl"),
                    sources = sources,
                    expirationDate = str("expirationDate"),
                    isDemo = bool("isDemo", false),
                    demoExpiresAt = fields.getAsJsonObject("demoExpiresAt")?.get("timestampValue")?.asString,
                    allowTv = bool("allowTv", true),
                    allowMovies = bool("allowMovies", true),
                    allowSeries = bool("allowSeries", true)
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error consultando Firestore: ${e.message}")
            null
        }
    }

    private fun reportDeviceActivity(code: String, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "tv-box"
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val nowMs = System.currentTimeMillis()
                val jsonType = "application/json; charset=utf-8".toMediaType()

                // Actualizar timestamp de actividad en RTDB
                val actUrl = "$RTDB_BASE/codes/$code/deviceActivity/$deviceId.json"
                val actReq = Request.Builder()
                    .url(actUrl)
                    .put(nowMs.toString().toRequestBody(jsonType))
                    .build()
                Http.client.newCall(actReq).execute().close()

                // Actualizar nombre del dispositivo en RTDB
                val nameUrl = "$RTDB_BASE/codes/$code/deviceNames/$deviceId.json"
                val nameReq = Request.Builder()
                    .url(nameUrl)
                    .put("\"$deviceName\"".toRequestBody(jsonType))
                    .build()
                Http.client.newCall(nameReq).execute().close()

                Log.i(TAG, "Dispositivo $deviceName ($deviceId) reportado en tiempo real para código $code")
            } catch (e: Throwable) {
                Log.w(TAG, "No se pudo reportar actividad del dispositivo: ${e.message}")
            }
        }
    }
}
