package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationTemplateThresholds
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Inventory-full → open bag → Recycle → confirm → optional daily Clue → close.
 * Same maintenance priority tier as potion purchase.
 *
 * After the internal Recycle confirm, the game may show a "Clue" dialog
 * (MU Coin Bonus Card) the first time each day / periodically. Tap its red
 * Recycle to finish; if absent, treat recycle as already done.
 */
object InventoryRecycleActions {

    private const val TAG = "InventoryRecycle"

    private const val INVENTORY_FULL = "templates/mu/ui/inventory_full.png"
    private const val RECYCLE_BUTTON = "templates/mu/ui/recycle_button.png"
    private const val RECYCLE_INTERNAL = "templates/mu/ui/recycle_button_internal.png"
    /** Optional daily/periodic "Clue" dialog after internal confirm. */
    private const val RECYCLE_CLUE_POPUP = "templates/mu/ui/recycle_clue_popup.png"
    /** Red "Recycle" on the Clue dialog (decline Bonus Card, proceed). */
    private const val RECYCLE_CLUE_RECYCLE = "templates/mu/ui/recycle_clue_recycle_button.png"
    private const val CLOSE_X = MapWindowActions.CLOSE_X

    private const val FULL_THRESHOLD = 0.92f
    private const val BUTTON_THRESHOLD = 0.80f
    private const val INTERNAL_THRESHOLD = 0.80f
    private const val CLUE_THRESHOLD = 0.80f

    private const val UI_SETTLE_MS = 600L
    private const val FIND_TIMEOUT_MS = 5_000L
    private const val POST_RECYCLE_MS = 800L
    /** Short window: popup is optional; absence means recycle already finished. */
    private const val CLUE_WAIT_MS = 2_500L

    suspend fun handleFullInventory(): Boolean {
        DisconnectDetector.beginUiAction("inventory-recycle")
        try {
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
            delay(UI_SETTLE_MS)

            dismissRecycleClueIfPresent()

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
        } finally {
            DisconnectDetector.endUiAction("inventory-recycle")
        }
    }

    /**
     * If the MU Coin Bonus Card "Clue" appears, tap red Recycle to proceed.
     * If it never shows within [CLUE_WAIT_MS], recycle already completed.
     */
    private suspend fun dismissRecycleClueIfPresent() {
        val deadline = System.currentTimeMillis() + CLUE_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val clueVisible = NavigationVision.findTemplate(RECYCLE_CLUE_POPUP, CLUE_THRESHOLD) != null
            val recycleBtn = NavigationVision.findTemplate(RECYCLE_CLUE_RECYCLE, CLUE_THRESHOLD)
            if (clueVisible || recycleBtn != null) {
                val tapTarget = recycleBtn
                    ?: NavigationVision.findTemplate(RECYCLE_CLUE_RECYCLE, CLUE_THRESHOLD)
                if (tapTarget == null) {
                    Log.w(TAG, "[RECYCLE] clue popup visible but Recycle button missing")
                    return
                }
                Log.d(TAG, "[RECYCLE] clue popup → tap Recycle")
                NavigationVision.tapMatch(tapTarget)
                delay(POST_RECYCLE_MS)
                return
            }
            delay(200L)
        }
        Log.d(TAG, "[RECYCLE] no clue popup — recycle already confirmed")
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
