package com.example.muamaizingbot.vision.template

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.muamaizingbot.calibration.CalibratedTemplateStore
import com.example.muamaizingbot.calibration.CalibrationRepository
import java.io.File

object TemplateRepository {

    private const val TAG = "TemplateRepository"

    private val templatesByCanonicalPath = linkedMapOf<String, TemplateInfo>()
    private var currentResolutionKey = TemplateAssets.REF_RESOLUTION_KEY
    private var usingCalibratedTemplates = false
    private var loadedCaptureWidth = 0
    private var loadedCaptureHeight = 0
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        reloadForActiveResolution()
    }

    fun reloadForActiveResolution() {
        if (!::appContext.isInitialized) {
            return
        }
        val capture = com.example.muamaizingbot.settings.ResolutionSettingsRepository.detectedCaptureSize()
        if (capture != null) {
            reloadForCapture(capture.first, capture.second)
        } else {
            reload(TemplateAssets.templateResolutionKey())
        }
    }

    fun reloadForCapture(captureWidth: Int, captureHeight: Int) {
        if (!::appContext.isInitialized) {
            return
        }
        if (captureWidth > 0 && captureHeight > 0 &&
            CalibratedTemplateStore.exists(appContext, captureWidth, captureHeight) &&
            CalibrationRepository.hasCalibrationFor(captureWidth, captureHeight)
        ) {
            reloadFromCalibratedStorage(captureWidth, captureHeight)
            return
        }
        usingCalibratedTemplates = false
        loadedCaptureWidth = 0
        loadedCaptureHeight = 0
        reload(TemplateAssets.snapToSupported(captureWidth, captureHeight))
    }

    fun currentResolutionKey(): String = currentResolutionKey

    fun isUsingCalibratedTemplates(): Boolean = usingCalibratedTemplates

    fun loadedCaptureSize(): Pair<Int, Int>? {
        if (!usingCalibratedTemplates || loadedCaptureWidth <= 0 || loadedCaptureHeight <= 0) {
            return null
        }
        return loadedCaptureWidth to loadedCaptureHeight
    }

    fun reload(resolutionKey: String) {
        if (!TemplateAssets.SUPPORTED_RESOLUTION_KEYS.contains(resolutionKey)) {
            Log.w(TAG, "[TEMPLATE] unsupported resolution=$resolutionKey fallback=${TemplateAssets.REF_RESOLUTION_KEY}")
            reload(TemplateAssets.REF_RESOLUTION_KEY)
            return
        }

        clearLoadedTemplates()
        currentResolutionKey = resolutionKey
        usingCalibratedTemplates = false
        loadedCaptureWidth = 0
        loadedCaptureHeight = 0

        val root = "templates/$resolutionKey/mu"
        walkAssets(appContext, root)
        Log.d(
            TAG,
            "[TEMPLATE] loaded preset=$resolutionKey count=${templatesByCanonicalPath.size}",
        )
    }

    private fun reloadFromCalibratedStorage(captureWidth: Int, captureHeight: Int) {
        clearLoadedTemplates()
        val captureKey = TemplateAssets.resolutionKey(captureWidth, captureHeight)
        currentResolutionKey = captureKey
        usingCalibratedTemplates = true
        loadedCaptureWidth = captureWidth
        loadedCaptureHeight = captureHeight

        val muRoot = CalibratedTemplateStore.templatesRoot(appContext, captureWidth, captureHeight)
        walkFiles(muRoot, muRoot)
        Log.d(
            TAG,
            "[TEMPLATE] loaded calibrated capture=$captureKey count=${templatesByCanonicalPath.size}",
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

    private fun walkFiles(rootDir: File, currentDir: File) {
        if (!currentDir.exists()) {
            return
        }
        currentDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                walkFiles(rootDir, file)
            } else if (file.isFile && file.extension.equals("png", ignoreCase = true)) {
                loadFile(rootDir, file)
            }
        }
    }

    private fun loadAsset(context: Context, physicalPath: String) {
        val prefix = "templates/$currentResolutionKey/mu/"
        if (!physicalPath.startsWith("templates/") || !physicalPath.endsWith(".png", ignoreCase = true)) {
            return
        }
        val relative = when {
            physicalPath.startsWith(prefix) -> physicalPath.removePrefix(prefix)
            physicalPath.startsWith("templates/${TemplateAssets.REF_RESOLUTION_KEY}/mu/") ->
                physicalPath.removePrefix("templates/${TemplateAssets.REF_RESOLUTION_KEY}/mu/")
            else -> return
        }
        registerTemplate(relative) {
            context.assets.open(physicalPath).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }

    private fun loadFile(rootDir: File, file: File) {
        val relative = file.relativeTo(rootDir).path.replace('\\', '/')
        registerTemplate(relative) {
            BitmapFactory.decodeFile(file.absolutePath)
        }
    }

    private fun registerTemplate(relative: String, decode: () -> Bitmap?) {
        val canonicalPath = "${TemplateAssets.CANONICAL_PREFIX}/$relative"
        if (templatesByCanonicalPath.containsKey(canonicalPath)) {
            return
        }
        val bitmap = decode() ?: run {
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
