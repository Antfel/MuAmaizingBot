package com.example.muamaizingbot.bot.loop

import android.util.Log
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.bot.bosses.BossHuntPhase
import com.example.muamaizingbot.bot.bosses.BossHuntState
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.profile.isFarmMode
import java.util.Calendar

/**
 * Automatic Farm ↔ Farm Bosses rotation ("Programación").
 * Call [maybeApply] at a safe point in [BotPriorityLoop]; [noteBossLapComplete] from map wrap.
 */
object ModeRotationGate {

    private const val TAG = "ModeRotation"
    private const val REST_PERSIST_EVERY_MS = 60_000L

    @Volatile
    private var memRestAccumulatedMs: Long = 0L

    @Volatile
    private var restSliceStartedAtMs: Long = 0L

    @Volatile
    private var seededForFilename: String? = null

    @Volatile
    private var lastRestPersistAtMs: Long = 0L

    enum class ApplyResult {
        NONE,
        DEFERRED,
        SWITCHED_TO_FARM,
        SWITCHED_TO_BOSSES,
    }

    /**
     * Set when [maybeApply] changes mode from inside a long action (elf/potion/fight).
     * [BotPriorityLoop] consumes this to navigate + force pet on the next safe tick.
     */
    @Volatile
    private var pendingNavigation: ApplyResult? = null

    fun resetMemory() {
        memRestAccumulatedMs = 0L
        restSliceStartedAtMs = 0L
        seededForFilename = null
        lastRestPersistAtMs = 0L
        pendingNavigation = null
        Log.d(TAG, "[MODE_ROTATION] memory reset")
    }

    /** Consume a navigation request left by an in-action mode flip. */
    fun takePendingNavigation(): ApplyResult? {
        val pending = pendingNavigation ?: return null
        pendingNavigation = null
        Log.d(TAG, "[MODE_ROTATION] take pending nav=$pending")
        return pending
    }

    fun clearPendingNavigation() {
        pendingNavigation = null
    }

    /** Fired when Farm Bosses map cursor wraps last → first. */
    fun noteBossLapComplete(profile: BotProfile) {
        val rot = profile.modeRotation
        if (!rot.enabled || rot.strategy != ModeRotationStrategy.MAP_LAP) return
        if (rot.lapCompletePending) {
            Log.d(TAG, "[MODE_ROTATION] lap_complete already pending")
            return
        }
        ProfileRepository.setModeRotationConfig(
            profile.filename,
            rot.copy(lapCompletePending = true),
        )
        Log.d(TAG, "[MODE_ROTATION] lap_complete pending restMin=${rot.restMinutes}")
    }

    /**
     * If rotation wants a different mode and it is safe, switch and return which side.
     * Caller must navigate to farm spot / boss checkpoint.
     */
    fun maybeApply(profile: BotProfile): ApplyResult {
        val rot = profile.modeRotation
        if (!rot.enabled) return ApplyResult.NONE
        if (profile.isElfBuffPostMode()) return ApplyResult.NONE
        if (!profile.isFarmMode() && !profile.isFarmBossesMode()) return ApplyResult.NONE

        ensureSeeded(profile)

        val desired = when (rot.strategy) {
            ModeRotationStrategy.MAP_LAP -> desiredMapLap(profile, rot)
            ModeRotationStrategy.CLOCK -> desiredClock(rot)
        } ?: return ApplyResult.NONE

        val current = when {
            profile.isFarmBossesMode() -> BotMode.FARM_BOSSES
            else -> BotMode.FARM
        }
        if (desired == current) {
            if (rot.strategy == ModeRotationStrategy.MAP_LAP) {
                tickRestAccumulation(profile, rot)
            }
            return ApplyResult.NONE
        }

        if (!isSafeToSwitch(profile)) {
            val reason = unsafeSwitchReason(profile)
            Log.d(
                TAG,
                "[MODE_ROTATION] defer switch want=$desired reason=$reason " +
                    "phase=${BossHuntState.phase} ui=${DisconnectDetector.uiActionReason()}",
            )
            return ApplyResult.DEFERRED
        }

        return when (desired) {
            BotMode.FARM -> switchToFarmRest(profile, rot)
            BotMode.FARM_BOSSES -> switchToBosses(profile, rot)
            else -> ApplyResult.NONE
        }
    }

    /**
     * Mode flips wait while:
     * - a UI action runs (elf buff, potion shop, pet validate, seal shop, …),
     * - Farm Bosses is still in FIGHT (finish the boss first).
     */
    fun isSafeToSwitch(profile: BotProfile): Boolean = unsafeSwitchReason(profile) == null

    fun unsafeSwitchReason(profile: BotProfile): String? {
        if (DisconnectDetector.isUiActionActive()) {
            val reason = DisconnectDetector.uiActionReason().ifBlank { "ui_action" }
            return "ui:$reason"
        }
        if (profile.isFarmBossesMode() && BossHuntState.phase == BossHuntPhase.FIGHT) {
            return "boss_fight"
        }
        return null
    }

    /** Visible for tests: MAP_LAP desired mode without side effects. */
    internal fun desiredMapLapForTest(
        botMode: String,
        rot: ModeRotationConfig,
        restAccumulatedMs: Long,
    ): String? {
        if (!rot.enabled || rot.strategy != ModeRotationStrategy.MAP_LAP) return null
        if (rot.lapCompletePending) return BotMode.FARM
        if (ModeRotationConfig.normalizeSegment(rot.segment) == ModeRotationConfig.SEGMENT_REST) {
            val needMs = rot.restMinutes.coerceIn(
                ModeRotationConfig.MIN_REST_MINUTES,
                ModeRotationConfig.MAX_REST_MINUTES,
            ) * 60_000L
            return if (restAccumulatedMs >= needMs) BotMode.FARM_BOSSES else BotMode.FARM
        }
        return BotMode.FARM_BOSSES
    }

    /** Visible for tests: CLOCK desired mode from minute-of-day. */
    internal fun desiredClockAtMinutes(
        rot: ModeRotationConfig,
        minuteOfDay: Int,
    ): String? {
        if (!rot.enabled || rot.strategy != ModeRotationStrategy.CLOCK) return null
        val spot = ModeRotationConfig.parseHhMmToMinutes(
            ModeRotationConfig.primaryTime(rot.farmWindows),
        ) ?: return null
        val bosses = ModeRotationConfig.parseHhMmToMinutes(
            ModeRotationConfig.primaryTime(rot.bossesWindows),
        ) ?: return null
        if (spot == bosses) return BotMode.FARM_BOSSES
        val inFarm = if (spot < bosses) {
            minuteOfDay in spot until bosses
        } else {
            minuteOfDay >= spot || minuteOfDay < bosses
        }
        return if (inFarm) BotMode.FARM else BotMode.FARM_BOSSES
    }

    private fun desiredMapLap(profile: BotProfile, rot: ModeRotationConfig): String? {
        tickRestAccumulation(profile, rot)
        return desiredMapLapForTest(profile.botMode, rot, memRestAccumulatedMs)
    }

    private fun desiredClock(rot: ModeRotationConfig): String? {
        val cal = Calendar.getInstance()
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return desiredClockAtMinutes(rot, minuteOfDay)
    }

    private fun switchToFarmRest(profile: BotProfile, rot: ModeRotationConfig): ApplyResult {
        memRestAccumulatedMs = 0L
        restSliceStartedAtMs = 0L
        lastRestPersistAtMs = System.currentTimeMillis()
        ProfileRepository.setModeRotationConfig(
            profile.filename,
            rot.copy(
                segment = ModeRotationConfig.SEGMENT_REST,
                restAccumulatedMs = 0L,
                lapCompletePending = false,
            ),
        )
        ProfileRepository.setBotMode(profile.filename, BotMode.FARM)
        BossHuntState.reset()
        pendingNavigation = ApplyResult.SWITCHED_TO_FARM
        Log.d(
            TAG,
            "[MODE_ROTATION] switch → farm restMin=${rot.restMinutes} strategy=${rot.strategy.toStorage()}",
        )
        return ApplyResult.SWITCHED_TO_FARM
    }

    private fun switchToBosses(profile: BotProfile, rot: ModeRotationConfig): ApplyResult {
        memRestAccumulatedMs = 0L
        restSliceStartedAtMs = 0L
        lastRestPersistAtMs = 0L
        ProfileRepository.setModeRotationConfig(
            profile.filename,
            rot.copy(
                segment = ModeRotationConfig.SEGMENT_BOSSES,
                restAccumulatedMs = 0L,
                lapCompletePending = false,
            ),
        )
        ProfileRepository.setBotMode(profile.filename, BotMode.FARM_BOSSES)
        BossHuntState.reset()
        pendingNavigation = ApplyResult.SWITCHED_TO_BOSSES
        Log.d(
            TAG,
            "[MODE_ROTATION] switch → farm_bosses strategy=${rot.strategy.toStorage()}",
        )
        return ApplyResult.SWITCHED_TO_BOSSES
    }

    private fun ensureSeeded(profile: BotProfile) {
        if (seededForFilename == profile.filename) return
        memRestAccumulatedMs = profile.modeRotation.restAccumulatedMs.coerceAtLeast(0L)
        restSliceStartedAtMs = 0L
        lastRestPersistAtMs = System.currentTimeMillis()
        seededForFilename = profile.filename
        Log.d(
            TAG,
            "[MODE_ROTATION] seed file=${profile.filename} restAccumMs=$memRestAccumulatedMs " +
                "segment=${profile.modeRotation.segment}",
        )
    }

    private fun tickRestAccumulation(profile: BotProfile, rot: ModeRotationConfig) {
        if (ModeRotationConfig.normalizeSegment(rot.segment) != ModeRotationConfig.SEGMENT_REST) {
            restSliceStartedAtMs = 0L
            return
        }
        if (BotController.state.value != BotRuntimeState.RUNNING) {
            restSliceStartedAtMs = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (restSliceStartedAtMs == 0L) {
            restSliceStartedAtMs = now
            return
        }
        val delta = (now - restSliceStartedAtMs).coerceAtLeast(0L)
        restSliceStartedAtMs = now
        if (delta == 0L) return
        memRestAccumulatedMs += delta
        if (now - lastRestPersistAtMs >= REST_PERSIST_EVERY_MS) {
            lastRestPersistAtMs = now
            ProfileRepository.setModeRotationConfig(
                profile.filename,
                rot.copy(restAccumulatedMs = memRestAccumulatedMs),
            )
            Log.d(
                TAG,
                "[MODE_ROTATION] rest progress ${memRestAccumulatedMs / 60_000L}m / ${rot.restMinutes}m",
            )
        }
    }
}
