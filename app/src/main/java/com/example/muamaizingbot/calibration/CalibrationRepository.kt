package com.example.muamaizingbot.calibration

import android.content.Context
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CalibrationRepository {

    private const val TAG = "Calibration"
    private const val PREFS_NAME = "hud_calibration"

    private val _state = MutableStateFlow(CalibrationState())
    val state: StateFlow<CalibrationState> = _state.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) {
            return
        }
        loadSaved(context)
        initialized = true
    }

    fun transformFor(captureWidth: Int, captureHeight: Int): CalibrationTransform? {
        val current = _state.value
        if (!current.isComplete) {
            return null
        }
        if (current.captureWidth != captureWidth || current.captureHeight != captureHeight) {
            return null
        }
        return current.transform
    }

    fun transformForActiveCapture(): CalibrationTransform? {
        val size = ScreenCaptureManager.peekLatestBitmapSize() ?: return null
        return transformFor(size.first, size.second)
    }

    fun hasCalibrationFor(captureWidth: Int, captureHeight: Int): Boolean {
        return transformFor(captureWidth, captureHeight) != null
    }

    fun beginSession(captureWidth: Int, captureHeight: Int) {
        _state.value = CalibrationState(
            captureWidth = captureWidth,
            captureHeight = captureHeight,
            screenPoints = emptyMap(),
            inProgress = true,
        )
        Log.d(TAG, "[CAL] session begin capture=${captureWidth}x$captureHeight")
    }

    fun recordScreenPoint(anchor: CalibrationAnchor, screenX: Int, screenY: Int) {
        val current = _state.value
        val updated = current.screenPoints + (anchor to (screenX to screenY))
        _state.value = current.copy(screenPoints = updated)
        Log.d(TAG, "[CAL] point anchor=${anchor.id} screen=($screenX,$screenY)")
    }

    fun completeSession(context: Context): Boolean {
        val current = _state.value
        val transform = CalibrationTransform.fromScreenPoints(
            captureWidth = current.captureWidth,
            captureHeight = current.captureHeight,
            screenPoints = current.screenPoints,
        ) ?: run {
            Log.e(TAG, "[CAL] complete failed — missing points")
            return false
        }
        _state.value = current.copy(
            transform = transform,
            inProgress = false,
            completedAtMs = System.currentTimeMillis(),
        )
        persist(context, _state.value)
        Log.d(TAG, "[CAL] complete capture=${current.captureWidth}x${current.captureHeight}")
        return true
    }

    fun cancelSession() {
        val current = _state.value
        _state.value = if (current.transform != null) {
            current.copy(inProgress = false)
        } else {
            CalibrationState(
                captureWidth = current.captureWidth,
                captureHeight = current.captureHeight,
            )
        }
        Log.d(TAG, "[CAL] session cancelled")
    }

    fun clear(context: Context) {
        _state.value = CalibrationState()
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        Log.d(TAG, "[CAL] cleared")
    }

    private fun persist(context: Context, state: CalibrationState) {
        if (!state.isComplete || state.transform == null) {
            return
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putInt("capture_width", state.captureWidth)
            .putInt("capture_height", state.captureHeight)
            .putLong("completed_at", state.completedAtMs)
        CalibrationAnchor.ordered.forEach { anchor ->
            val point = state.screenPoints[anchor] ?: return
            editor.putInt("${anchor.id}_x", point.first)
            editor.putInt("${anchor.id}_y", point.second)
        }
        editor.apply()
    }

    private fun loadSaved(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val width = prefs.getInt("capture_width", 0)
        val height = prefs.getInt("capture_height", 0)
        if (width <= 0 || height <= 0) {
            return
        }
        val points = linkedMapOf<CalibrationAnchor, Pair<Int, Int>>()
        for (anchor in CalibrationAnchor.ordered) {
            if (!prefs.contains("${anchor.id}_x")) {
                return
            }
            points[anchor] = prefs.getInt("${anchor.id}_x", 0) to prefs.getInt("${anchor.id}_y", 0)
        }
        val transform = CalibrationTransform.fromScreenPoints(width, height, points) ?: return
        _state.value = CalibrationState(
            captureWidth = width,
            captureHeight = height,
            screenPoints = points,
            transform = transform,
            completedAtMs = prefs.getLong("completed_at", 0L),
        )
        Log.d(TAG, "[CAL] loaded capture=${width}x$height")
    }
}

data class CalibrationState(
    val captureWidth: Int = 0,
    val captureHeight: Int = 0,
    val screenPoints: Map<CalibrationAnchor, Pair<Int, Int>> = emptyMap(),
    val transform: CalibrationTransform? = null,
    val inProgress: Boolean = false,
    val completedAtMs: Long = 0L,
) {
    val isComplete: Boolean get() = transform != null && screenPoints.size == CalibrationAnchor.ordered.size
}
