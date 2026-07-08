package com.example.muamaizingbot.bot.navigation

import com.example.muamaizingbot.vision.template.TemplateRepository

object NavigationTemplateThresholds {

    private const val MAP_WINDOW = 0.80f
    /** Calibrated PNGs can be a few px off; keep threshold at or below preset. */
    private const val MAP_WINDOW_CALIBRATED = 0.76f
    private const val CLOSE_X = 0.82f
    private const val CLOSE_X_CALIBRATED = 0.82f

    fun mapWindow(): Float {
        return if (TemplateRepository.isUsingCalibratedTemplates()) MAP_WINDOW_CALIBRATED else MAP_WINDOW
    }

    fun closeX(): Float {
        return if (TemplateRepository.isUsingCalibratedTemplates()) CLOSE_X_CALIBRATED else CLOSE_X
    }
}
