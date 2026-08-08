package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois

object PotionCheckActions {

    private const val TAG = "PotionCheck"
    const val HP_OUT = "templates/mu/ui/hp_potion_out.png"
    const val MP_OUT = "templates/mu/ui/mana_potion_out.png"
    const val POTION_THRESHOLD = 0.96f

    suspend fun isHpPotionEmpty(): Boolean {
        return findEmpty(HP_OUT) != null
    }

    suspend fun isManaPotionEmpty(): Boolean {
        return findEmpty(MP_OUT) != null
    }

    suspend fun isAnyPotionEmpty(): Boolean {
        val frame = NavigationVision.captureFrame() ?: return false
        return try {
            val roi = MuCombatRois.potionSlotsRoi(frame)
            val hp = NavigationVision.findOnFrame(frame, HP_OUT, POTION_THRESHOLD, roi) != null
            val mp = NavigationVision.findOnFrame(frame, MP_OUT, POTION_THRESHOLD, roi) != null
            val empty = hp || mp
            Log.d(TAG, "[POTION] anyEmpty=$empty hp=$hp mp=$mp roi=$roi")
            empty
        } finally {
            frame.recycle()
        }
    }

    /** Shared ROI for potion-slot template taps / probes. */
    fun potionSlotsRoi(): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return MuCombatRois.potionSlotsRoi(w, h)
    }

    private suspend fun findEmpty(assetPath: String) =
        NavigationVision.findTemplate(assetPath, POTION_THRESHOLD, potionSlotsRoi())
}
