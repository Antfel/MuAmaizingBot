package com.example.muamaizingbot.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.muamaizingbot.calibration.CalibrationRepository
import com.example.muamaizingbot.capture.ScreenCaptureManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CalibrationOverlayManager {

    @Volatile
    var isRunning: Boolean = false
        internal set

    private val _stepIndex = MutableStateFlow(0)
    val stepIndex: StateFlow<Int> = _stepIndex.asStateFlow()

    internal fun setStep(index: Int) {
        _stepIndex.value = index
    }

    fun start(context: Context): StartResult {
        val captureSize = ScreenCaptureManager.peekLatestBitmapSize()
            ?: return StartResult.NO_CAPTURE
        if (OverlayManager.isRunning) {
            OverlayManager.stop(context)
        }
        CalibrationRepository.beginSession(captureSize.first, captureSize.second)
        _stepIndex.value = 0
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, CalibrationOverlayService::class.java),
        )
        return StartResult.OK
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(
            Intent(context.applicationContext, CalibrationOverlayService::class.java),
        )
    }

    enum class StartResult {
        OK,
        NO_CAPTURE,
    }
}
