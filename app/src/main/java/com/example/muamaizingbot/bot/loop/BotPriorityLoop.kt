package com.example.muamaizingbot.bot.loop

import android.util.Log
import com.example.muamaizingbot.bot.BotDiagnosticJournal
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.farming.FarmingLoop
import com.example.muamaizingbot.bot.maintenance.ElfBuffCastActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffCheckActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffNavigationActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffSeekGate
import com.example.muamaizingbot.bot.maintenance.ElfBuffSkillMapper
import com.example.muamaizingbot.bot.maintenance.ElfBuffTargetingActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffWarActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffWarPostActions
import com.example.muamaizingbot.bot.maintenance.MapCheckActions
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions
import com.example.muamaizingbot.bot.maintenance.PotionPurchaseActions
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.normalizedBotMode
import kotlinx.coroutines.delay

object BotPriorityLoop {

    private const val TAG = "BotLoop"
    /** Farm soft-fails tolerated before any recovery (PC-style: keep farming on spot). */
    private const val FARM_SOFT_FAIL_TOLERANCE = 8
    private const val NAV_COOLDOWN_SOFT_WAIT_MS = 2_000L
    /**
     * Repeated wrong_map / city soft-OK flaps (open/close map) before hard ERROR
     * so [BotAutoRestart] can re-run startup navigation.
     */
    private const val WRONG_MAP_SOFT_LIMIT = 8

    private var consecutiveFarmSoftFails = 0
    private var consecutiveWrongMapSoftFails = 0

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
            BotDiagnosticJournal.record(TAG, "branch=death_recovery")
            consecutiveFarmSoftFails = 0
            consecutiveWrongMapSoftFails = 0
            if (!DeathActions.recoverIfDead()) {
                return IterationResult.ERROR
            }
            return if (profile.isElfBuffWarMode()) {
                navigateToWarPost("post-revive")
            } else {
                navigateToFarm("post-revive")
            }
        }

        if (profile.enablePotionRecovery && PotionCheckActions.isAnyPotionEmpty()) {
            Log.d(TAG, "[LOOP] branch=empty_potions")
            BotDiagnosticJournal.record(TAG, "branch=empty_potions")
            consecutiveFarmSoftFails = 0
            return if (PotionPurchaseActions.handleEmptyPotions()) {
                IterationResult.OK
            } else {
                recoveryOrError("potion-failed")
            }
        }

        // Farm mode only: seek NPC elf. Giver / War stay on post (no seek).
        if (ElfBuffSeekGate.shouldAttemptSeek(profile)) {
            if (!ElfBuffCheckActions.hasElfBuff()) {
                // Death screen can hide the buff icon; prefer revive over seeking.
                if (DeathActions.isDead()) {
                    Log.d(TAG, "[LOOP] branch=death_recovery (masked as missing buff)")
                    BotDiagnosticJournal.record(TAG, "branch=death_recovery (masked)")
                    consecutiveFarmSoftFails = 0
                    consecutiveWrongMapSoftFails = 0
                    if (!DeathActions.recoverIfDead()) {
                        return IterationResult.ERROR
                    }
                    return if (profile.isElfBuffWarMode()) {
                        navigateToWarPost("post-revive")
                    } else {
                        navigateToFarm("post-revive")
                    }
                }
                return handleMissingElfBuff("loop")
            }
        }

        if (!MapCheckActions.isInConfiguredMap()) {
            Log.d(TAG, "[LOOP] branch=wrong_map")
            BotDiagnosticJournal.record(TAG, "branch=wrong_map")
            consecutiveFarmSoftFails = 0
            return if (profile.isElfBuffWarMode()) {
                navigateToWarPost("wrong_map", countAsWrongMapSoft = true)
            } else {
                navigateToFarm("wrong_map", countAsWrongMapSoft = true)
            }
        }

        // On configured map — clear city flap counter.
        consecutiveWrongMapSoftFails = 0

        if (profile.isElfBuffWarMode()) {
            Log.d(TAG, "[LOOP] branch=elf_buff_war")
            BotDiagnosticJournal.record(TAG, "branch=elf_buff_war")
            if (!ElfBuffSkillMapper.isReady()) {
                ElfBuffSkillMapper.calibrate()
            }
            ElfBuffWarActions.tick(profile)
            return IterationResult.OK
        }

        val farmBranch = if (profile.isElfBuffGiverMode()) "elf_giver_hold" else "farming"
        Log.d(TAG, "[LOOP] branch=$farmBranch")
        BotDiagnosticJournal.record(TAG, "branch=$farmBranch")
        val farmResult = handleFarmingCycle()
        if (farmResult == IterationResult.OK && profile.isElfBuffGiverMode()) {
            if (!ElfBuffSkillMapper.isReady()) {
                ElfBuffSkillMapper.calibrate()
            }
            ElfBuffCastActions.maybeCast(profile)
        }
        return farmResult
    }

    suspend fun runStartup(): IterationResult {
        consecutiveFarmSoftFails = 0
        consecutiveWrongMapSoftFails = 0

        val profile = ProfileRepository.currentProfile.value
        if (profile == null) {
            Log.w(TAG, "[STARTUP] no active profile")
            return IterationResult.ERROR
        }

        Log.d(
            TAG,
            "[STARTUP] profile=${profile.displayName} mode=${profile.normalizedBotMode()} " +
                "elfBuff=${profile.enableElfBuff} potions=${profile.enablePotionRecovery}",
        )

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
        }

        // War / APEX: ensure Divine map, capture HUD post, calibrate skills — no PK, no Auto.
        if (profile.isElfBuffWarMode()) {
            Log.d(TAG, "[STARTUP] mode=elf_buff_war → Divine post")
            if (!MapCheckActions.isInConfiguredMap()) {
                val nav = navigateToFarm("startup-war", skipAuto = true)
                if (nav != IterationResult.OK) {
                    return nav
                }
            }
            if (ElfBuffWarPostActions.captureWarPost() == null) {
                Log.w(TAG, "[STARTUP] war_post capture failed — will retry on death/loop")
            }
            if (!ElfBuffSkillMapper.calibrate()) {
                Log.w(TAG, "[STARTUP] war skill map incomplete — will retry on tick")
            }
            return IterationResult.OK
        }

        // Elf buff giver: hold farm spot, map skills once, force PK All, then cast loop.
        if (profile.isElfBuffGiverMode()) {
            Log.d(TAG, "[STARTUP] mode=elf_buff_giver → static post")
            val nav = navigateToFarm("startup-elf-giver")
            if (nav == IterationResult.OK) {
                if (!ElfBuffSkillMapper.calibrate()) {
                    Log.w(TAG, "[STARTUP] skill map incomplete — will retry on cast")
                }
                if (!ElfBuffTargetingActions.ensurePkModeAll()) {
                    Log.w(TAG, "[STARTUP] ensure PK All failed — will retry on cast")
                }
            }
            return nav
        }

        if (ElfBuffSeekGate.shouldAttemptSeek(profile)) {
            if (!ElfBuffCheckActions.hasElfBuff()) {
                Log.d(TAG, "[STARTUP] elf buff missing before navigation")
                val result = handleMissingElfBuff("startup")
                if (result == IterationResult.ERROR) {
                    return result
                }
                return ensureAutoOnly("startup-after-elf")
            }
        } else if (!ProfileRepository.shouldSeekElfBuff(profile)) {
            Log.d(TAG, "[STARTUP] elf buff skipped (disabled, post mode, or not configured)")
        } else {
            Log.d(TAG, "[STARTUP] elf buff skipped (seek cooldown)")
        }

        if (MapCheckActions.isInConfiguredMap()) {
            Log.d(TAG, "[STARTUP] already on configured map")
            return ensureAutoOnly("startup-on-map")
        }

        return navigateToFarm("startup")
    }

    private suspend fun handleMissingElfBuff(reason: String): IterationResult {
        Log.d(TAG, "[LOOP] branch=elf_buff reason=$reason")
        consecutiveFarmSoftFails = 0
        if (!ElfBuffNavigationActions.goToElfBuffAndReturn()) {
            return recoveryOrError("elf-failed-$reason")
        }
        if (!ElfBuffCheckActions.hasElfBuff()) {
            ElfBuffSeekGate.noteSeekFailed()
        }
        return IterationResult.OK
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

    private suspend fun navigateToWarPost(
        reason: String,
        countAsWrongMapSoft: Boolean = false,
    ): IterationResult {
        Log.d(TAG, "[LOOP] war_post nav reason=$reason")
        if (BotRecoveryActions.isNavCooldownActive()) {
            val waitMs = BotRecoveryActions.navCooldownRemainingMs()
                .coerceAtMost(NAV_COOLDOWN_SOFT_WAIT_MS)
                .coerceAtLeast(500L)
            Log.w(TAG, "[LOOP] nav cooldown soft-wait ${waitMs}ms reason=$reason")
            delay(waitMs)
            return noteWrongMapSoftOrOk(countAsWrongMapSoft, reason)
        }
        if (ElfBuffWarPostActions.navigateToWarPost(reason)) {
            consecutiveWrongMapSoftFails = 0
            return IterationResult.OK
        }
        if (BotRecoveryActions.isNavCooldownActive()) {
            Log.w(TAG, "[LOOP] war_post nav failed → cooldown; soft OK reason=$reason")
            return noteWrongMapSoftOrOk(countAsWrongMapSoft, reason)
        }
        return recoveryOrError("war-post-failed-$reason")
    }

    private suspend fun navigateToFarm(
        reason: String,
        countAsWrongMapSoft: Boolean = false,
        skipAuto: Boolean = false,
    ): IterationResult {
        Log.d(TAG, "[LOOP] navigating reason=$reason")
        if (BotRecoveryActions.isNavCooldownActive()) {
            val waitMs = BotRecoveryActions.navCooldownRemainingMs()
                .coerceAtMost(NAV_COOLDOWN_SOFT_WAIT_MS)
                .coerceAtLeast(500L)
            Log.w(TAG, "[LOOP] nav cooldown soft-wait ${waitMs}ms reason=$reason")
            delay(waitMs)
            return noteWrongMapSoftOrOk(countAsWrongMapSoft, reason)
        }
        if (BotRecoveryActions.navigateToFarmWithRetry(reason, ensureAuto = !skipAuto)) {
            consecutiveWrongMapSoftFails = 0
            return if (skipAuto) {
                IterationResult.OK
            } else {
                ensureAutoOnly(reason)
            }
        }
        if (BotRecoveryActions.isNavCooldownActive()) {
            Log.w(TAG, "[LOOP] navigate failed → cooldown; soft OK reason=$reason")
            return noteWrongMapSoftOrOk(countAsWrongMapSoft, reason)
        }
        return recoveryOrError("nav-failed-$reason")
    }

    /**
     * Soft-OK while on nav cooldown avoids Worker kill, but city map open/close can flap forever.
     * After [WRONG_MAP_SOFT_LIMIT] consecutive wrong_map soft fails → ERROR → BotAutoRestart.
     */
    private fun noteWrongMapSoftOrOk(countAsWrongMapSoft: Boolean, reason: String): IterationResult {
        if (!countAsWrongMapSoft) {
            return IterationResult.OK
        }
        consecutiveWrongMapSoftFails++
        Log.w(
            TAG,
            "[LOOP] wrong_map soft-fail $consecutiveWrongMapSoftFails/$WRONG_MAP_SOFT_LIMIT " +
                "reason=$reason",
        )
        if (consecutiveWrongMapSoftFails >= WRONG_MAP_SOFT_LIMIT) {
            consecutiveWrongMapSoftFails = 0
            Log.e(TAG, "[LOOP] wrong_map soft-fail limit → ERROR (auto-restart)")
            return IterationResult.ERROR
        }
        return IterationResult.OK
    }

    private suspend fun recoveryOrError(reason: String): IterationResult {
        Log.w(TAG, "[LOOP] attempting recovery checkpoint reason=$reason")
        val profile = ProfileRepository.currentProfile.value
        if (profile?.isElfBuffWarMode() == true) {
            return if (ElfBuffWarPostActions.navigateToWarPost("recovery-$reason")) {
                IterationResult.OK
            } else if (BotRecoveryActions.isNavCooldownActive()) {
                IterationResult.OK
            } else {
                IterationResult.ERROR
            }
        }
        return if (BotRecoveryActions.recoverFromLostState(reason)) {
            IterationResult.OK
        } else if (BotRecoveryActions.isNavCooldownActive()) {
            Log.w(TAG, "[LOOP] recovery deferred (nav cooldown); soft OK reason=$reason")
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
