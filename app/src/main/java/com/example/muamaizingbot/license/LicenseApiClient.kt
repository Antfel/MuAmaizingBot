package com.example.muamaizingbot.license

import android.util.Log
import com.example.muamaizingbot.R
import com.example.muamaizingbot.settings.UiStrings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

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
                    LicenseApiResult.Failed(
                        "INVALID_RESPONSE",
                        UiStrings.get(R.string.license_err_no_session_id),
                        code,
                    )
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
            "NO_SESSIONS" -> UiStrings.get(R.string.license_err_no_sessions)
            "INVALID_LICENSE" -> UiStrings.get(R.string.license_err_invalid)
            "REVOKED" -> UiStrings.get(R.string.license_err_revoked)
            "EXPIRED" -> UiStrings.get(R.string.license_err_expired)
            "SESSION_EXPIRED", "SESSION_NOT_FOUND" ->
                UiStrings.get(R.string.license_err_session_released)
            "MISSING_KEY" -> UiStrings.get(R.string.license_err_missing_key)
            "NETWORK" -> UiStrings.get(R.string.license_err_network)
            else -> UiStrings.get(R.string.license_err_generic, code)
        }
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        map: (JSONObject, Int) -> LicenseApiResult,
    ): LicenseApiResult {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            LicenseHttpClient.addTunnelHeaders(url, requestBuilder)

            LicenseHttpClient.licenseClient.newCall(requestBuilder.build()).execute().use { response ->
                val text = response.body.string()
                val json = if (text.isBlank()) {
                    JSONObject()
                } else {
                    runCatching { JSONObject(text) }.getOrElse {
                        Log.w(TAG, "POST $url non-JSON status=${response.code}")
                        JSONObject()
                            .put("error", "INVALID_RESPONSE")
                            .put("message", userMessageForCode("INVALID_RESPONSE"))
                    }
                }
                map(json, response.code)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "POST $url failed: ${t.message}")
            LicenseApiResult.Failed(
                "NETWORK",
                userMessageForCode("NETWORK"),
            )
        }
    }
}
