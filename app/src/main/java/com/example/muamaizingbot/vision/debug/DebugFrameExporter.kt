package com.example.muamaizingbot.vision.debug

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DebugFrameExporter {

    private const val TAG = "DebugFrameExporter"
    const val SUBDIR = "debug_capture"

    fun exportDirectory(context: Context): File {
        return File(context.getExternalFilesDir(null), SUBDIR).apply { mkdirs() }
    }

    fun adbPullCommand(context: Context): String {
        val dir = exportDirectory(context)
        return "adb pull \"${dir.absolutePath}/\" ./debug_capture/"
    }

    suspend fun saveFullFrame(context: Context, label: String): String? {
        return withContext(Dispatchers.IO) {
            val frame = ScreenCaptureManager.getLatestBitmap()
            if (frame == null) {
                Log.w(TAG, "[RECAPTURE] save skipped label=$label reason=no_frame")
                return@withContext null
            }
            try {
                saveBitmap(context, frame, label)
            } finally {
                frame.recycle()
            }
        }
    }

    suspend fun saveCropFromMatch(
        context: Context,
        result: PcTemplateMatchResult
    ): String? {
        return withContext(Dispatchers.IO) {
            val frame = ScreenCaptureManager.getLatestBitmap()
            if (frame == null) {
                Log.w(TAG, "[RECAPTURE] crop skipped template=${result.templateName} reason=no_frame")
                return@withContext null
            }
            try {
                val left = result.bestX.coerceIn(0, frame.width - 1)
                val top = result.bestY.coerceIn(0, frame.height - 1)
                val width = result.templateWidth.coerceAtMost(frame.width - left)
                val height = result.templateHeight.coerceAtMost(frame.height - top)
                if (width <= 0 || height <= 0) {
                    Log.w(TAG, "[RECAPTURE] crop skipped template=${result.templateName} reason=invalid_rect")
                    return@withContext null
                }
                val crop = Bitmap.createBitmap(frame, left, top, width, height)
                try {
                    saveBitmap(
                        context = context,
                        bitmap = crop,
                        label = result.templateName,
                        suffix = "crop_${left}x${top}"
                    )
                } finally {
                    crop.recycle()
                }
            } finally {
                frame.recycle()
            }
        }
    }

    private fun saveBitmap(
        context: Context,
        bitmap: Bitmap,
        label: String,
        suffix: String? = null
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeLabel = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val nameSuffix = suffix?.let { "_$it" }.orEmpty()
        val fileName = "${safeLabel}${nameSuffix}_$timestamp.png"
        val file = File(exportDirectory(context), fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.i(
            TAG,
            "[RECAPTURE] saved path=${file.absolutePath} size=${bitmap.width}x${bitmap.height}"
        )
        Log.i(TAG, "[RECAPTURE] pull: ${adbPullCommand(context)}")
        return file.absolutePath
    }
}
