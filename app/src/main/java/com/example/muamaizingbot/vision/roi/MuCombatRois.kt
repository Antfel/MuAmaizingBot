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

    /** Bottom-right skill cluster (Greater Defense / Greater Damage, etc.). */
    fun skillBarRoi(frame: Bitmap): Rect {
        return ScaledRoi.fromRefRect(
            left = 1600,
            top = 550,
            right = 2560,
            bottom = 1440,
            frameWidth = frame.width,
            frameHeight = frame.height,
        )
    }

    /**
     * Bottom combat cluster: closed PK label (All) + Focus player button.
     * Logical ref 2560×1440.
     */
    fun targetingHudRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = 1600,
            top = 900,
            right = 2560,
            bottom = 1440,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun targetingHudRoi(frame: Bitmap): Rect = targetingHudRoi(frame.width, frame.height)

    /**
     * PK mode popup: option boxes that open **above** the All button (~10s visible).
     * Covers UnionKuaFu / All rows in that stack.
     */
    fun pkModePopupRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = 1400,
            top = 500,
            right = 2300,
            bottom = 1400,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }
}
