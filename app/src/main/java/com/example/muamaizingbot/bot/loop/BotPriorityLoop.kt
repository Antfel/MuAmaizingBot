package com.example.muamaizingbot.bot.loop

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.farming.FarmingLoop
import com.example.muamaizingbot.bot.maintenance.ElfBuffCheckActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffNavigationActions
import com.example.muamaizingbot.bot.maintenance.MapCheckActions
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions
import com.example.muamaizingbot.bot.maintenance.PotionPurchaseActions
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository

object BotPriorityLoop {

    private const val TAG = "BotLoop"
    /** Farm soft-fails tolerated before any recovery (PC-style: keep farming on spot). */
    private const val FARM_SOFT_FAIL_TOLERANCE = 8

    private var consecutiveFarmSoftFails = 0

    enum class IterationResult {
        OK,
        ERROR,
    }

    suspend fun runIteration(): IterationResult {
        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[LOOP] no active profile")
            return IterationResult.ERROR
        }

        if (DeathActions.isDead()) {
            Log.d(TAG, "[LOOP] branch=death_recovery")
            consecutiveFarmSoftFails = 0
            if (!DeathActions.recoverIfDead()) {
                return IterationResult.ERROR
            }
            return navigateToFarm("post-revive")
        }

        if (profile.enablePotionRecovery && PotionCheckActions.isAnyPotionEmpty()) {
            Log.d(TAG, "[LOOP] branch=empty_potions")
            consecutiveFarmSoftFails = 0
            return if (PotionPurchaseActions.handleEmptyPotions()) {
                IterationResult.OK
            } else {
                recoveryOrError("potion-failed")
            }
        }

        if (profile.enableElfBuff) {
            val elfConfigured = LocationRepository.getElfBuff(profile.filename) != null
            if (elfConfigured && !ElfBuffCheckActions.hasElfBuff()) {
                Log.d(TAG, "[LOOP] branch=elf_buff")
                consecutiveFarmSoftFails = 0
                return if (ElfBuffNavigationActions.goToElfBuffAndReturn()) {
                    IterationResult.OK
                } else {
                    recoveryOrError("elf-failed")
                }
            }
        }

        if (!MapCheckActions.isInConfiguredMap()) {
            Log.d(TAG, "[LOOP] branch=wrong_map")
            consecutiveFarmSoftFails = 0
            return navigateToFarm("wrong_map")
        }

        Log.d(TAG, "[LOOP] branch=farming")
        return handleFarmingCycle()
    }

    suspend fun runStartup(): IterationResult {
        consecutiveFarmSoftFails = 0

        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[STARTUP] no active profile")
            return IterationResult.ERROR
        }

        if (DeathActions.isDead()) {
            Log.d(TAG, "[STARTUP] dead before navigation")
            if (!DeathActions.recoverIfDead()) {
                return IterationResult.ERROR
            }
        }

        if (profile.enablePotionRecovery && PotionCheckActions.isAnyPotionEmpty()) {
            Log.d(TAG, "[STARTUP] empty potions before navigation")
            if (!PotionPurchaseActions.handleEmptyPotions()) {
                return recoveryOrError("startup-potion-failed")
            }
            if (MapCheckActions.isInConfiguredMap()) {
                return ensureAutoOnly("startup-after-potions")
            }
        }

        if (profile.enableElfBuff) {
            val elfConfigured = LocationRepository.getElfBuff(profile.filename) != null
            if (elfConfigured && !ElfBuffCheckActions.hasElfBuff()) {
                Log.d(TAG, "[STARTUP] elf buff missing before navigation")
                if (!ElfBuffNavigationActions.goToElfBuffAndReturn()) {
                    return recoveryOrError("startup-elf-failed")
                }
                return ensureAutoOnly("startup-after-elf")
            }
        }

        if (MapCheckActions.isInConfiguredMap()) {
            Log.d(TAG, "[STARTUP] already on configured map")
            return ensureAutoOnly("startup-on-map")
        }

        return navigateToFarm("startup")
    }

    private suspend fun handleFarmingCycle(): IterationResult {
        return when (FarmingLoop.runCycle()) {
            FarmingLoop.CycleResult.OK -> {
                consecutiveFarmSoftFails = 0
                IterationResult.OK
            }
            FarmingLoop.CycleResult.DEAD -> {
                consecutiveFarmSoftFails = 0
                IterationResult.OK
            }
            FarmingLoop.CycleResult.SOFT_FAIL -> {
                consecutiveFarmSoftFails++
                Log.d(
                    TAG,
                    "[LOOP] farm soft-fail $consecutiveFarmSoftFails/$FARM_SOFT_FAIL_TOLERANCE " +
                        "(staying on spot)"
                )
                if (consecutiveFarmSoftFails >= FARM_SOFT_FAIL_TOLERANCE) {
                    consecutiveFarmSoftFails = 0
                    Log.w(TAG, "[LOOP] farm soft-fail limit; light on-spot recovery")
                    BotRecoveryActions.recoverOnSpot("farm-soft-fail-limit")
                }
                IterationResult.OK
            }
        }
    }

    private suspend fun navigateToFarm(reason: String): IterationResult {
        Log.d(TAG, "[LOOP] navigating reason=$reason")
        if (BotRecoveryActions.navigateToFarmWithRetry(reason)) {
            return ensureAutoOnly(reason)
        }
        return recoveryOrError("nav-failed-$reason")
    }

    private suspend fun recoveryOrError(reason: String): IterationResult {
        Log.w(TAG, "[LOOP] attempting recovery checkpoint reason=$reason")
        return if (BotRecoveryActions.recoverFromLostState(reason)) {
            IterationResult.OK
        } else {
            IterationResult.ERROR
        }
    }

    private suspend fun ensureAutoOnly(reason: String): IterationResult {
        if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[LOOP] ensureAutoMode failed reason=$reason")
        }
        return IterationResult.OK
    }
}
