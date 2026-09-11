package com.example.muamaizingbot.bot.bosses

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runtime hunt cursor for Farm Bosses (maps-only).
 * Checkpoint is the map+wire to resume after post-kill maintenance or death.
 */
data class BossHuntCheckpoint(
    val mapId: String,
    val wireId: Int,
)

/** One boss icon on the parchment (geometry only — no alive/dead). */
data class HuntSlot(
    val centerX: Int,
    val centerY: Int,
    val bestX: Int,
    val bestY: Int,
    val templateWidth: Int,
    val templateHeight: Int,
    val templateName: String,
) {
    fun toHuntIcon(score: Float = 0f): HuntIcon = HuntIcon(
        centerX = centerX,
        centerY = centerY,
        bestX = bestX,
        bestY = bestY,
        templateWidth = templateWidth,
        templateHeight = templateHeight,
        score = score,
        templateName = templateName,
    )
}

/** Shared parchment slots for every wire of [mapId]. */
data class MapHuntLayout(
    val mapId: String,
    val slots: List<HuntSlot>,
)

/** One boss icon from a classified wire pass. */
data class HuntIcon(
    val centerX: Int,
    val centerY: Int,
    val bestX: Int,
    val bestY: Int,
    val templateWidth: Int,
    val templateHeight: Int,
    val score: Float,
    val templateName: String,
)

/** Cursor over this wire's live icons (re-classified from [MapHuntLayout]). */
data class WireHuntPlan(
    val mapId: String,
    val wireId: Int,
    val icons: List<HuntIcon>,
    var nextIndex: Int = 0,
) {
    fun current(): HuntIcon? = icons.getOrNull(nextIndex)

    fun isExhausted(): Boolean = nextIndex >= icons.size

    fun consume() {
        if (nextIndex < icons.size) nextIndex++
    }
}

enum class BossHuntPhase {
    ENSURE_LOCATION,
    HUNT,
    FIGHT,
    POST_KILL,
}

object BossHuntState {
    @Volatile
    var mapIndex: Int = 0

    @Volatile
    var wireId: Int = 1

    @Volatile
    var phase: BossHuntPhase = BossHuntPhase.ENSURE_LOCATION

    @Volatile
    var checkpoint: BossHuntCheckpoint? = null

    /** When true, [BotPriorityLoop] may run potions/elf before returning to hunt. */
    @Volatile
    var awaitingGeneralMaintenance: Boolean = false

    @Volatile
    var fightStartedAtMs: Long = 0L

    /** Game HUD coords of the boss icon we tapped (from affine). */
    @Volatile
    var targetCoordX: Int? = null

    @Volatile
    var targetCoordY: Int? = null

    @Volatile
    var mapLayout: MapHuntLayout? = null

    @Volatile
    var huntPlan: WireHuntPlan? = null

    private val bossesKilledCount = AtomicInteger(0)
    private val _bossesKilled = MutableStateFlow(0)
    /** Session kills (overlay). Survives farm↔bosses cycles; cleared on Stop. */
    val bossesKilled: StateFlow<Int> = _bossesKilled.asStateFlow()

    fun reset() {
        resetCycle()
        bossesKilledCount.set(0)
        _bossesKilled.value = 0
    }

    /**
     * Wipe hunt cursor / checkpoints / fight leftovers for a new bosses cycle.
     * Does **not** touch [bossesKilled].
     */
    fun resetCycle() {
        mapIndex = 0
        wireId = 1
        phase = BossHuntPhase.ENSURE_LOCATION
        checkpoint = null
        awaitingGeneralMaintenance = false
        fightStartedAtMs = 0L
        clearBossTarget()
        huntPlan = null
        mapLayout = null
    }

    fun setBossTarget(coordX: Int, coordY: Int) {
        targetCoordX = coordX
        targetCoordY = coordY
    }

    fun clearBossTarget() {
        targetCoordX = null
        targetCoordY = null
    }

    fun saveCheckpoint(mapId: String, wire: Int) {
        checkpoint = BossHuntCheckpoint(mapId = mapId, wireId = wire.coerceAtLeast(1))
    }

    fun markPostKill(mapId: String, wire: Int) {
        saveCheckpoint(mapId, wire)
        awaitingGeneralMaintenance = true
        phase = BossHuntPhase.POST_KILL
        fightStartedAtMs = 0L
        consumePlanIcon()
        _bossesKilled.value = bossesKilledCount.incrementAndGet()
    }

    fun clearHuntPlan() {
        huntPlan = null
    }

    fun clearMapLayout() {
        mapLayout = null
        huntPlan = null
    }

    fun consumePlanIcon() {
        huntPlan?.consume()
    }

    /** Skip this wire's current icon without counting a kill (focus never acquired). */
    fun skipCurrentPlanIcon() {
        huntPlan?.consume()
    }

    @Suppress("UNUSED_PARAMETER")
    fun noteHuntTap(screenX: Int, screenY: Int) {
        // Layout owns geometry; tap is only for the current hop log.
    }

    fun clearMaintenanceFlag() {
        awaitingGeneralMaintenance = false
        if (phase == BossHuntPhase.POST_KILL) {
            phase = BossHuntPhase.ENSURE_LOCATION
        }
    }

    fun shouldRunGeneralMaintenance(): Boolean = awaitingGeneralMaintenance
}
