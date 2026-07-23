package com.example.muamaizingbot.bot.bosses

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Maps-only Farm Bosses: teleport → wire cycle → open map → boss icon → Focus+Auto → post-kill.
 */
object FarmBossesLoop {

    private const val TAG = "FarmBosses"
    private const val INVENTORY_OPEN = "templates/mu/ui/inventory_open.png"
    private const val FIGHT_POLL_MS = 2_000L
    private const val POST_FOCUS_LOST_WAIT_MS = 5_000L
    private const val FOCUS_FAIL_BEFORE_REHUNT = 4

    @Volatile
    private var consecutiveFocusFails = 0

    enum class CycleResult {
        OK,
        SOFT_FAIL,
        DEAD,
        NO_MAPS,
        /** Caller should run potions/elf then [resumeAfterMaintenance]. */
        NEED_MAINTENANCE,
    }

    fun reset() {
        BossHuntState.reset()
        consecutiveFocusFails = 0
    }

    fun clearArrivalState() {
        BossHuntState.fightStartedAtMs = 0L
        consecutiveFocusFails = 0
        if (BossHuntState.phase == BossHuntPhase.FIGHT) {
            BossHuntState.phase = BossHuntPhase.HUNT
        }
    }

    fun currentMapId(profile: BotProfile): String? {
        val maps = profile.killBossesConfig.maps
        if (maps.isEmpty()) return null
        val idx = BossHuntState.mapIndex.floorMod(maps.size)
        BossHuntState.mapIndex = idx
        return maps[idx]
    }

    fun currentCheckpointOrCursor(profile: BotProfile): BossHuntCheckpoint? {
        BossHuntState.checkpoint?.let { return it }
        val mapId = currentMapId(profile) ?: return null
        return BossHuntCheckpoint(mapId, BossHuntState.wireId)
    }

    suspend fun tick(profile: BotProfile): CycleResult {
        val maps = profile.killBossesConfig.maps
        if (maps.isEmpty()) {
            Log.w(TAG, "[BOSS] no maps configured")
            return CycleResult.NO_MAPS
        }
        if (BossHuntState.mapIndex !in maps.indices) {
            BossHuntState.mapIndex = 0
        }

        if (DeathActions.isDead()) {
            clearArrivalState()
            return CycleResult.DEAD
        }

        if (BossHuntState.phase == BossHuntPhase.POST_KILL ||
            BossHuntState.awaitingGeneralMaintenance
        ) {
            return CycleResult.NEED_MAINTENANCE
        }

        if (!ensureInventoryClosed()) {
            return CycleResult.SOFT_FAIL
        }

        return when (BossHuntState.phase) {
            BossHuntPhase.POST_KILL -> CycleResult.NEED_MAINTENANCE
            BossHuntPhase.ENSURE_LOCATION -> ensureLocation(profile)
            BossHuntPhase.HUNT -> hunt(profile)
            BossHuntPhase.FIGHT -> fight(profile)
        }
    }

    /** After potions/elf: clear flag and return to checkpoint map/wire. */
    suspend fun resumeAfterMaintenance(profile: BotProfile): Boolean {
        val cp = BossHuntState.checkpoint
            ?: currentCheckpointOrCursor(profile)
            ?: return false
        BossHuntState.clearMaintenanceFlag()
        BossHuntState.mapIndex = profile.killBossesConfig.maps.indexOf(cp.mapId)
            .takeIf { it >= 0 } ?: BossHuntState.mapIndex
        BossHuntState.wireId = cp.wireId
        BossHuntState.phase = BossHuntPhase.ENSURE_LOCATION
        Log.d(TAG, "[BOSS] resumeAfterMaintenance map=${cp.mapId} wire=${cp.wireId}")
        return navigateToMapWire(cp.mapId, cp.wireId)
    }

    private suspend fun ensureLocation(profile: BotProfile): CycleResult {
        val mapId = currentMapId(profile) ?: return CycleResult.NO_MAPS
        val wire = BossHuntState.wireId.coerceAtLeast(1)
        BossHuntState.saveCheckpoint(mapId, wire)
        Log.d(TAG, "[BOSS] ensure location map=$mapId wire=$wire")
        if (!navigateToMapWire(mapId, wire)) {
            return CycleResult.SOFT_FAIL
        }
        BossHuntState.phase = BossHuntPhase.HUNT
        return CycleResult.OK
    }

    private suspend fun hunt(profile: BotProfile): CycleResult {
        val mapId = currentMapId(profile) ?: return CycleResult.NO_MAPS
        val mapDef = MapDefinitionRepository.getById(mapId)
        if (mapDef == null) {
            Log.w(TAG, "[BOSS] unknown map id=$mapId")
            advanceMap(profile)
            return CycleResult.SOFT_FAIL
        }

        BossHuntState.saveCheckpoint(mapId, BossHuntState.wireId)
        val includeGolden = profile.killBossesConfig.includeGoldenMobs
        val matches = BossMapHuntActions.findAliveBosses(includeGolden)
        if (matches.isEmpty()) {
            Log.d(TAG, "[BOSS] no bosses on wire=${BossHuntState.wireId} → advance wire/map")
            MapWindowActions.closeMapWindow()
            advanceWireOrMap(profile, mapDef)
            BossHuntState.phase = BossHuntPhase.ENSURE_LOCATION
            return CycleResult.OK
        }

        if (!BossMapHuntActions.navigateToBestBoss(
                mapDef = mapDef,
                wireId = BossHuntState.wireId,
                includeGolden = includeGolden,
            )
        ) {
            return CycleResult.SOFT_FAIL
        }
        BossHuntState.phase = BossHuntPhase.FIGHT
        BossHuntState.fightStartedAtMs = 0L
        consecutiveFocusFails = 0
        return CycleResult.OK
    }

    private suspend fun fight(profile: BotProfile): CycleResult {
        val mapId = currentMapId(profile) ?: return CycleResult.NO_MAPS
        val includeGolden = profile.killBossesConfig.includeGoldenMobs
        val fightInProgress = BossHuntState.fightStartedAtMs != 0L

        if (fightInProgress) {
            // Mid-fight: one short acquire round; if focus stays gone → boss killed.
            if (!BossTargetingActions.hasBossFocus()) {
                Log.d(TAG, "[BOSS] focus missing mid-fight — one acquire round")
                if (!BossTargetingActions.ensureFocusBoss(
                        includeGolden = includeGolden,
                        maxAttempts = 1,
                    )
                ) {
                    return finishKillAfterFocusLost(mapId)
                }
            }
        } else {
            // First acquire after arriving on boss — allow a few soft retries then re-hunt.
            if (!BossTargetingActions.ensureFocusBoss(includeGolden = includeGolden)) {
                consecutiveFocusFails++
                Log.w(
                    TAG,
                    "[BOSS] ensureFocusBoss fail $consecutiveFocusFails/$FOCUS_FAIL_BEFORE_REHUNT " +
                        "(no Auto until HUD)",
                )
                if (consecutiveFocusFails >= FOCUS_FAIL_BEFORE_REHUNT) {
                    consecutiveFocusFails = 0
                    BossHuntState.phase = BossHuntPhase.HUNT
                    BossHuntState.clearBossTarget()
                    Log.w(TAG, "[BOSS] focus fail limit → re-hunt")
                }
                delay(FIGHT_POLL_MS)
                return CycleResult.SOFT_FAIL
            }
            consecutiveFocusFails = 0
        }

        if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[BOSS] ensureAuto soft-fail after focus HUD")
            delay(FIGHT_POLL_MS)
            return CycleResult.SOFT_FAIL
        }

        val now = System.currentTimeMillis()
        if (BossHuntState.fightStartedAtMs == 0L) {
            BossHuntState.fightStartedAtMs = now
            Log.d(
                TAG,
                "[BOSS] fight start target=" +
                    "(${BossHuntState.targetCoordX},${BossHuntState.targetCoordY})",
            )
        }

        val elapsed = now - BossHuntState.fightStartedAtMs
        if (!BossTargetingActions.hasBossFocus()) {
            return finishKillAfterFocusLost(mapId)
        }

        Log.d(TAG, "[BOSS] fighting ${elapsed / 1000}s")
        delay(FIGHT_POLL_MS)
        return CycleResult.OK
    }

    /** Wait briefly; if focus does not return, mark post-kill. */
    private suspend fun finishKillAfterFocusLost(mapId: String): CycleResult {
        Log.d(TAG, "[BOSS] boss_focus lost — wait ${POST_FOCUS_LOST_WAIT_MS / 1000}s")
        delay(POST_FOCUS_LOST_WAIT_MS)
        if (BossTargetingActions.hasBossFocus()) {
            Log.d(TAG, "[BOSS] boss_focus returned during wait — keep fighting")
            return CycleResult.OK
        }
        Log.d(TAG, "[BOSS] focus gone after wait → post-kill then re-hunt / next map")
        consecutiveFocusFails = 0
        BossHuntState.clearBossTarget()
        BossHuntState.markPostKill(mapId, BossHuntState.wireId)
        return CycleResult.NEED_MAINTENANCE
    }

    private suspend fun navigateToMapWire(mapId: String, wireId: Int): Boolean {
        val mapDef = MapDefinitionRepository.getById(mapId)
        if (mapDef == null) {
            Log.w(TAG, "[BOSS] map missing id=$mapId")
            return false
        }
        return NavigationOrchestrator.navigateToMapAndWire(mapDef, wireId, farmSpot = null)
    }

    private fun advanceWireOrMap(profile: BotProfile, mapDef: MapDefinition) {
        val wires = mapDef.availableWires().ifEmpty { listOf(1) }
        val current = BossHuntState.wireId
        val nextWire = wires.firstOrNull { it > current }
        if (nextWire != null) {
            Log.d(TAG, "[BOSS] advance wire $current → $nextWire")
            BossHuntState.wireId = nextWire
            return
        }
        advanceMap(profile)
    }

    private fun advanceMap(profile: BotProfile) {
        val maps = profile.killBossesConfig.maps
        if (maps.isEmpty()) return
        val next = (BossHuntState.mapIndex + 1) % maps.size
        Log.d(TAG, "[BOSS] advance map ${BossHuntState.mapIndex + 1}→${next + 1}/${maps.size}")
        BossHuntState.mapIndex = next
        BossHuntState.wireId = 1
    }

    private suspend fun ensureInventoryClosed(): Boolean {
        val open = NavigationVision.findTemplate(INVENTORY_OPEN, 0.8f)
        if (open == null) return true
        Log.d(TAG, "[BOSS] closing inventory")
        if (NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, 0.8f)) {
            delay(500)
            return NavigationVision.findTemplate(INVENTORY_OPEN, 0.8f) == null
        }
        return false
    }

    private fun Int.floorMod(m: Int): Int {
        if (m <= 0) return 0
        val r = this % m
        return if (r < 0) r + m else r
    }
}
