package com.example.muamaizingbot.bot.loop

import android.util.Log
import com.example.muamaizingbot.bot.BotDiagnosticJournal
import com.example.muamaizingbot.bot.bosses.BossHuntPhase
import com.example.muamaizingbot.bot.bosses.BossHuntState
import com.example.muamaizingbot.bot.bosses.FarmBossesLoop
import com.example.muamaizingbot.bot.combat.CombatFocusActions
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
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
import com.example.muamaizingbot.bot.maintenance.HudValidationGate
import com.example.muamaizingbot.bot.maintenance.MapCheckActions
import com.example.muamaizingbot.bot.maintenance.InventoryCheckActions
import com.example.muamaizingbot.bot.maintenance.InventoryRecycleActions
import com.example.muamaizingbot.bot.maintenance.PetActions
import com.example.muamaizingbot.bot.maintenance.PetCheckGate
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
import com.example.muamaizingbot.profile.isFarmMode
import com.example.muamaizingbot.profile.normalizedBotMode
import com.example.muamaizingbot.vision.coordinate.CoordinateTextParser
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
    /** A single plausible-but-wrong HUD read must not reopen the map after a confirmed spot. */
    private const val OFF_SPOT_CONFIRMATION_READS = 2

    private var consecutiveFarmSoftFails = 0
    private var consecutiveWrongMapSoftFails = 0
    /** Last confirmed on-spot from valid HUD coords (sticky across OCR misses). */
    private var lastSpotOk = false
    private var consecutiveCoordMisses = 0
    private var consecutiveValidOffSpotReads = 0

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
            consecutiveValidOffSpotReads = 0
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

        // While a UI action is in progress (shouldn't normally overlap this loop),
        // skip HUD probes that would false-miss under open panels. Death already ran.
        if (DisconnectDetector.isUiActionActive()) {
            Log.d(
                TAG,
                "[LOOP] skip hud_validations ui_action=${DisconnectDetector.uiActionReason()}",
            )
            BotDiagnosticJournal.record(
                TAG,
                "skip hud_validations ui_action=${DisconnectDetector.uiActionReason()}",
            )
            return IterationResult.OK
        }

        // Close leftover Gear/Store/map/inventory before elf/potion/inventory/pet probes.
        val hudClear = HudValidationGate.ensureClearForHudProbe()
        if (!hudClear) {
            Log.w(TAG, "[LOOP] skip hud_validations — blocking panel still open")
            BotDiagnosticJournal.record(TAG, "skip hud_validations panel_open")
            // Still allow mode branch / farming; do not treat as missing buff/potion.
        }

        // Potions + inventory: any time (farm, open-world elf, and Farm Bosses mid-hunt).
        if (hudClear && profile.enablePotionRecovery && PotionCheckActions.isAnyPotionEmpty()) {
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

        if (hudClear && InventoryCheckActions.isInventoryFull()) {
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

        // Pet: farm / giver / war keep interval checks. Farm Bosses skip while FIGHT
        // (prep runs in post-kill / post-revive general checks instead).
        val skipPetDuringBossFight =
            profile.isFarmBossesMode() && BossHuntState.phase == BossHuntPhase.FIGHT
        if (hudClear && !skipPetDuringBossFight && PetCheckGate.shouldCheck(profile)) {
            Log.d(
                TAG,
                "[LOOP] branch=pet_validate intervalMin=${profile.petCheckIntervalMinutes} " +
                    "want=${profile.petType.toStorage()}",
            )
            BotDiagnosticJournal.record(TAG, "branch=pet_validate")
            consecutiveFarmSoftFails = 0
            val pet = PetActions.validateIfEnabled(profile)
            PetCheckGate.noteCheckDone()
            Log.d(TAG, "[LOOP] pet_validate result=$pet want=${profile.petType.toStorage()}")
            if (profile.isFarmBossesMode() && !MapCheckActions.isInConfiguredMap()) {
                return navigateToBossCheckpoint("post-pet")
            }
            return IterationResult.OK
        }
        if (skipPetDuringBossFight && PetCheckGate.shouldCheck(profile)) {
            Log.d(TAG, "[LOOP] skip pet_validate during boss FIGHT (prep after kill/death)")
        }

        // Elf buff: any time (farm + Farm Bosses mid-hunt). Giver / War never seek.
        if (hudClear && ElfBuffSeekGate.shouldAttemptSeek(profile)) {
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
        // Exception: combat-focus chase — do not yank back while enemy focus is active.
        if (!isAtConfiguredFarmSpot()) {
            if (CombatFocusActions.shouldSuppressOffSpotRecovery(profile)) {
                Log.d(TAG, "[LOOP] branch=farming (off_spot suppressed — combat focus chase)")
                BotDiagnosticJournal.record(TAG, "branch=farming_chase")
                return handleFarmingCycle()
            }
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

        if (profile.enablePet) {
            when (val pet = PetActions.validateIfEnabled(profile)) {
                PetActions.CheckResult.MATCH ->
                    Log.d(TAG, "[STARTUP] pet ok type=${profile.petType.toStorage()}")
                PetActions.CheckResult.EQUIPPED ->
                    Log.d(
                        TAG,
                        "[STARTUP] pet equipped from inventory type=${profile.petType.toStorage()}",
                    )
                PetActions.CheckResult.PURCHASED ->
                    Log.d(
                        TAG,
                        "[STARTUP] pet purchased+equipped type=${profile.petType.toStorage()}",
                    )
                PetActions.CheckResult.NEED_PURCHASE ->
                    Log.w(
                        TAG,
                        "[STARTUP] pet need_purchase want=${profile.petType.toStorage()}",
                    )
                PetActions.CheckResult.BUY_FAILED ->
                    Log.w(
                        TAG,
                        "[STARTUP] pet buy_failed want=${profile.petType.toStorage()}",
                    )
                PetActions.CheckResult.EQUIP_FAILED ->
                    Log.w(
                        TAG,
                        "[STARTUP] pet equip_failed want=${profile.petType.toStorage()}",
                    )
                PetActions.CheckResult.OPEN_FAILED,
                PetActions.CheckResult.READ_FAILED,
                ->
                    Log.w(TAG, "[STARTUP] pet validate failed result=$pet")
                PetActions.CheckResult.SKIPPED -> Unit
            }
            PetCheckGate.noteCheckDone()
        } else {
            PetCheckGate.reset()
        }

        // Combat focus: set PK mode once at start (farm / farm_bosses). On-spot only confirms.
        if (profile.enableCombatFocus && (profile.isFarmMode() || profile.isFarmBossesMode())) {
            if (!CombatFocusActions.prevalidatePkModeAtStartup(profile)) {
                Log.w(TAG, "[STARTUP] combat focus PK prevalidate failed — on-spot will repair")
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
     * Truncated reads (161→61) while last confirmed on-spot → sticky (do not retap map).
     * Off-spot only with valid coords, not truncation-like, and dist > radius.
     */
    private suspend fun isAtConfiguredFarmSpot(): Boolean {
        val farmSpot = LocationRepository.farmSpot.value
        if (farmSpot == null) {
            Log.d(TAG, "[SPOT] no farm spot saved")
            lastSpotOk = false
            consecutiveValidOffSpotReads = 0
            return false
        }
        val targetX = farmSpot.coordX
        val targetY = farmSpot.coordY
        if (targetX == null || targetY == null) {
            Log.w(TAG, "[SPOT] farm spot missing HUD coords — cannot validate arrival")
            lastSpotOk = false
            consecutiveValidOffSpotReads = 0
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
        if (!onSpot &&
            lastSpotOk &&
            CoordinateTextParser.looksLikeTruncatedHudRead(
                current,
                targetX to targetY,
                tolerance = farmSpot.arrivalRadius,
            )
        ) {
            Log.d(
                TAG,
                "[SPOT] OCR trunc sticky current=(${current.first},${current.second}) " +
                    "target=($targetX,$targetY) dist=$dist r=${farmSpot.arrivalRadius}",
            )
            consecutiveValidOffSpotReads = 0
            return true
        }
        if (onSpot) {
            consecutiveValidOffSpotReads = 0
        } else if (lastSpotOk) {
            consecutiveValidOffSpotReads++
            if (!shouldTreatAsOffSpot(lastSpotOk, consecutiveValidOffSpotReads)) {
                Log.d(
                    TAG,
                    "[SPOT] off-spot pending #$consecutiveValidOffSpotReads/" +
                        "$OFF_SPOT_CONFIRMATION_READS current=(${current.first},${current.second}) " +
                        "target=($targetX,$targetY) dist=$dist r=${farmSpot.arrivalRadius}",
                )
                return true
            }
        }
        lastSpotOk = onSpot
        Log.d(
            TAG,
            "[SPOT] atSpot=$onSpot current=(${current.first},${current.second}) " +
                "target=($targetX,$targetY) dist=$dist r=${farmSpot.arrivalRadius}",
        )
        return onSpot
    }

    internal fun shouldTreatAsOffSpot(
        lastSpotWasConfirmed: Boolean,
        consecutiveValidOffSpotReads: Int,
    ): Boolean {
        return !lastSpotWasConfirmed ||
            consecutiveValidOffSpotReads >= OFF_SPOT_CONFIRMATION_READS
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
     * Shared general maintenance for Farm Bosses: potions, inventory, pet, then elf buff.
     * Used at startup, post-revive, and post-kill (preparation before the next hunt).
     * Potions / inventory / elf also run every loop tick before the farm-bosses branch;
     * pet is **not** probed mid-FIGHT — only here and via the normal interval outside FIGHT.
     * Return to checkpoint is the caller's job.
     */
    private suspend fun runFarmBossesGeneralChecks(
        profile: BotProfile,
        reason: String,
    ): IterationResult {
        Log.d(TAG, "[LOOP] farm_bosses general checks reason=$reason")
        BotDiagnosticJournal.record(TAG, "farm_bosses_general reason=$reason")

        if (DisconnectDetector.isUiActionActive()) {
            Log.d(
                TAG,
                "[LOOP] farm_bosses skip hud_validations ui_action=" +
                    DisconnectDetector.uiActionReason(),
            )
            return IterationResult.OK
        }

        val hudClear = HudValidationGate.ensureClearForHudProbe()
        if (!hudClear) {
            Log.w(TAG, "[LOOP] farm_bosses skip hud_validations — panel open reason=$reason")
            return IterationResult.OK
        }

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

        // Preparation pet check after fight / death (and startup general pass).
        // Force regardless of interval — this is the farm_bosses prep window.
        if (profile.enablePet) {
            Log.d(
                TAG,
                "[LOOP] farm_bosses pet_validate reason=$reason " +
                    "want=${profile.petType.toStorage()}",
            )
            val pet = PetActions.validateIfEnabled(profile)
            PetCheckGate.noteCheckDone()
            Log.d(
                TAG,
                "[LOOP] farm_bosses pet_validate result=$pet reason=$reason " +
                    "want=${profile.petType.toStorage()}",
            )
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
            consecutiveValidOffSpotReads = 0
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
            // Must NOT call navigateToBossCheckpoint again: that nests
            // recoveryOrError("boss-nav-failed-$reason") forever and grows the reason string.
            Log.e(
                TAG,
                "[LOOP] boss nav recovery exhausted reason=$reason → ERROR (auto-restart)",
            )
            return IterationResult.ERROR
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
