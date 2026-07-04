package com.example.muamaizingbot.bot.maintenance

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions.isHpPotionEmpty
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions.isManaPotionEmpty
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

object PotionPurchaseActions {

    private const val TAG = "PotionPurchase"

    private const val HP_OUT = "templates/mu/ui/hp_potion_out.png"
    private const val MP_OUT = "templates/mu/ui/mana_potion_out.png"
    private const val POTION_THRESHOLD = 0.96f
    private const val POTION_CLUE = "templates/mu/ui/potion_clue_popup.png"
    private const val POTION_TELEPORT = "templates/mu/ui/potion_teleport_button.png"
    private const val SHOP_OPEN = "templates/mu/ui/common/shop_open.png"
    private const val TELEPORT_THRESHOLD = 0.8f
    private const val SHOP_THRESHOLD = 0.50f

    private const val HP_BUY_X = 2382
    private const val HP_BUY_Y = 473
    private const val MP_BUY_X = 2382
    private const val MP_BUY_Y = 830
    private const val SHOP_CLOSE_X = 2520
    private const val SHOP_CLOSE_Y = 45

    private const val TAP_SLOT_WAIT_MS = 1000L
    private const val ENTRY_POLL_MS = 500L
    private const val ENTRY_TIMEOUT_MS = 8000L
    private const val TELEPORT_ACCEPT_WAIT_MS = 5000L
    private const val SHOP_OPEN_TIMEOUT_MS = 10_000L
    private const val CLOSE_SHOP_WAIT_MS = 1000L
    private const val POST_SHOP_SETTLE_MS = 2000L
    private const val BUY_FIRST_TAP_MS = 400L
    private const val BUY_SECOND_TAP_MS = 600L
    private const val REFILL_TIMEOUT_MS = 10_000L

    suspend fun handleEmptyPotions(): Boolean {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[POTION] no active profile")
            return false
        }

        if (DeathActions.isDead()) {
            Log.d(TAG, "[POTION] dead before purchase; reviving first")
            if (!DeathActions.recoverIfDead()) {
                return false
            }
        }

        val hpEmpty = isHpPotionEmpty()
        val mpEmpty = isManaPotionEmpty()
        if (!hpEmpty && !mpEmpty) {
            Log.d(TAG, "[POTION] no empty potions")
            return true
        }

        Log.d(TAG, "[POTION] starting recovery hpEmpty=$hpEmpty mpEmpty=$mpEmpty")
        if (!tapEmptyPotionSlot(hpEmpty, mpEmpty)) {
            return BotRecoveryActions.recoverFromLostState("potion-tap-failed")
        }

        delay(TAP_SLOT_WAIT_MS)
        val entry = waitForPotionEntryResult() ?: run {
            Log.w(TAG, "[POTION] entry flow unknown")
            return BotRecoveryActions.recoverFromLostState("potion-entry-unknown")
        }

        when (entry) {
            PotionEntry.TELEPORT_POPUP -> {
                if (!acceptPotionTeleportPopup()) {
                    return BotRecoveryActions.recoverFromLostState("potion-teleport-failed")
                }
                if (!waitForShopOpen()) {
                    Log.w(TAG, "[POTION] shop did not open after teleport")
                    return BotRecoveryActions.recoverFromLostState("potion-shop-timeout")
                }
            }
            PotionEntry.SHOP_OPEN -> Unit
        }

        buyPotions(
            hpAmount = if (hpEmpty) profile.hpPotionStacks else 0,
            mpAmount = if (mpEmpty) profile.mpPotionStacks else 0,
        )

        Log.d(TAG, "[POTION] purchase done, closing shop")
        closeShop()

        if (!waitForPurchasedPotions(hpEmpty, mpEmpty)) {
            Log.w(TAG, "[POTION] slots not refilled; attempting recovery")
            return BotRecoveryActions.recoverFromLostState("potion-refill-timeout")
        }

        return finishPotionRecovery(entry == PotionEntry.TELEPORT_POPUP)
    }

    private enum class PotionEntry {
        TELEPORT_POPUP,
        SHOP_OPEN,
    }

    private suspend fun finishPotionRecovery(needsNavigation: Boolean): Boolean {
        delay(POST_SHOP_SETTLE_MS)
        NavigationOrchestrator.cleanGameUi()

        if (needsNavigation) {
            Log.d(TAG, "[POTION] teleport purchase; navigating back to farm")
            if (!BotRecoveryActions.navigateToFarmWithRetry("post-potion-teleport")) {
                return BotRecoveryActions.recoverFromLostState("post-potion-nav-failed")
            }
        } else {
            Log.d(TAG, "[POTION] direct shop purchase; staying at spot")
        }

        if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[POTION] ensureAutoMode failed; farm loop will retry")
        }

        Log.d(TAG, "[POTION] recovery completed")
        return true
    }

    private suspend fun waitForPurchasedPotions(hpWasEmpty: Boolean, mpWasEmpty: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + REFILL_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val hpOk = !hpWasEmpty || !isHpPotionEmpty()
            val mpOk = !mpWasEmpty || !isManaPotionEmpty()
            if (hpOk && mpOk) {
                Log.d(TAG, "[POTION] slots refilled")
                return true
            }
            delay(500)
        }
        return false
    }

    private suspend fun tapEmptyPotionSlot(hpEmpty: Boolean, mpEmpty: Boolean): Boolean {
        return when {
            hpEmpty -> {
                Log.d(TAG, "[POTION] tapping empty HP slot")
                NavigationVision.tapTemplate(HP_OUT, POTION_THRESHOLD)
            }
            mpEmpty -> {
                Log.d(TAG, "[POTION] tapping empty MP slot")
                NavigationVision.tapTemplate(MP_OUT, POTION_THRESHOLD)
            }
            else -> false
        }
    }

    private suspend fun waitForPotionEntryResult(): PotionEntry? {
        val deadline = System.currentTimeMillis() + ENTRY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val frame = NavigationVision.captureFrame() ?: run {
                delay(ENTRY_POLL_MS)
                continue
            }
            try {
                val shopRegion = shopSearchRegion(frame)
                val teleport = NavigationVision.findOnFrame(frame, POTION_CLUE, TELEPORT_THRESHOLD)
                val shop = NavigationVision.findOnFrame(frame, SHOP_OPEN, SHOP_THRESHOLD, shopRegion)
                if (teleport != null) {
                    Log.d(TAG, "[POTION] teleport popup detected")
                    return PotionEntry.TELEPORT_POPUP
                }
                if (shop != null) {
                    Log.d(TAG, "[POTION] shop opened directly")
                    return PotionEntry.SHOP_OPEN
                }
            } finally {
                frame.recycle()
            }
            delay(ENTRY_POLL_MS)
        }
        return null
    }

    private suspend fun acceptPotionTeleportPopup(): Boolean {
        if (NavigationVision.findTemplate(POTION_CLUE, TELEPORT_THRESHOLD) == null) {
            Log.w(TAG, "[POTION] teleport popup not visible")
            return false
        }
        val teleport = NavigationVision.findTemplate(POTION_TELEPORT, TELEPORT_THRESHOLD)
            ?: run {
                Log.w(TAG, "[POTION] teleport button not found")
                return false
            }
        Log.d(TAG, "[POTION] accepting teleport to shop")
        NavigationVision.tapMatch(teleport)
        delay(TELEPORT_ACCEPT_WAIT_MS)
        return true
    }

    private suspend fun waitForShopOpen(): Boolean {
        val deadline = System.currentTimeMillis() + SHOP_OPEN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val frame = NavigationVision.captureFrame() ?: run {
                delay(ENTRY_POLL_MS)
                continue
            }
            try {
                if (NavigationVision.findOnFrame(frame, SHOP_OPEN, SHOP_THRESHOLD, shopSearchRegion(frame)) != null) {
                    return true
                }
            } finally {
                frame.recycle()
            }
            delay(ENTRY_POLL_MS)
        }
        return false
    }

    private suspend fun buyPotions(hpAmount: Int, mpAmount: Int) {
        if (hpAmount > 0) {
            Log.d(TAG, "[POTION] buying HP x$hpAmount")
            repeat(hpAmount) {
                NavigationVision.tap(HP_BUY_X, HP_BUY_Y)
                delay(BUY_FIRST_TAP_MS)
                NavigationVision.tap(HP_BUY_X, HP_BUY_Y)
                delay(BUY_SECOND_TAP_MS)
            }
        }
        if (mpAmount > 0) {
            Log.d(TAG, "[POTION] buying MP x$mpAmount")
            repeat(mpAmount) {
                NavigationVision.tap(MP_BUY_X, MP_BUY_Y)
                delay(BUY_FIRST_TAP_MS)
                NavigationVision.tap(MP_BUY_X, MP_BUY_Y)
                delay(BUY_SECOND_TAP_MS)
            }
        }
    }

    private suspend fun closeShop() {
        NavigationVision.tap(SHOP_CLOSE_X, SHOP_CLOSE_Y)
        delay(CLOSE_SHOP_WAIT_MS)
        val closed = NavigationVision.waitUntilAbsent(SHOP_OPEN, SHOP_THRESHOLD, 5000)
        Log.d(TAG, "[POTION] shop closed=$closed")
    }

    private fun shopSearchRegion(frame: Bitmap): Rect {
        val halfW = frame.width / 2
        return Rect(halfW, 0, frame.width, frame.height)
    }
}
