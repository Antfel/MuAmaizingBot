package com.example.muamaizingbot.license

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

sealed class LicenseApiResult {
    data class Acquired(
        val sessionId: String,
        val maxSessions: Int,
        val activeSessions: Int,
    ) : LicenseApiResult()

    data class Ok(val ok: Boolean = true) : LicenseApiResult()

    data class Failed(
        val code: String,
        val message: String,
        val httpStatus: Int? = null,
    ) : LicenseApiResult()
}

object LicenseApiClient {

    private const val TAG = "LicenseApi"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000

    fun acquire(
        baseUrl: String,
        licenseKey: String,
        deviceId: String,
        appVersion: String?,
    ): LicenseApiResult {
        val body = JSONObject()
            .put("license_key", licenseKey)
            .put("device_id", deviceId)
        if (!appVersion.isNullOrBlank()) {
            body.put("app_version", appVersion)
        }
        return postJson("$baseUrl/api/v1/sessions/acquire", body) { json, code ->
            if (code in 200..299) {
                val sessionId = json.optString("session_id")
                if (sessionId.isBlank()) {
                    LicenseApiResult.Failed("INVALID_RESPONSE", "Respuesta sin session_id", code)
                } else {
                    LicenseApiResult.Acquired(
                        sessionId = sessionId,
                        maxSessions = json.optInt("max_sessions", 0),
                        activeSessions = json.optInt("active_sessions", 0),
                    )
                }
            } else {
                failedFromBody(json, code)
            }
        }
    }

    fun heartbeat(
        baseUrl: String,
        sessionId: String,
        licenseKey: String,
    ): LicenseApiResult {
        val body = JSONObject()
            .put("session_id", sessionId)
            .put("license_key", licenseKey)
        return postJson("$baseUrl/api/v1/sessions/heartbeat", body) { json, code ->
            if (code in 200..299) {
                LicenseApiResult.Ok()
            } else {
                failedFromBody(json, code)
            }
        }
    }

    fun release(
        baseUrl: String,
        sessionId: String,
        licenseKey: String,
    ): LicenseApiResult {
        val body = JSONObject()
            .put("session_id", sessionId)
            .put("license_key", licenseKey)
        return postJson("$baseUrl/api/v1/sessions/release", body) { json, code ->
            if (code in 200..299) {
                LicenseApiResult.Ok()
            } else {
                failedFromBody(json, code)
            }
        }
    }

    private fun failedFromBody(json: JSONObject, code: Int): LicenseApiResult.Failed {
        val err = json.optString("error").ifBlank { "HTTP_$code" }
        val msg = json.optString("message").ifBlank {
            userMessageForCode(err)
        }
        return LicenseApiResult.Failed(err, msg, code)
    }

    fun userMessageForCode(code: String): String {
        return when (code) {
            "NO_SESSIONS" -> "No cuenta con sesiones disponibles para ejecución"
            "INVALID_LICENSE" -> "Licencia inválida"
            "REVOKED" -> "Licencia revocada"
            "EXPIRED" -> "Licencia expirada"
            "SESSION_EXPIRED", "SESSION_NOT_FOUND" -> "Sesión liberada desde el panel — bot detenido"
            "MISSING_KEY" -> "Configura tu licencia en el menú Licencia"
            "NETWORK" -> "No se pudo conectar al servidor de licencias"
            else -> "Error de licencia ($code)"
        }
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        map: (JSONObject, Int) -> LicenseApiResult,
    ): LicenseApiResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
            }.orEmpty()

            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            map(json, code)
        } catch (t: Throwable) {
            Log.w(TAG, "POST $url failed: ${t.message}")
            LicenseApiResult.Failed(
                "NETWORK",
                userMessageForCode("NETWORK"),
            )
        } finally {
            conn?.disconnect()
        }
    }
}
