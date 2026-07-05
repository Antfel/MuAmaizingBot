package com.example.muamaizingbot.settings

import android.content.Context
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ResolutionSettingsRepository {

    private const val TAG = "ResolutionSettings"
    private const val PREFS_NAME = "resolution_settings"
    private const val KEY_PRESET = "preset"
    private const val KEY_USER_CONFIGURED = "user_configured"

    private val _preset = MutableStateFlow(ResolutionPreset.AUTO)
    val preset: StateFlow<ResolutionPreset> = _preset.asStateFlow()

    private val _detectionResult = MutableStateFlow<ResolutionDetectionResult?>(null)
    val detectionResult: StateFlow<ResolutionDetectionResult?> = _detectionResult.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) {
            return
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userConfigured = prefs.getBoolean(KEY_USER_CONFIGURED, false)
        val stored = prefs.getString(KEY_PRESET, ResolutionPreset.AUTO.name)
        _preset.value = runCatching { ResolutionPreset.valueOf(stored.orEmpty()) }
            .getOrDefault(ResolutionPreset.AUTO)

        val detection = ResolutionDetector.detect(context)
        _detectionResult.value = detection
        Log.d(
            TAG,
            "[RES] detect display=${detection.displayWidth}x${detection.displayHeight} " +
                "match=${detection.matchType} suggested=${detection.suggestedPreset?.resolutionKey}"
        )

        if (!userConfigured && detection.isSupported && detection.suggestedPreset != null) {
            applyPresetInternal(detection.suggestedPreset, context, userConfigured = false)
            Log.d(TAG, "[RES] auto-selected preset=${detection.suggestedPreset}")
        } else if (!userConfigured && !detection.isSupported) {
            Log.w(TAG, "[RES] unsupported display; keeping preset=${_preset.value}")
        }

        initialized = true
        Log.d(TAG, "[RES] init preset=${_preset.value} userConfigured=$userConfigured")
    }

    fun setPreset(preset: ResolutionPreset, context: Context) {
        applyPresetInternal(preset, context, userConfigured = true)
    }

    private fun applyPresetInternal(
        preset: ResolutionPreset,
        context: Context,
        userConfigured: Boolean,
    ) {
        _preset.value = preset
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRESET, preset.name)
            .putBoolean(KEY_USER_CONFIGURED, userConfigured)
            .apply()
        Log.d(TAG, "[RES] preset=$preset userConfigured=$userConfigured")
        TemplateRepository.reloadForActiveResolution()
    }

    /** Prefer live capture size; otherwise preset or 2560×1440 fallback. */
    fun activeScreenSize(): Pair<Int, Int> {
        ScreenCaptureManager.peekLatestBitmapSize()?.let { return it }
        if (_preset.value.isAuto) {
            return ResolutionPreset.REF_2560x1440.width to ResolutionPreset.REF_2560x1440.height
        }
        val preset = _preset.value
        return preset.width to preset.height
    }

    fun detectedCaptureSize(): Pair<Int, Int>? = ScreenCaptureManager.peekLatestBitmapSize()

    fun captureMatchesPreset(): Boolean {
        val capture = detectedCaptureSize() ?: return true
        val active = activeScreenSize()
        return capture.first == active.first && capture.second == active.second
    }
}
