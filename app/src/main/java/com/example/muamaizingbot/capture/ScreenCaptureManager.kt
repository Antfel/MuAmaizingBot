package com.example.muamaizingbot.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    @Volatile
    private var latestBitmap: Bitmap? = null

    internal fun setActive(active: Boolean) {
        _isActive.value = active
        Log.d(TAG, "[CAPTURE] active=$active")
    }

    internal fun updateFrame(frame: Bitmap) {
        synchronized(this) {
            latestBitmap?.recycle()
            latestBitmap = frame
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
