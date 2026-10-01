package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isFarmBossesMode
import kotlinx.coroutines.delay

object ElfBuffNavigationActions {

    private const val TAG = "ElfBuffNav"
    /** Stand at elf until both buffs appear, or this timeout (not a blind short delay). */
    private const val BUFF_PICKUP_TIMEOUT_MS = 30_000L
    private const val BUFF_PICKUP_POLL_MS = 1_500L

    /** Go to elf, wait pickup, do not return to farm (Devil Square prep). */
    suspend fun goToElfBuffWithoutReturn(): Boolean {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[ELF] no active profile")
            return false
        }
        val elfLocation = LocationRepository.getElfBuff(profile.filename)
        if (elfLocation == null) {
            Log.w(TAG, "[ELF] no elf buff location — skip renew")
            return true
        }
        if (DeathActions.isDead()) {
            if (!DeathActions.recoverIfDead()) return false
        }
        if (!navigateToElf(elfLocation)) return false
        return waitForBuffPickup()
    }

    suspend fun goToElfBuffAndReturn(): Boolean {
        DisconnectDetector.beginUiAction("elf-buff-nav")
        try {
            val profile = ProfileRepository.currentProfile.value
            if (profile == null) {
                Log.w(TAG, "[ELF] no active profile")
                return false
            }

            if (!ProfileRepository.shouldSeekElfBuff(profile)) {
                Log.d(TAG, "[ELF] route skipped enableElfBuff=${profile.enableElfBuff}")
                return true
            }

            val elfLocation = LocationRepository.getElfBuff(profile.filename)
            if (elfLocation == null) {
                Log.w(TAG, "[ELF] no elf buff location configured")
                return false
            }

            // Death UI often hides the buff icon → false "missing buff". Revive and let the
            // mode loop return to farm spot / boss checkpoint on the next iteration.
            if (DeathActions.isDead()) {
                Log.d(TAG, "[ELF] dead before elf route; revive (defer buff)")
                if (!DeathActions.recoverIfDead()) {
                    return false
                }
                if (profile.isFarmBossesMode()) {
                    Log.d(TAG, "[ELF] farm_bosses post-revive; checkpoint return deferred")
                    return true
                }
                return BotRecoveryActions.navigateToFarmWithRetry("post-revive-defer-elf") ||
                    BotRecoveryActions.recoverFromLostState("post-revive-defer-elf")
            }

            if (!navigateToElf(elfLocation)) {
                // Mode-aware: farm → farm spot; farm_bosses → boss checkpoint; war → war post.
                Log.w(TAG, "[ELF] route to buff failed; mode-aware recovery checkpoint")
                BotRecoveryActions.recoverFromLostState("elf-route-failed")
                return false
            }

            val gotBuff = waitForBuffPickup()
            if (!gotBuff) {
                // Reached elf but no cast — do not loop as a nav ERROR; seek gate counts the miss.
                Log.w(TAG, "[ELF] no buff after wait — leaving elf spot")
                if (profile.isFarmBossesMode()) {
                    Log.d(TAG, "[ELF] farm_bosses post-miss; checkpoint return deferred")
                    return true
                }
                if (!BotRecoveryActions.navigateToFarmWithRetry("post-elf-buff-miss")) {
                    Log.w(TAG, "[ELF] return to farm after miss failed; recovery checkpoint")
                    return BotRecoveryActions.recoverFromLostState("elf-return-failed")
                }
                return true
            }

            // Farm Bosses: post-kill resumes via FarmBossesLoop.resumeAfterMaintenance.
            if (profile.isFarmBossesMode()) {
                Log.d(TAG, "[ELF] buff pickup done; farm_bosses return deferred to checkpoint")
                return true
            }

            if (!BotRecoveryActions.navigateToFarmWithRetry("post-elf-buff")) {
                Log.w(TAG, "[ELF] return to farm failed; recovery checkpoint")
                return BotRecoveryActions.recoverFromLostState("elf-return-failed")
            }

            Log.d(TAG, "[ELF] elf buff and return completed")
            return true
        } finally {
            DisconnectDetector.endUiAction("elf-buff-nav")
        }
    }

    private suspend fun navigateToElf(location: FarmLocation): Boolean {
        Log.d(
            TAG,
            "[ELF] going to buff map=${location.map} wire=${location.wire} " +
                "pixel=(${location.x},${location.y})",
        )
        if (!NavigationOrchestrator.goToVisualLocation(location)) {
            Log.w(TAG, "[ELF] failed to reach elf buff location")
            return false
        }
        return true
    }

    /**
     * Stay on the elf spot up to [BUFF_PICKUP_TIMEOUT_MS], polling icons.
     * Leaves early once both buffs are present. Returns false if still missing
     * after the window so callers do not treat it as a successful renew.
     */
    private suspend fun waitForBuffPickup(): Boolean {
        val deadline = System.currentTimeMillis() + BUFF_PICKUP_TIMEOUT_MS
        Log.d(TAG, "[ELF] buff pickup wait up to ${BUFF_PICKUP_TIMEOUT_MS}ms")
        while (true) {
            if (ElfBuffCheckActions.hasElfBuff()) {
                val left = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                Log.d(TAG, "[ELF] buff pickup confirmed (~${left}ms early)")
                return true
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) {
                Log.w(
                    TAG,
                    "[ELF] buff pickup wait done — still missing after ${BUFF_PICKUP_TIMEOUT_MS}ms",
                )
                return false
            }
            delay(minOf(BUFF_PICKUP_POLL_MS, remaining))
        }
    }
}
