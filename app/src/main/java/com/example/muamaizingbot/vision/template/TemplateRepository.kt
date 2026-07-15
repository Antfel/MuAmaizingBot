package com.example.muamaizingbot.vision.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * Loads the single fixed template pack from assets ([TemplateAssets.ASSET_ROOT]).
 * Target capture: 1280×720 @ 240 DPI.
 */
object TemplateRepository {

    private const val TAG = "TemplateRepository"

    private val templatesByCanonicalPath = linkedMapOf<String, TemplateInfo>()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        reload()
    }

    fun reload() {
        if (!::appContext.isInitialized) {
            return
        }
        clearLoadedTemplates()
        walkAssets(appContext, TemplateAssets.ASSET_ROOT)
        Log.d(TAG, "[TEMPLATE] loaded count=${templatesByCanonicalPath.size}")
    }

    fun getByPath(canonicalPath: String): TemplateInfo? {
        val normalized = TemplateAssets.normalizeToCanonical(canonicalPath)
        return templatesByCanonicalPath[normalized]
    }

    fun getByName(sourceName: String): TemplateInfo? {
        return templatesByCanonicalPath.values.firstOrNull { it.sourceName == sourceName }
    }

    fun allTemplates(): List<TemplateInfo> = templatesByCanonicalPath.values.toList()

    private fun clearLoadedTemplates() {
        templatesByCanonicalPath.values.forEach { info ->
            if (!info.bitmap.isRecycled) {
                info.bitmap.recycle()
            }
        }
        templatesByCanonicalPath.clear()
    }

    private fun walkAssets(context: Context, physicalRoot: String) {
        val assetManager = context.assets
        val entries = assetManager.list(physicalRoot) ?: return
        for (entry in entries) {
            val fullPath = "$physicalRoot/$entry"
            if (entry.endsWith(".png", ignoreCase = true)) {
                loadAsset(context, fullPath)
            } else {
                walkAssets(context, fullPath)
            }
        }
    }

    private fun loadAsset(context: Context, physicalPath: String) {
        if (!physicalPath.startsWith("${TemplateAssets.ASSET_ROOT}/") ||
            !physicalPath.endsWith(".png", ignoreCase = true)
        ) {
            return
        }
        val relative = physicalPath.removePrefix("${TemplateAssets.ASSET_ROOT}/")
        val canonicalPath = "${TemplateAssets.CANONICAL_PREFIX}/$relative"
        if (templatesByCanonicalPath.containsKey(canonicalPath)) {
            return
        }
        val bitmap = context.assets.open(physicalPath).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: run {
            Log.w(TAG, "[TEMPLATE] failed relative=$relative")
            return
        }
        val category = relative.substringBefore('/', missingDelimiterValue = "root")
        templatesByCanonicalPath[canonicalPath] = TemplateInfo(
            assetPath = canonicalPath,
            sourceName = relative.substringAfterLast('/').removeSuffix(".png"),
            category = category,
            bitmap = bitmap,
        )
    }
}
