package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.vision.navigation.NavigationVision

/** Detects the HUD "inventory full" bag icon (circular-masked template). */
object InventoryCheckActions {

    private const val TAG = "InventoryCheck"
    private const val INVENTORY_FULL = "templates/mu/ui/inventory_full.png"
    /** Circular mask hit ~0.98 on live 5574; keep headroom under that. */
    private const val FULL_THRESHOLD = 0.92f

    suspend fun isInventoryFull(): Boolean {
        val full = NavigationVision.findTemplate(INVENTORY_FULL, FULL_THRESHOLD) != null
        Log.d(TAG, "[INVENTORY] full=$full")
        return full
    }
}
