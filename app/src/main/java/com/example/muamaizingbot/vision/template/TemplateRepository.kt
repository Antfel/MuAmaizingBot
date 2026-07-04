package com.example.muamaizingbot.vision.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

object TemplateRepository {

    private const val TAG = "TemplateRepository"
    private const val ROOT = "templates/mu"

    private val templatesByPath = linkedMapOf<String, TemplateInfo>()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) {
            return
        }
        val appContext = context.applicationContext
        walkAssets(appContext, ROOT)
        initialized = true
        Log.d(TAG, "[TEMPLATE] loaded count=${templatesByPath.size}")
    }

    fun getByPath(assetPath: String): TemplateInfo? = templatesByPath[assetPath]

    fun getByName(sourceName: String): TemplateInfo? {
        return templatesByPath.values.firstOrNull { it.sourceName == sourceName }
    }

    fun allTemplates(): List<TemplateInfo> = templatesByPath.values.toList()

    private fun walkAssets(context: Context, path: String) {
        val assetManager = context.assets
        val entries = assetManager.list(path) ?: return
        for (entry in entries) {
            val fullPath = "$path/$entry"
            if (entry.endsWith(".png", ignoreCase = true)) {
                loadAsset(context, fullPath)
            } else {
                walkAssets(context, fullPath)
            }
        }
    }

    private fun loadAsset(context: Context, assetPath: String) {
        if (templatesByPath.containsKey(assetPath)) {
            return
        }
        val bitmap = context.assets.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: run {
            Log.w(TAG, "[TEMPLATE] failed path=$assetPath")
            return
        }

        val relativePath = assetPath.removePrefix("$ROOT/")
        val category = relativePath.substringBefore('/', missingDelimiterValue = "root")

        templatesByPath[assetPath] = TemplateInfo(
            assetPath = assetPath,
            sourceName = assetPath.substringAfterLast('/').removeSuffix(".png"),
            category = category,
            bitmap = bitmap
        )
    }
}
