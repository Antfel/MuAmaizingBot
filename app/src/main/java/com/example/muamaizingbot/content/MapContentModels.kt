package com.example.muamaizingbot.content

import org.json.JSONArray
import org.json.JSONObject

/** Client capability; ignore remote packs with a higher schema_version. */
const val MAP_CONTENT_SCHEMA_VERSION = 1

data class MapContentFileEntry(
    val path: String,
    val sha256: String,
    val size: Long,
)

data class MapContentManifest(
    val packVersion: Int,
    val schemaVersion: Int,
    val minAppVersion: String? = null,
    val files: List<MapContentFileEntry> = emptyList(),
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        for (file in files) {
            arr.put(
                JSONObject()
                    .put("path", file.path)
                    .put("sha256", file.sha256)
                    .put("size", file.size),
            )
        }
        return JSONObject()
            .put("pack_version", packVersion)
            .put("schema_version", schemaVersion)
            .put("min_app_version", minAppVersion)
            .put("files", arr)
    }

    companion object {
        fun fromJson(json: JSONObject): MapContentManifest {
            val filesJson = json.optJSONArray("files") ?: JSONArray()
            val files = buildList {
                for (i in 0 until filesJson.length()) {
                    val item = filesJson.optJSONObject(i) ?: continue
                    val path = item.optString("path").trim()
                    val sha = item.optString("sha256").trim().lowercase()
                    if (path.isBlank() || sha.isBlank()) continue
                    add(
                        MapContentFileEntry(
                            path = path.trimStart('/'),
                            sha256 = sha,
                            size = item.optLong("size", 0L),
                        ),
                    )
                }
            }
            return MapContentManifest(
                packVersion = json.optInt("pack_version", 0),
                schemaVersion = json.optInt("schema_version", 0),
                minAppVersion = json.optString("min_app_version").takeIf { it.isNotBlank() },
                files = files,
            )
        }
    }
}

sealed class MapContentSyncResult {
    data class UpToDate(val packVersion: Int) : MapContentSyncResult()
    data class Updated(val packVersion: Int, val downloadedFiles: Int) : MapContentSyncResult()
    data class Skipped(val reason: String) : MapContentSyncResult()
    data class Failed(val code: String, val message: String) : MapContentSyncResult()
}
