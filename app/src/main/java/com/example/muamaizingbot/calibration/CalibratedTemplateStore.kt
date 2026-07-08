package com.example.muamaizingbot.calibration

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/** Device-local PNG cache keyed by exact capture resolution. */
object CalibratedTemplateStore {

    private const val TAG = "CalTemplates"
    private const val ROOT_DIR = "calibrated_templates"
    private const val MANIFEST = "manifest.json"

    fun rootDir(context: Context): File {
        return File(context.applicationContext.getExternalFilesDir(null), ROOT_DIR)
    }

    fun captureDir(context: Context, captureWidth: Int, captureHeight: Int): File {
        return File(rootDir(context), "${captureWidth}x$captureHeight")
    }

    fun templatesRoot(context: Context, captureWidth: Int, captureHeight: Int): File {
        return File(captureDir(context, captureWidth, captureHeight), "mu")
    }

    fun exists(context: Context, captureWidth: Int, captureHeight: Int): Boolean {
        val manifest = File(captureDir(context, captureWidth, captureHeight), MANIFEST)
        val muRoot = templatesRoot(context, captureWidth, captureHeight)
        return manifest.exists() && muRoot.exists() && muRoot.walkTopDown().any { it.isFile && it.extension == "png" }
    }

    fun templateFile(
        context: Context,
        captureWidth: Int,
        captureHeight: Int,
        relativePath: String,
    ): File {
        return File(templatesRoot(context, captureWidth, captureHeight), relativePath)
    }

    fun writeManifest(
        context: Context,
        captureWidth: Int,
        captureHeight: Int,
        templateCount: Int,
        scaleX: Float,
        scaleY: Float,
    ) {
        val payload = JSONObject()
            .put("capture_width", captureWidth)
            .put("capture_height", captureHeight)
            .put("source_resolution", TemplateSourceResolution.KEY)
            .put("scale_x", scaleX.toDouble())
            .put("scale_y", scaleY.toDouble())
            .put("template_count", templateCount)
            .put("generated_at_ms", System.currentTimeMillis())
        val file = File(captureDir(context, captureWidth, captureHeight), MANIFEST)
        file.parentFile?.mkdirs()
        file.writeText(payload.toString(2))
    }

    fun clearForCapture(context: Context, captureWidth: Int, captureHeight: Int) {
        val dir = captureDir(context, captureWidth, captureHeight)
        if (dir.exists()) {
            dir.deleteRecursively()
            Log.d(TAG, "[CAL-TPL] cleared capture=${captureWidth}x$captureHeight")
        }
    }

    fun clearAll(context: Context) {
        val dir = rootDir(context)
        if (dir.exists()) {
            dir.deleteRecursively()
            Log.d(TAG, "[CAL-TPL] cleared all calibrated templates")
        }
    }

    fun clearExcept(context: Context, captureWidth: Int, captureHeight: Int) {
        val root = rootDir(context)
        if (!root.exists()) {
            return
        }
        val keep = "${captureWidth}x$captureHeight"
        root.listFiles()?.forEach { entry ->
            if (entry.isDirectory && entry.name != keep) {
                entry.deleteRecursively()
                Log.d(TAG, "[CAL-TPL] removed stale dir=${entry.name}")
            }
        }
    }
}

internal object TemplateSourceResolution {
    const val KEY = "2560x1440"
    const val ASSET_ROOT = "templates/2560x1440/mu"
}
