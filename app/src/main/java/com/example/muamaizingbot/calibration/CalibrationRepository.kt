package com.example.muamaizingbot.calibration

import android.content.Context
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CalibrationRepository {

    private const val TAG = "Calibration"
    private const val PREFS_NAME = "hud_calibration"

    private val _state = MutableStateFlow(CalibrationState())
    val state: StateFlow<CalibrationState> = _state.asStateFlow()

    private var initialized = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        if (initialized) {
            return
        }
        appContext = context.applicationContext
        loadSaved(appContext)
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

    /**
     * If capture resolution changed vs saved calibration, drop calibration + device templates.
     */
    fun ensureCaptureSize(captureWidth: Int, captureHeight: Int) {
        if (!initialized) {
            return
        }
        val current = _state.value
        if (!current.isComplete) {
            return
        }
        if (current.captureWidth == captureWidth && current.captureHeight == captureHeight) {
            return
        }
        Log.w(
            TAG,
            "[CAL] capture changed ${current.captureWidth}x${current.captureHeight} " +
                "-> ${captureWidth}x$captureHeight — invalidating calibration",
        )
        invalidateCalibration(appContext, recalibrationRequired = true)
    }

    fun beginSession(captureWidth: Int, captureHeight: Int) {
        _state.value = CalibrationState(
            captureWidth = captureWidth,
            captureHeight = captureHeight,
            screenRects = emptyMap(),
            inProgress = true,
        )
        Log.d(TAG, "[CAL] session begin capture=${captureWidth}x$captureHeight")
    }

    fun recordScreenRect(anchor: CalibrationAnchor, rect: CalibrationScreenRect) {
        val current = _state.value
        val updated = current.screenRects + (anchor to rect)
        _state.value = current.copy(screenRects = updated)
        Log.d(
            TAG,
            "[CAL] rect anchor=${anchor.id} center=(${rect.centerX},${rect.centerY}) " +
                "size=${rect.width}x${rect.height}",
        )
    }

    fun completeSession(context: Context): Boolean {
        val current = _state.value
        val transform = CalibrationTransform.fromScreenRects(
            captureWidth = current.captureWidth,
            captureHeight = current.captureHeight,
            screenRects = current.screenRects,
        ) ?: run {
            Log.e(TAG, "[CAL] complete failed — missing rects")
            return false
        }
        _state.value = current.copy(
            transform = transform,
            inProgress = false,
            completedAtMs = System.currentTimeMillis(),
            templatesGenerated = false,
            recalibrationRequired = false,
        )
        persist(appContext, _state.value)
        Log.d(TAG, "[CAL] complete capture=${current.captureWidth}x${current.captureHeight}")

        Thread {
            runCatching {
                val count = CalibratedTemplateGenerator.generate(
                    context.applicationContext,
                    transform,
                    current.captureWidth,
                    current.captureHeight,
                )
                _state.value = _state.value.copy(
                    templatesGenerated = true,
                    calibratedTemplateCount = count,
                )
                TemplateRepository.reloadForCapture(current.captureWidth, current.captureHeight)
                Log.d(TAG, "[CAL] device templates ready count=$count")
            }.onFailure { error ->
                Log.e(TAG, "[CAL] device template generation failed", error)
            }
        }.start()

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
        invalidateCalibration(context.applicationContext, recalibrationRequired = false)
    }

    fun invalidateCalibration(context: Context, recalibrationRequired: Boolean) {
        CalibratedTemplateStore.clearAll(context.applicationContext)
        _state.value = CalibrationState(recalibrationRequired = recalibrationRequired)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        TemplateRepository.reloadForActiveResolution()
        Log.d(TAG, "[CAL] invalidated recalibrationRequired=$recalibrationRequired")
    }

    fun expectedTemplateSize(
        anchor: CalibrationAnchor,
        captureWidth: Int,
        captureHeight: Int,
    ): Pair<Int, Int> {
        val w = (anchor.refTemplateWidth.toLong() * captureWidth / RefCoords.REF_WIDTH).toInt()
        val h = (anchor.refTemplateHeight.toLong() * captureHeight / RefCoords.REF_HEIGHT).toInt()
        return w to h
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
            .putBoolean("templates_generated", state.templatesGenerated)
            .putInt("calibrated_template_count", state.calibratedTemplateCount)
        CalibrationAnchor.ordered.forEach { anchor ->
            val rect = state.screenRects[anchor] ?: return
            editor.putInt("${anchor.id}_x", rect.centerX)
            editor.putInt("${anchor.id}_y", rect.centerY)
            editor.putInt("${anchor.id}_w", rect.width)
            editor.putInt("${anchor.id}_h", rect.height)
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
        val rects = linkedMapOf<CalibrationAnchor, CalibrationScreenRect>()
        for (anchor in CalibrationAnchor.ordered) {
            if (!prefs.contains("${anchor.id}_x")) {
                return
            }
            val cx = prefs.getInt("${anchor.id}_x", 0)
            val cy = prefs.getInt("${anchor.id}_y", 0)
            val (expectedW, expectedH) = expectedTemplateSize(anchor, width, height)
            val w = prefs.getInt("${anchor.id}_w", expectedW).coerceAtLeast(1)
            val h = prefs.getInt("${anchor.id}_h", expectedH).coerceAtLeast(1)
            rects[anchor] = CalibrationScreenRect(cx, cy, w, h)
        }
        val transform = CalibrationTransform.fromScreenRects(width, height, rects) ?: return
        val templatesGenerated = prefs.getBoolean("templates_generated", false)
        val templateCount = prefs.getInt("calibrated_template_count", 0)
        _state.value = CalibrationState(
            captureWidth = width,
            captureHeight = height,
            screenRects = rects,
            transform = transform,
            completedAtMs = prefs.getLong("completed_at", 0L),
            templatesGenerated = templatesGenerated,
            calibratedTemplateCount = templateCount,
        )
        if (templatesGenerated && CalibratedTemplateStore.exists(context, width, height)) {
            TemplateRepository.reloadForCapture(width, height)
        }
        Log.d(TAG, "[CAL] loaded capture=${width}x$height templates=$templatesGenerated")
    }
}

data class CalibrationState(
    val captureWidth: Int = 0,
    val captureHeight: Int = 0,
    val screenRects: Map<CalibrationAnchor, CalibrationScreenRect> = emptyMap(),
    val transform: CalibrationTransform? = null,
    val inProgress: Boolean = false,
    val completedAtMs: Long = 0L,
    val templatesGenerated: Boolean = false,
    val calibratedTemplateCount: Int = 0,
    val recalibrationRequired: Boolean = false,
) {
    val isComplete: Boolean get() = transform != null && screenRects.size == CalibrationAnchor.ordered.size
}
