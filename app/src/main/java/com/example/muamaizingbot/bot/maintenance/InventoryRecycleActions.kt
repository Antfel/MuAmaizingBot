package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationTemplateThresholds
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Inventory-full → open bag → Recycle → confirm → close.
 * Same maintenance priority tier as potion purchase.
 */
object InventoryRecycleActions {

    private const val TAG = "InventoryRecycle"

    private const val INVENTORY_FULL = "templates/mu/ui/inventory_full.png"
    private const val RECYCLE_BUTTON = "templates/mu/ui/recycle_button.png"
    private const val RECYCLE_INTERNAL = "templates/mu/ui/recycle_button_internal.png"
    private const val CLOSE_X = MapWindowActions.CLOSE_X

    private const val FULL_THRESHOLD = 0.92f
    private const val BUTTON_THRESHOLD = 0.80f
    private const val INTERNAL_THRESHOLD = 0.80f

    private const val UI_SETTLE_MS = 600L
    private const val FIND_TIMEOUT_MS = 5_000L
    private const val POST_RECYCLE_MS = 800L

    suspend fun handleFullInventory(): Boolean {
        if (DeathActions.isDead()) {
            Log.d(TAG, "[RECYCLE] dead before recycle; reviving first")
            if (!DeathActions.recoverIfDead()) {
                return false
            }
        }

        if (!InventoryCheckActions.isInventoryFull()) {
            Log.d(TAG, "[RECYCLE] inventory not full — skip")
            return true
        }

        Log.d(TAG, "[RECYCLE] start: tap inventory_full")
        if (!NavigationVision.tapTemplate(INVENTORY_FULL, FULL_THRESHOLD)) {
            Log.w(TAG, "[RECYCLE] inventory_full tap failed")
            return BotRecoveryActions.recoverFromLostState("recycle-open-failed")
        }
        delay(UI_SETTLE_MS)

        Log.d(TAG, "[RECYCLE] wait recycle_button")
        val bagRecycle = NavigationVision.waitForTemplate(
            RECYCLE_BUTTON,
            BUTTON_THRESHOLD,
            FIND_TIMEOUT_MS,
        )
        if (bagRecycle == null) {
            Log.w(TAG, "[RECYCLE] recycle_button not found")
            dismissPanels()
            return BotRecoveryActions.recoverFromLostState("recycle-button-missing")
        }
        NavigationVision.tapMatch(bagRecycle)
        delay(UI_SETTLE_MS)

        Log.d(TAG, "[RECYCLE] wait recycle_button_internal")
        val confirm = NavigationVision.waitForTemplate(
            RECYCLE_INTERNAL,
            INTERNAL_THRESHOLD,
            FIND_TIMEOUT_MS,
        )
        if (confirm == null) {
            Log.w(TAG, "[RECYCLE] recycle_button_internal not found")
            dismissPanels()
            return BotRecoveryActions.recoverFromLostState("recycle-confirm-missing")
        }
        NavigationVision.tapMatch(confirm)
        delay(POST_RECYCLE_MS)

        Log.d(TAG, "[RECYCLE] tap close_x")
        if (!NavigationVision.tapTemplate(CLOSE_X, NavigationTemplateThresholds.closeX())) {
            Log.w(TAG, "[RECYCLE] close_x not found after recycle")
        }
        delay(UI_SETTLE_MS)
        // Inventory panel may still be open after closing the recycle sub-panel.
        dismissPanels()

        val stillFull = InventoryCheckActions.isInventoryFull()
        Log.d(TAG, "[RECYCLE] done stillFull=$stillFull")
        return true
    }

    private suspend fun dismissPanels() {
        repeat(2) {
            val close = NavigationVision.findTemplate(
                CLOSE_X,
                NavigationTemplateThresholds.closeX(),
            ) ?: return
            NavigationVision.tapMatch(close)
            delay(UI_SETTLE_MS)
        }
    }
}
