package com.example.muamaizingbot.content

import android.util.Log
import com.example.muamaizingbot.license.LicenseHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class MapContentApiResult<out T> {
    data class Ok<T>(val value: T) : MapContentApiResult<T>()
    data class Failed(val code: String, val message: String, val httpStatus: Int? = null) :
        MapContentApiResult<Nothing>()
}

object MapContentApiClient {

    private const val TAG = "MapContentApi"

    fun fetchManifest(
        baseUrl: String,
        licenseKey: String,
        sessionId: String?,
        schemaVersion: Int = MAP_CONTENT_SCHEMA_VERSION,
    ): MapContentApiResult<MapContentManifest> {
        val url = buildUrl(
            baseUrl,
            "/api/v1/content/maps/manifest",
            mapOf(
                "license_key" to licenseKey,
                "session_id" to sessionId,
                "schema_version" to schemaVersion.toString(),
            ),
        )
        return getJson(url) { json, code ->
            if (code in 200..299) {
                MapContentApiResult.Ok(MapContentManifest.fromJson(json))
            } else {
                failedFromBody(json, code)
            }
        }
    }

    fun downloadFile(
        baseUrl: String,
        licenseKey: String,
        sessionId: String?,
        relativePath: String,
        destFile: File,
    ): MapContentApiResult<Unit> {
        val encodedPath = relativePath.trimStart('/').split('/')
            .joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8.name()) }
        val url = buildUrl(
            baseUrl,
            "/api/v1/content/maps/files/$encodedPath",
            mapOf(
                "license_key" to licenseKey,
                "session_id" to sessionId,
            ),
        )
        return try {
            val requestBuilder = Request.Builder().url(url).get()
            LicenseHttpClient.addTunnelHeaders(url, requestBuilder)
            LicenseHttpClient.contentClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body.string()
                    val json = if (text.isBlank()) {
                        JSONObject()
                    } else {
                        runCatching { JSONObject(text) }.getOrElse { JSONObject() }
                    }
                    return failedFromBody(json, response.code)
                }
                destFile.parentFile?.mkdirs()
                response.body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            MapContentApiResult.Ok(Unit)
        } catch (t: Throwable) {
            Log.w(TAG, "download failed path=$relativePath: ${t.message}")
            MapContentApiResult.Failed("NETWORK", t.message ?: "network error")
        }
    }

    private fun failedFromBody(json: JSONObject, code: Int): MapContentApiResult.Failed {
        val err = json.optString("error").ifBlank { "HTTP_$code" }
        val msg = json.optString("message").ifBlank { err }
        return MapContentApiResult.Failed(err, msg, code)
    }

    private fun <T> getJson(
        url: String,
        map: (JSONObject, Int) -> MapContentApiResult<T>,
    ): MapContentApiResult<T> {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
            LicenseHttpClient.addTunnelHeaders(url, requestBuilder)
            LicenseHttpClient.contentClient.newCall(requestBuilder.build()).execute().use { response ->
                val text = response.body.string()
                val json = if (text.isBlank()) {
                    JSONObject()
                } else {
                    runCatching { JSONObject(text) }.getOrElse {
                        JSONObject()
                            .put("error", "INVALID_RESPONSE")
                            .put("message", "Server returned a non-JSON response")
                    }
                }
                map(json, response.code)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "GET $url failed: ${t.message}")
            MapContentApiResult.Failed("NETWORK", t.message ?: "network error")
        }
    }

    private fun buildUrl(baseUrl: String, path: String, query: Map<String, String?>): String {
        val base = baseUrl.trimEnd('/')
        val q = query.entries
            .filter { !it.value.isNullOrBlank() }
            .joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
        return if (q.isEmpty()) "$base$path" else "$base$path?$q"
    }
}
