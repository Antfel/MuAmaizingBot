package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois

/** Detects the HUD "inventory full" bag icon (circular-masked template). */
object InventoryCheckActions {

    private const val TAG = "InventoryCheck"
    const val INVENTORY_FULL = "templates/mu/ui/inventory_full.png"
    /** Circular mask hit ~0.98 on live 5574; keep headroom under that. */
    const val FULL_THRESHOLD = 0.92f

    suspend fun isInventoryFull(): Boolean {
        val roi = inventoryFullRoi()
        val full = NavigationVision.findTemplate(INVENTORY_FULL, FULL_THRESHOLD, roi) != null
        Log.d(TAG, "[INVENTORY] full=$full roi=$roi")
        return full
    }

    /** Shared ROI for inventory-full template probes / taps. */
    fun inventoryFullRoi(): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return MuCombatRois.inventoryFullRoi(w, h)
    }
}
