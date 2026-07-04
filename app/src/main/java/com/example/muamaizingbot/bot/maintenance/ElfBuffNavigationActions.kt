package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import kotlinx.coroutines.delay

object ElfBuffNavigationActions {

    private const val TAG = "ElfBuffNav"
    private const val BUFF_PICKUP_WAIT_MS = 5000L

    suspend fun goToElfBuffAndReturn(): Boolean {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[ELF] no active profile")
            return false
        }

        val elfLocation = LocationRepository.getElfBuff(profile.filename)
        if (elfLocation == null) {
            Log.w(TAG, "[ELF] no elf buff location configured")
            return false
        }

        if (DeathActions.isDead()) {
            Log.d(TAG, "[ELF] dead before elf route; reviving")
            if (!DeathActions.recoverIfDead()) {
                return false
            }
        }

        if (!goToElfBuff(elfLocation)) {
            Log.w(TAG, "[ELF] route to buff failed; recovery checkpoint")
            return BotRecoveryActions.recoverFromLostState("elf-route-failed")
        }

        if (!BotRecoveryActions.navigateToFarmWithRetry("post-elf-buff")) {
            Log.w(TAG, "[ELF] return to farm failed; recovery checkpoint")
            return BotRecoveryActions.recoverFromLostState("elf-return-failed")
        }

        Log.d(TAG, "[ELF] elf buff and return completed")
        return true
    }

    private suspend fun goToElfBuff(location: FarmLocation): Boolean {
        Log.d(
            TAG,
            "[ELF] going to buff map=${location.map} wire=${location.wire} " +
                "pixel=(${location.x},${location.y})"
        )

        if (!NavigationOrchestrator.goToVisualLocation(location)) {
            Log.w(TAG, "[ELF] failed to reach elf buff location")
            return false
        }

        delay(BUFF_PICKUP_WAIT_MS)
        Log.d(TAG, "[ELF] buff pickup wait done")
        return true
    }
}
