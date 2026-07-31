package com.example.muamaizingbot.content

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Resolves pack-relative or asset paths preferring the downloaded overlay, then APK assets.
 *
 * Accepts:
 * - `navigation/maps/...`
 * - `templates/mu/...`
 * - legacy `templates/maps/...` (normalized by callers when needed)
 */
object ContentAssetResolver {

    @Volatile
    private var overlayRoot: File? = null

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context, packStore: ContentPackStore) {
        appContext = context.applicationContext
        overlayRoot = packStore.currentRoot()
    }

    fun refreshOverlay(packStore: ContentPackStore) {
        overlayRoot = packStore.currentRoot()
    }

    /** Overlay-only bind for JVM unit tests (no asset fallback). */
    fun bindOverlayForTests(overlay: File?) {
        overlayRoot = overlay
        appContext = null
    }

    fun open(relativeOrAssetPath: String): InputStream? {
        val path = relativeOrAssetPath.replace('\\', '/').trim().trimStart('/')
        if (path.isBlank() || path.contains("..")) return null

        val overlay = overlayRoot
        if (overlay != null) {
            val disk = File(overlay, path)
            if (disk.isFile) {
                return FileInputStream(disk)
            }
        }

        val ctx = appContext ?: return null
        return runCatching { ctx.assets.open(path) }.getOrNull()
    }

    fun exists(relativeOrAssetPath: String): Boolean {
        open(relativeOrAssetPath)?.use { return true }
        return false
    }
}
