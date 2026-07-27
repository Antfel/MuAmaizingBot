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

    private val bossesKilledCount = AtomicInteger(0)
    private val _bossesKilled = MutableStateFlow(0)
    /** Session kills since last Farm Bosses start (overlay). */
    val bossesKilled: StateFlow<Int> = _bossesKilled.asStateFlow()

    fun reset() {
        mapIndex = 0
        wireId = 1
        phase = BossHuntPhase.ENSURE_LOCATION
        checkpoint = null
        awaitingGeneralMaintenance = false
        fightStartedAtMs = 0L
        clearBossTarget()
        bossesKilledCount.set(0)
        _bossesKilled.value = 0
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
        _bossesKilled.value = bossesKilledCount.incrementAndGet()
    }

    fun clearMaintenanceFlag() {
        awaitingGeneralMaintenance = false
        if (phase == BossHuntPhase.POST_KILL) {
            phase = BossHuntPhase.ENSURE_LOCATION
        }
    }

    fun shouldRunGeneralMaintenance(): Boolean = awaitingGeneralMaintenance
}
