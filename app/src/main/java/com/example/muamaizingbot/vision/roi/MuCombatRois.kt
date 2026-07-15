package com.example.muamaizingbot.vision.roi

import android.graphics.Bitmap
import android.graphics.Rect

object MuCombatRois {

    // auto_text / manual_text viven arriba-derecha (logical ref 2560×1440), no en el HUD inferior.
    // Validado con frames BlueStacks: auto ~0.89 @ (2445,692), manual ~0.98 @ (2427,617).
    private const val AUTO_HUD_LEFT = 2300
    private const val AUTO_HUD_TOP = 550
    private const val AUTO_HUD_RIGHT = 2560
    private const val AUTO_HUD_BOTTOM = 800

    fun autoHudRoi(frame: Bitmap): Rect {
        return ScaledRoi.fromRefRect(
            left = AUTO_HUD_LEFT,
            top = AUTO_HUD_TOP,
            right = AUTO_HUD_RIGHT,
            bottom = AUTO_HUD_BOTTOM,
            frameWidth = frame.width,
            frameHeight = frame.height
        )
    }

    /** Death / revive dialog (logical ref 2560×1440). Revive button ~(1120,865). */
    fun deathDialogRoi(frame: Bitmap): Rect {
        return ScaledRoi.fromRefRect(
            left = 500,
            top = 550,
            right = 1900,
            bottom = 1100,
            frameWidth = frame.width,
            frameHeight = frame.height
        )
    }
}
