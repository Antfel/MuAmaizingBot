package com.example.muamaizingbot.bot.maintenance

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions.isHpPotionEmpty
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions.isManaPotionEmpty
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

object PotionPurchaseActions {

    private const val TAG = "PotionPurchase"

    private const val HP_OUT = PotionCheckActions.HP_OUT
    private const val MP_OUT = PotionCheckActions.MP_OUT
    private const val POTION_THRESHOLD = PotionCheckActions.POTION_THRESHOLD
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
        DisconnectDetector.beginUiAction("potion-shop")
        try {
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

            delay(BotTiming.ms(TAP_SLOT_WAIT_MS, BotTimingCategory.POST_TAP))
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
        } finally {
            DisconnectDetector.endUiAction("potion-shop")
        }
    }

    private enum class PotionEntry {
        TELEPORT_POPUP,
        SHOP_OPEN,
    }

    private suspend fun finishPotionRecovery(needsNavigation: Boolean): Boolean {
        delay(BotTiming.ms(POST_SHOP_SETTLE_MS, BotTimingCategory.FIXED_SETTLE))
        NavigationOrchestrator.cleanGameUi()

        if (needsNavigation) {
            val profile = ProfileRepository.currentProfile.value
            if (profile?.isFarmBossesMode() == true) {
                // Caller resumes via FarmBossesLoop checkpoint (startup / post-kill / post-revive).
                Log.d(TAG, "[POTION] teleport purchase; farm_bosses return deferred to checkpoint")
            } else {
                Log.d(TAG, "[POTION] teleport purchase; navigating back to farm")
                if (!BotRecoveryActions.navigateToFarmWithRetry("post-potion-teleport")) {
                    return BotRecoveryActions.recoverFromLostState("post-potion-nav-failed")
                }
            }
        } else {
            Log.d(TAG, "[POTION] direct shop purchase; no teleport (town or already near shop)")
        }

        // Auto only if we were already farming (map+spot). Startup / city shop must not
        // toggle Auto in town — later startup nav / farm loop enables it on arrival.
        if (ProfileRepository.currentProfile.value?.isFarmBossesMode() == true) {
            Log.d(TAG, "[POTION] skip ensureAutoMode (farm_bosses)")
        } else if (!isAlreadyFarmingOnSpot()) {
            Log.d(TAG, "[POTION] skip ensureAutoMode (not at farm spot)")
        } else if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[POTION] ensureAutoMode failed; farm loop will retry")
        }

        Log.d(TAG, "[POTION] recovery completed")
        return true
    }

    /** True when on configured farm map and within spot radius (was farming). */
    private suspend fun isAlreadyFarmingOnSpot(): Boolean {
        if (!MapCheckActions.isInConfiguredMap()) {
            return false
        }
        val farmSpot = LocationRepository.farmSpot.value ?: return false
        if (farmSpot.coordX == null || farmSpot.coordY == null) {
            return false
        }
        val mapDef = MapDefinitionRepository.getById(farmSpot.map)
        return NavigationWaitActions.isAtFarmSpot(farmSpot, mapDef)
    }

    private suspend fun waitForPurchasedPotions(hpWasEmpty: Boolean, mpWasEmpty: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + BotTiming.ms(
            REFILL_TIMEOUT_MS,
            BotTimingCategory.SCREEN_LOAD,
        )
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
        val roi = PotionCheckActions.potionSlotsRoi()
        return when {
            hpEmpty -> {
                Log.d(TAG, "[POTION] tapping empty HP slot roi=$roi")
                NavigationVision.tapTemplate(HP_OUT, POTION_THRESHOLD, roi)
            }
            mpEmpty -> {
                Log.d(TAG, "[POTION] tapping empty MP slot roi=$roi")
                NavigationVision.tapTemplate(MP_OUT, POTION_THRESHOLD, roi)
            }
            else -> false
        }
    }

    private suspend fun waitForPotionEntryResult(): PotionEntry? {
        val deadline = System.currentTimeMillis() + BotTiming.ms(
            ENTRY_TIMEOUT_MS,
            BotTimingCategory.SCREEN_LOAD,
        )
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
        delay(BotTiming.ms(TELEPORT_ACCEPT_WAIT_MS, BotTimingCategory.FIXED_SETTLE))
        return true
    }

    private suspend fun waitForShopOpen(): Boolean {
        val deadline = System.currentTimeMillis() + BotTiming.ms(
            SHOP_OPEN_TIMEOUT_MS,
            BotTimingCategory.SCREEN_LOAD,
        )
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
        delay(BotTiming.ms(CLOSE_SHOP_WAIT_MS, BotTimingCategory.POST_TAP))
        val closed = NavigationVision.waitUntilAbsent(
            SHOP_OPEN,
            SHOP_THRESHOLD,
            BotTiming.ms(5000L, BotTimingCategory.SCREEN_LOAD),
        )
        Log.d(TAG, "[POTION] shop closed=$closed")
    }

    private fun shopSearchRegion(frame: Bitmap): Rect {
        val halfW = frame.width / 2
        return Rect(halfW, 0, frame.width, frame.height)
    }
}
