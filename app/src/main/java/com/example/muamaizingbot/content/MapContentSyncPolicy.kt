package com.example.muamaizingbot.content

/**
 * Pure decisions for map-pack sync (unit-testable without Android/network).
 */
object MapContentSyncPolicy {

    fun shouldSkipUnsupportedSchema(remoteSchema: Int, clientSchema: Int): Boolean {
        return remoteSchema > clientSchema
    }

    fun isSamePack(local: MapContentManifest?, remote: MapContentManifest): Boolean {
        if (local == null) return false
        return local.packVersion == remote.packVersion && local.files == remote.files
    }

    fun filesNeedingDownload(
        remote: MapContentManifest,
        localShaByPath: Map<String, String>,
    ): List<MapContentFileEntry> {
        return remote.files.filter { entry ->
            val existing = localShaByPath[entry.path]
            existing == null || !existing.equals(entry.sha256, ignoreCase = true)
        }
    }
}
