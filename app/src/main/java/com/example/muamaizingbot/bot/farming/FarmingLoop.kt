package com.example.muamaizingbot.bot.farming

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

object FarmingLoop {

    private const val TAG = "Farming"
    private const val CYCLE_WAIT_MS = 3000L
    private const val INVENTORY_OPEN = "templates/mu/ui/inventory_open.png"

    enum class CycleResult {
        OK,
        /** Transient miss — stay on spot, retry next loop (auto off, inventory, etc.). */
        SOFT_FAIL,
        /** Dead — handled by death branch, not farm recovery. */
        DEAD,
    }

    suspend fun runCycle(): CycleResult {
        Log.d(TAG, "[FARMING] cycle start")

        if (DeathActions.isDead()) {
            Log.w(TAG, "[FARMING] dead during farm cycle")
            return CycleResult.DEAD
        }

        if (!ensureInventoryClosed()) {
            Log.w(TAG, "[FARMING] inventory check failed; will retry")
            return CycleResult.SOFT_FAIL
        }

        if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[FARMING] ensure auto failed; will retry on spot")
            return CycleResult.SOFT_FAIL
        }

        Log.d(TAG, "[FARMING] farming OK")
        delay(CYCLE_WAIT_MS)
        return CycleResult.OK
    }

    private suspend fun ensureInventoryClosed(): Boolean {
        val open = NavigationVision.findTemplate(INVENTORY_OPEN, 0.8f)
        if (open == null) {
            return true
        }
        Log.d(TAG, "[FARMING] closing inventory")
        if (NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, 0.8f)) {
            delay(500)
            return NavigationVision.findTemplate(INVENTORY_OPEN, 0.8f) == null
        }
        return false
    }
}
