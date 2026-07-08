package com.example.muamaizingbot.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.muamaizingbot.calibration.CalibrationRepository
import com.example.muamaizingbot.settings.ResolutionSettingsRepository
import com.example.muamaizingbot.vision.template.TemplateAssets
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    @Volatile
    private var latestBitmap: Bitmap? = null

    @Volatile
    private var lastCaptureSize: Pair<Int, Int>? = null

    internal fun setActive(active: Boolean) {
        _isActive.value = active
        if (!active) {
            lastCaptureSize = null
        }
        Log.d(TAG, "[CAPTURE] active=$active")
    }

    internal fun updateFrame(frame: Bitmap) {
        synchronized(this) {
            latestBitmap?.recycle()
            latestBitmap = frame
        }
        maybeReloadTemplatesForCapture(frame.width, frame.height)
    }

    private fun maybeReloadTemplatesForCapture(width: Int, height: Int) {
        val size = width to height
        if (size == lastCaptureSize) {
            return
        }
        lastCaptureSize = size

        CalibrationRepository.ensureCaptureSize(width, height)

        val calibrated = CalibrationRepository.hasCalibrationFor(width, height)
        if (calibrated) {
            Log.d(TAG, "[CAPTURE] reloading calibrated templates for ${width}x$height")
            TemplateRepository.reloadForCapture(width, height)
            return
        }

        if (!ResolutionSettingsRepository.preset.value.isAuto) {
            val key = ResolutionSettingsRepository.preset.value.resolutionKey
            if (TemplateRepository.currentResolutionKey() != key) {
                TemplateRepository.reload(key)
            }
            return
        }

        val key = TemplateAssets.snapToSupported(width, height)
        if (TemplateRepository.currentResolutionKey() != key) {
            Log.d(TAG, "[CAPTURE] reloading preset templates for ${width}x$height -> $key")
            TemplateRepository.reloadForCapture(width, height)
        }
    }

    fun getLatestBitmap(): Bitmap? {
        synchronized(this) {
            val frame = latestBitmap ?: return null
            if (frame.isRecycled) {
                return null
            }
            return frame.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    fun peekLatestBitmapSize(): Pair<Int, Int>? {
        synchronized(this) {
            val frame = latestBitmap ?: return null
            if (frame.isRecycled) {
                return null
            }
            return frame.width to frame.height
        }
    }

    internal fun clearFrame() {
        synchronized(this) {
            latestBitmap?.recycle()
            latestBitmap = null
        }
    }

    fun start(context: Context, resultCode: Int, data: Intent) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, ScreenCaptureService::class.java))
        clearFrame()
        setActive(false)
    }
}
