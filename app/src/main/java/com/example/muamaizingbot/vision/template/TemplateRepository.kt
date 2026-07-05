package com.example.muamaizingbot.vision.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object TemplateRepository {

    private const val TAG = "TemplateRepository"

    private val templatesByCanonicalPath = linkedMapOf<String, TemplateInfo>()
    private var currentResolutionKey = TemplateAssets.REF_RESOLUTION_KEY
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        reloadForActiveResolution()
    }

    fun reloadForActiveResolution() {
        if (!::appContext.isInitialized) {
            return
        }
        reload(TemplateAssets.templateResolutionKey())
    }

    fun currentResolutionKey(): String = currentResolutionKey

    fun reload(resolutionKey: String) {
        if (!TemplateAssets.SUPPORTED_RESOLUTION_KEYS.contains(resolutionKey)) {
            Log.w(TAG, "[TEMPLATE] unsupported resolution=$resolutionKey fallback=${TemplateAssets.REF_RESOLUTION_KEY}")
            reload(TemplateAssets.REF_RESOLUTION_KEY)
            return
        }

        templatesByCanonicalPath.values.forEach { info ->
            if (!info.bitmap.isRecycled) {
                info.bitmap.recycle()
            }
        }
        templatesByCanonicalPath.clear()
        currentResolutionKey = resolutionKey

        val root = "templates/$resolutionKey/mu"
        walkAssets(appContext, root)
        Log.d(
            TAG,
            "[TEMPLATE] loaded resolution=$resolutionKey count=${templatesByCanonicalPath.size}"
        )
    }

    fun getByPath(canonicalPath: String): TemplateInfo? {
        val normalized = TemplateAssets.normalizeToCanonical(canonicalPath)
        return templatesByCanonicalPath[normalized]
    }

    fun getByName(sourceName: String): TemplateInfo? {
        return templatesByCanonicalPath.values.firstOrNull { it.sourceName == sourceName }
    }

    fun allTemplates(): List<TemplateInfo> = templatesByCanonicalPath.values.toList()

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
        val prefix = "templates/$currentResolutionKey/mu/"
        if (!physicalPath.startsWith(prefix)) {
            return
        }
        val relative = physicalPath.removePrefix(prefix)
        val canonicalPath = "${TemplateAssets.CANONICAL_PREFIX}/$relative"
        if (templatesByCanonicalPath.containsKey(canonicalPath)) {
            return
        }

        val bitmap = context.assets.open(physicalPath).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: run {
            Log.w(TAG, "[TEMPLATE] failed physical=$physicalPath")
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
