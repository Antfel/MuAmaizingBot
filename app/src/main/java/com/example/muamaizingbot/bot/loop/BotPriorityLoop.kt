package com.example.muamaizingbot.bot.loop

import android.util.Log
import com.example.muamaizingbot.bot.BotDiagnosticJournal
import com.example.muamaizingbot.bot.bosses.BossHuntPhase
import com.example.muamaizingbot.bot.bosses.BossHuntState
import com.example.muamaizingbot.bot.bosses.FarmBossesLoop
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
import com.example.muamaizingbot.bot.maintenance.ElfBuffWarTapGrid
import com.example.muamaizingbot.bot.maintenance.MapCheckActions
import com.example.muamaizingbot.bot.maintenance.InventoryCheckActions
import com.example.muamaizingbot.bot.maintenance.InventoryRecycleActions
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions
import com.example.muamaizingbot.bot.maintenance.PotionPurchaseActions
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.bot.recovery.BotRecoveryActions
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffGiverMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.profile.normalizedBotMode
import kotlin.math.abs
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
    /** Last confirmed on-spot from valid HUD coords (sticky across OCR misses). */
    private var lastSpotOk = false
    private var consecutiveCoordMisses = 0

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
            lastSpotOk = false
            consecutiveCoordMisses = 0
            if (!DeathActions.recoverIfDead()) {
                return IterationResult.ERROR
            }
            return when {
                profile.isElfBuffWarMode() -> navigateToWarPost("post-revive")
                profile.isFarmBossesMode() -> {
                    val checks = runFarmBossesGeneralChecks(profile, "post-revive")
                    if (checks != IterationResult.OK) {
                        return checks
                    }
                    navigateToBossCheckpoint("post-revive")
                }
                else -> navigateToFarm("post-revive")
            }
        }

        // Potions + inventory: any time (farm, open-world elf, and Farm Bosses mid-hunt).
        if (profile.enablePotionRecovery && PotionCheckActions.isAnyPotionEmpty()) {
            Log.d(TAG, "[LOOP] branch=empty_potions")
            BotDiagnosticJournal.record(TAG, "branch=empty_potions")
            consecutiveFarmSoftFails = 0
            if (!PotionPurchaseActions.handleEmptyPotions()) {
                return recoveryOrError("potion-failed")
            }
            // Teleport shop leaves Farm Bosses off-map; return to checkpoint.
            if (profile.isFarmBossesMode() && !MapCheckActions.isInConfiguredMap()) {
                return navigateToBossCheckpoint("post-potion")
            }
            return IterationResult.OK
        }

        if (InventoryCheckActions.isInventoryFull()) {
            Log.d(TAG, "[LOOP] branch=inventory_full")
            BotDiagnosticJournal.record(TAG, "branch=inventory_full")
            consecutiveFarmSoftFails = 0
            if (!InventoryRecycleActions.handleFullInventory()) {
                return recoveryOrError("recycle-failed")
            }
            if (profile.isFarmBossesMode() && !MapCheckActions.isInConfiguredMap()) {
                return navigateToBossCheckpoint("post-recycle")
            }
            return IterationResult.OK
        }

        // Elf buff: any time (farm + Farm Bosses mid-hunt). Giver / War never seek.
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
                    return when {
                        profile.isElfBuffWarMode() -> navigateToWarPost("post-revive")
                        profile.isFarmBossesMode() -> {
                            val checks = runFarmBossesGeneralChecks(profile, "post-revive")
                            if (checks != IterationResult.OK) {
                                checks
                            } else {
                                navigateToBossCheckpoint("post-revive")
                            }
                        }
                        else -> navigateToFarm("post-revive")
                    }
                }
                val elfResult = handleMissingElfBuff("loop")
                if (elfResult != IterationResult.OK) {
                    return elfResult
                }
                if (profile.isFarmBossesMode() && !MapCheckActions.isInConfiguredMap()) {
                    return navigateToBossCheckpoint("post-elf")
                }
                return IterationResult.OK
            }
        }

        // Farm Bosses hunt / post-kill (potions, inventory, elf already checked above).
        if (profile.isFarmBossesMode()) {
            return runFarmBossesIteration(profile)
        }

        if (!MapCheckActions.isInConfiguredMap()) {
            Log.d(TAG, "[LOOP] branch=wrong_map")
            BotDiagnosticJournal.record(TAG, "branch=wrong_map")
            consecutiveFarmSoftFails = 0
            // War never uses wrong_map (validation skipped); keep farm/giver/bosses path.
            return navigateToFarm("wrong_map", countAsWrongMapSoft = true)
        }

        // On configured map — clear city flap counter.
        consecutiveWrongMapSoftFails = 0

        if (profile.isElfBuffWarMode()) {
            Log.d(TAG, "[LOOP] branch=elf_buff_war")
            BotDiagnosticJournal.record(TAG, "branch=elf_buff_war")
            if (!ElfBuffSkillMapper.isReady()) {
                ElfBuffSkillMapper.calibrate()
            }
            return when (ElfBuffWarActions.tick(profile)) {
                ElfBuffWarActions.TickResult.DEAD -> {
                    Log.d(TAG, "[LOOP] war tick → death")
                    if (!DeathActions.recoverIfDead()) {
                        IterationResult.ERROR
                    } else {
                        navigateToWarPost("post-revive")
                    }
                }
                ElfBuffWarActions.TickResult.OK -> IterationResult.OK
            }
        }

        // Farm / giver: never Auto or farm cycle until HUD coords confirm farm spot.
        if (!isAtConfiguredFarmSpot()) {
            Log.d(TAG, "[LOOP] branch=off_spot")
            BotDiagnosticJournal.record(TAG, "branch=off_spot")
            consecutiveFarmSoftFails = 0
            return navigateToFarm("off_spot")
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

        if (InventoryCheckActions.isInventoryFull()) {
            Log.d(TAG, "[STARTUP] inventory_full → recycle")
            if (!InventoryRecycleActions.handleFullInventory()) {
                return recoveryOrError("startup-recycle-failed")
            }
        }

        // War / APEX: already inside event — capture HUD post only (no map teleport).
        if (profile.isElfBuffWarMode()) {
            Log.d(TAG, "[STARTUP] mode=elf_buff_war → capture war_post only")
            ElfBuffWarTapGrid.reset("startup")
            if (ElfBuffWarPostActions.captureWarPost() == null) {
                Log.w(TAG, "[STARTUP] war_post capture failed — will retry on death/loop")
            }
            if (!ElfBuffSkillMapper.calibrate()) {
                Log.w(TAG, "[STARTUP] war skill map incomplete — will retry on tick")
            }
            return IterationResult.OK
        }

        if (profile.isFarmBossesMode()) {
            Log.d(TAG, "[STARTUP] mode=farm_bosses → general checks then hunt")
            FarmBossesLoop.reset()
            if (profile.killBossesConfig.maps.isEmpty()) {
                Log.w(TAG, "[STARTUP] farm_bosses has no maps configured")
                return IterationResult.ERROR
            }
            val checks = runFarmBossesGeneralChecks(profile, "startup")
            if (checks != IterationResult.OK) {
                return checks
            }
            return navigateToBossCheckpoint("startup")
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

        if (MapCheckActions.isInConfiguredMap() && isAtConfiguredFarmSpot()) {
            Log.d(TAG, "[STARTUP] already on configured map + farm spot")
            return ensureAutoOnly("startup-on-spot")
        }

        return navigateToFarm("startup")
    }

    /**
     * On-spot only when HUD coords parse and dist ≤ [arrivalRadius].
     * OCR miss / parse fail → sticky [lastSpotOk] (do not reopen map on junk like `YS,95)`).
     * Off-spot only with valid coords and dist > radius.
     */
    private suspend fun isAtConfiguredFarmSpot(): Boolean {
        val farmSpot = LocationRepository.farmSpot.value
        if (farmSpot == null) {
            Log.d(TAG, "[SPOT] no farm spot saved")
            lastSpotOk = false
            return false
        }
        val targetX = farmSpot.coordX
        val targetY = farmSpot.coordY
        if (targetX == null || targetY == null) {
            Log.w(TAG, "[SPOT] farm spot missing HUD coords — cannot validate arrival")
            lastSpotOk = false
            return false
        }
        val mapDef = MapDefinitionRepository.getById(farmSpot.map)
        val current = NavigationWaitActions.readHudGameCoordinates(mapDef)
        if (current == null) {
            consecutiveCoordMisses++
            Log.d(
                TAG,
                "[SPOT] OCR miss #$consecutiveCoordMisses sticky=$lastSpotOk " +
                    "target=($targetX,$targetY) r=${farmSpot.arrivalRadius}",
            )
            return lastSpotOk
        }
        consecutiveCoordMisses = 0
        val dist = abs(current.first - targetX) + abs(current.second - targetY)
        val onSpot = dist <= farmSpot.arrivalRadius
        lastSpotOk = onSpot
        Log.d(
            TAG,
            "[SPOT] atSpot=$onSpot current=(${current.first},${current.second}) " +
                "target=($targetX,$targetY) dist=$dist r=${farmSpot.arrivalRadius}",
        )
        return onSpot
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

    /**
     * Shared general maintenance for Farm Bosses: potions, inventory, then elf buff.
     * Used at startup, post-revive, and post-kill.
     * Potions / inventory / elf also run every loop tick before the farm-bosses branch.
     * Return to checkpoint is the caller's job.
     */
    private suspend fun runFarmBossesGeneralChecks(
        profile: BotProfile,
        reason: String,
    ): IterationResult {
        Log.d(TAG, "[LOOP] farm_bosses general checks reason=$reason")
        BotDiagnosticJournal.record(TAG, "farm_bosses_general reason=$reason")

        if (profile.enablePotionRecovery && PotionCheckActions.isAnyPotionEmpty()) {
            Log.d(TAG, "[LOOP] farm_bosses potions empty reason=$reason")
            if (!PotionPurchaseActions.handleEmptyPotions()) {
                return recoveryOrError("boss-$reason-potion")
            }
        }

        if (InventoryCheckActions.isInventoryFull()) {
            Log.d(TAG, "[LOOP] farm_bosses inventory_full reason=$reason")
            if (!InventoryRecycleActions.handleFullInventory()) {
                return recoveryOrError("boss-$reason-recycle")
            }
        }

        if (ElfBuffSeekGate.shouldAttemptSeek(profile) && !ElfBuffCheckActions.hasElfBuff()) {
            Log.d(TAG, "[LOOP] farm_bosses elf buff missing reason=$reason")
            if (!ElfBuffNavigationActions.goToElfBuffAndReturn()) {
                return recoveryOrError("boss-$reason-elf")
            }
            if (!ElfBuffCheckActions.hasElfBuff()) {
                ElfBuffSeekGate.noteSeekFailed()
            }
        }

        return IterationResult.OK
    }

    private suspend fun runFarmBossesIteration(profile: BotProfile): IterationResult {
        if (BossHuntState.shouldRunGeneralMaintenance()) {
            Log.d(TAG, "[LOOP] branch=farm_bosses_post_kill")
            BotDiagnosticJournal.record(TAG, "branch=farm_bosses_post_kill")
            consecutiveFarmSoftFails = 0

            val checks = runFarmBossesGeneralChecks(profile, "post-kill")
            if (checks != IterationResult.OK) {
                return checks
            }

            if (!FarmBossesLoop.resumeAfterMaintenance(profile)) {
                return navigateToBossCheckpoint("post-kill-return")
            }
            consecutiveWrongMapSoftFails = 0
            return IterationResult.OK
        }

        Log.d(TAG, "[LOOP] branch=farm_bosses")
        BotDiagnosticJournal.record(TAG, "branch=farm_bosses")
        return handleFarmBossesCycle(profile)
    }

    private suspend fun handleFarmBossesCycle(profile: BotProfile): IterationResult {
        return when (FarmBossesLoop.tick(profile)) {
            FarmBossesLoop.CycleResult.OK -> {
                consecutiveFarmSoftFails = 0
                IterationResult.OK
            }
            FarmBossesLoop.CycleResult.DEAD -> {
                consecutiveFarmSoftFails = 0
                IterationResult.OK
            }
            FarmBossesLoop.CycleResult.NO_MAPS -> {
                Log.w(TAG, "[LOOP] farm_bosses no maps → ERROR")
                IterationResult.ERROR
            }
            FarmBossesLoop.CycleResult.NEED_MAINTENANCE -> {
                consecutiveFarmSoftFails = 0
                // Next iteration runs post-kill gate.
                IterationResult.OK
            }
            FarmBossesLoop.CycleResult.SOFT_FAIL -> {
                consecutiveFarmSoftFails++
                Log.d(
                    TAG,
                    "[LOOP] farm_bosses soft-fail $consecutiveFarmSoftFails/$FARM_SOFT_FAIL_TOLERANCE",
                )
                if (consecutiveFarmSoftFails >= FARM_SOFT_FAIL_TOLERANCE) {
                    consecutiveFarmSoftFails = 0
                    return navigateToBossCheckpoint("boss-soft-fail-limit")
                }
                IterationResult.OK
            }
        }
    }

    private suspend fun navigateToBossCheckpoint(
        reason: String,
        countAsWrongMapSoft: Boolean = false,
    ): IterationResult {
        val profile = ProfileRepository.currentProfile.value
        val cp = profile?.let { FarmBossesLoop.currentCheckpointOrCursor(it) }
        if (cp == null) {
            Log.w(TAG, "[LOOP] farm_bosses nav missing checkpoint reason=$reason")
            return IterationResult.ERROR
        }
        val mapDef = MapDefinitionRepository.getById(cp.mapId)
        if (mapDef == null) {
            Log.w(TAG, "[LOOP] farm_bosses map missing id=${cp.mapId}")
            return IterationResult.ERROR
        }
        Log.d(TAG, "[LOOP] boss checkpoint nav reason=$reason map=${cp.mapId} W${cp.wireId}")
        FarmBossesLoop.clearArrivalState()
        BossHuntState.phase = BossHuntPhase.ENSURE_LOCATION
        if (BotRecoveryActions.isNavCooldownActive()) {
            val waitMs = BotRecoveryActions.navCooldownRemainingMs()
                .coerceAtMost(NAV_COOLDOWN_SOFT_WAIT_MS)
                .coerceAtLeast(500L)
            delay(waitMs)
            return noteWrongMapSoftOrOk(countAsWrongMapSoft, reason)
        }
        if (NavigationOrchestrator.navigateToMapAndWire(mapDef, cp.wireId, farmSpot = null)) {
            consecutiveWrongMapSoftFails = 0
            BossHuntState.phase = BossHuntPhase.HUNT
            return IterationResult.OK
        }
        if (BotRecoveryActions.isNavCooldownActive()) {
            return noteWrongMapSoftOrOk(countAsWrongMapSoft, reason)
        }
        return recoveryOrError("boss-nav-failed-$reason")
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
            // Nav already called ensureAutoMode when ensureAuto=true — avoid a second OCR/tap pass.
            lastSpotOk = true
            consecutiveCoordMisses = 0
            return IterationResult.OK
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
        if (profile?.isFarmBossesMode() == true) {
            return navigateToBossCheckpoint("recovery-$reason")
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
