package com.example.muamaizingbot.content

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Local overlay pack under filesDir/content/maps.
 * Layout: current/ (active), staging/ (in-progress sync), manifest.json beside current.
 */
class ContentPackStore(private val rootDir: File) {

    private val currentDir = File(rootDir, CURRENT_DIR)
    private val stagingDir = File(rootDir, STAGING_DIR)
    private val manifestFile = File(rootDir, MANIFEST_FILE)
    private val stagingManifestFile = File(rootDir, STAGING_MANIFEST_FILE)

    fun currentRoot(): File? = currentDir.takeIf { it.isDirectory }

    fun stagingRoot(): File {
        stagingDir.mkdirs()
        return stagingDir
    }

    fun readLocalManifest(): MapContentManifest? {
        if (!manifestFile.isFile) return null
        return runCatching {
            MapContentManifest.fromJson(JSONObject(manifestFile.readText()))
        }.getOrNull()
    }

    fun localPackVersion(): Int = readLocalManifest()?.packVersion ?: 0

    fun prepareStagingFromCurrent() {
        deleteRecursively(stagingDir)
        stagingDir.mkdirs()
        if (currentDir.isDirectory) {
            currentDir.copyRecursively(stagingDir, overwrite = true)
        }
        val local = readLocalManifest()
        if (local != null) {
            stagingManifestFile.writeText(local.toJson().toString(2))
        }
    }

    fun stagingFileFor(relativePath: String): File {
        val safe = sanitizeRelativePath(relativePath)
        val out = File(stagingDir, safe)
        out.parentFile?.mkdirs()
        return out
    }

    fun writeStagingManifest(manifest: MapContentManifest) {
        rootDir.mkdirs()
        stagingManifestFile.writeText(manifest.toJson().toString(2))
    }

    /**
     * Verifies every staging file matches the manifest sha256, then swaps into current.
     * On failure, leaves [currentDir] untouched.
     */
    fun commitStaging(manifest: MapContentManifest): Boolean {
        if (!verifyStaging(manifest)) {
            Log.w(TAG, "[CONTENT] staging verify failed; keep current pack")
            return false
        }
        val swap = File(rootDir, SWAP_DIR)
        deleteRecursively(swap)
        if (currentDir.exists() && !currentDir.renameTo(swap)) {
            Log.w(TAG, "[CONTENT] could not move current → swap")
            return false
        }
        if (!stagingDir.renameTo(currentDir)) {
            Log.w(TAG, "[CONTENT] could not move staging → current; restoring")
            if (swap.exists()) {
                swap.renameTo(currentDir)
            }
            return false
        }
        deleteRecursively(swap)
        deleteRecursively(stagingDir)
        stagingManifestFile.delete()
        rootDir.mkdirs()
        manifestFile.writeText(manifest.toJson().toString(2))
        Log.i(TAG, "[CONTENT] committed pack_version=${manifest.packVersion} files=${manifest.files.size}")
        return true
    }

    fun discardStaging() {
        deleteRecursively(stagingDir)
        stagingManifestFile.delete()
    }

    fun verifyStaging(manifest: MapContentManifest): Boolean {
        for (entry in manifest.files) {
            val file = File(stagingDir, sanitizeRelativePath(entry.path))
            if (!file.isFile) {
                Log.w(TAG, "[CONTENT] missing staging file path=${entry.path}")
                return false
            }
            val digest = sha256Hex(file)
            if (!digest.equals(entry.sha256, ignoreCase = true)) {
                Log.w(TAG, "[CONTENT] sha mismatch path=${entry.path}")
                return false
            }
        }
        return true
    }

    fun sha256OfCurrentOrStaging(relativePath: String, preferStaging: Boolean): String? {
        val base = if (preferStaging && stagingDir.isDirectory) stagingDir else currentDir
        val file = File(base, sanitizeRelativePath(relativePath))
        if (!file.isFile) return null
        return sha256Hex(file)
    }

    companion object {
        private const val TAG = "ContentPackStore"
        const val CURRENT_DIR = "current"
        const val STAGING_DIR = "staging"
        private const val SWAP_DIR = "swap"
        const val MANIFEST_FILE = "manifest.json"
        private const val STAGING_MANIFEST_FILE = "staging_manifest.json"

        fun fromContext(context: Context): ContentPackStore {
            val root = File(context.applicationContext.filesDir, "content/maps")
            root.mkdirs()
            return ContentPackStore(root)
        }

        fun sanitizeRelativePath(path: String): String {
            val normalized = path.replace('\\', '/').trim().trimStart('/')
            require(normalized.isNotBlank()) { "empty path" }
            require(!normalized.contains("..")) { "path traversal: $path" }
            require(!normalized.startsWith("/")) { "absolute path: $path" }
            return normalized
        }

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun deleteRecursively(dir: File) {
            if (!dir.exists()) return
            dir.deleteRecursively()
        }
    }
}
