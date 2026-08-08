package com.example.muamaizingbot.bot.combat

import android.util.Log
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.bot.bosses.BossHuntState
import com.example.muamaizingbot.bot.bosses.BossMapHuntActions
import com.example.muamaizingbot.bot.bosses.BossTargetingActions
import com.example.muamaizingbot.bot.bosses.FarmBossesLoop
import com.example.muamaizingbot.bot.maintenance.ElfBuffCheckActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffFocusHud
import com.example.muamaizingbot.bot.maintenance.ElfBuffNavigationActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffSeekGate
import com.example.muamaizingbot.bot.maintenance.ElfBuffTargetingActions
import com.example.muamaizingbot.bot.maintenance.PotionCheckActions
import com.example.muamaizingbot.bot.maintenance.PotionPurchaseActions
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.CombatFocusPkMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.profile.isFarmMode
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import kotlinx.coroutines.delay

/**
 * Farm / farm_bosses defense: PK prevalidation at startup, then Focus enemies
 * (red HUD) → spam Attack until the enemy focus disappears.
 * Caller handles return to farm spot / boss.
 *
 * Mid-spam: periodically checks potions + elf buff; pauses spam, runs the action,
 * then resumes the same loop (farm_bosses returns to stored boss target after elf).
 *
 * Does **not** clear [com.example.muamaizingbot.bot.bosses.BossHuntState] targets.
 *
 * PK mode is switched once at bot start ([prevalidatePkModeAtStartup]).
 * On-spot ticks only confirm the bar; they switch only if it drifted.
 */
object CombatFocusActions {

    private const val TAG = "CombatFocus"
    private const val ATTACK_MAIN = "templates/mu/ui/targeting/attack_main.png"
    private const val FOCUS_PLAYER = "templates/mu/ui/targeting/focus_player.png"
    private const val ATTACK_THRESHOLD = 0.75f
    private const val FOCUS_PLAYER_THRESHOLD = 0.62f
    private const val POST_ATTACK_TAP_MS = 160L
    private const val POST_FOCUS_PROBE_MS = 180L
    /** Log continuous focus spam every N taps while boss emblem is up. */
    private const val FOCUS_PROBE_LOG_EVERY = 10
    /** Log attack spam progress every N taps (loop is unbounded until focus lost). */
    private const val ATTACK_LOG_EVERY = 10
    /** Probe potions/buff about every ~1.5–2s of spam taps. */
    private const val MAINT_EVERY_TAPS = 10
    private const val MAINT_MIN_INTERVAL_MS = 2_000L
    /**
     * Red HP can flicker under VFX while still chasing — require this many
     * consecutive misses before ending attack and returning to spot/boss.
     */
    private const val FOCUS_LOST_CONFIRM_MISSES = 4

    @Volatile
    private var engagingEnemy: Boolean = false

    enum class TickResult {
        /** Toggle off, wrong mode, or no enemy found this tick. */
        Idle,
        /** Enemy red HUD still visible — attack spam in progress. */
        Engaging,
        /** Had enemy focus and it disappeared — caller should recover position. */
        EnemyClearedNeedReturn,
    }

    private enum class FightMaintResult {
        /** No potions/buff work this probe. */
        None,
        /** Ran maintenance — re-evaluate emblem / red HUD before next spam tap. */
        Ran,
        /** Leave spam as Idle (dead, pause, or boss gone after return). */
        AbortIdle,
    }

    fun reset() {
        engagingEnemy = false
    }

    fun isEngagingEnemy(): Boolean = engagingEnemy

    /**
     * True while chasing / fighting an enemy focus — farm off_spot must not
     * yank the character back until focus is confirmed lost.
     */
    suspend fun shouldSuppressOffSpotRecovery(profile: BotProfile): Boolean {
        if (!isApplicable(profile)) return false
        if (profile.combatFocusPkMode == CombatFocusPkMode.PEACE) return false
        if (engagingEnemy) return true
        return ElfBuffFocusHud.isRedHpBarVisible()
    }

    private fun isApplicable(profile: BotProfile): Boolean {
        return profile.enableCombatFocus &&
            (profile.isFarmMode() || profile.isFarmBossesMode())
    }

    /**
     * Startup prevalidation: force the configured PK mode before farm/boss loops.
     * Soft-fail (logs + returns false) — loops will confirm/repair on spot.
     */
    suspend fun prevalidatePkModeAtStartup(profile: BotProfile): Boolean {
        if (!isApplicable(profile)) {
            return true
        }
        val mode = profile.combatFocusPkMode
        Log.d(TAG, "[COMBAT_FOCUS] startup prevalidate pk=${mode.toStorage()}")
        val ok = ElfBuffTargetingActions.ensurePkMode(mode)
        if (!ok) {
            Log.w(TAG, "[COMBAT_FOCUS] startup ensurePkMode failed mode=${mode.toStorage()}")
        } else {
            Log.d(TAG, "[COMBAT_FOCUS] startup pk ok mode=${mode.toStorage()}")
        }
        return ok
    }

    /**
     * On-spot confirm: if already in the configured PK mode, do nothing.
     * Only open the PK menu when the bar drifted away from the profile setting.
     */
    private suspend fun confirmPkModeOrRepair(profile: BotProfile): Boolean {
        val mode = profile.combatFocusPkMode
        if (ElfBuffTargetingActions.isPkMode(mode)) {
            return true
        }
        Log.w(
            TAG,
            "[COMBAT_FOCUS] pk confirm miss → repair to ${mode.toStorage()}",
        )
        return ElfBuffTargetingActions.ensurePkMode(mode)
    }

    /**
     * One combat-focus tick. Call only from active **farming** / **farm_bosses FIGHT**.
     * Does not re-force PK every cycle — only confirms / repairs drift.
     */
    suspend fun tickIfEnabled(profile: BotProfile): TickResult {
        if (!profile.enableCombatFocus) {
            if (engagingEnemy) {
                engagingEnemy = false
            }
            return TickResult.Idle
        }
        if (!profile.isFarmMode() && !profile.isFarmBossesMode()) {
            return TickResult.Idle
        }

        if (!confirmPkModeOrRepair(profile)) {
            Log.w(
                TAG,
                "[COMBAT_FOCUS] pk repair failed mode=${profile.combatFocusPkMode.toStorage()}",
            )
            return if (engagingEnemy) TickResult.Engaging else TickResult.Idle
        }

        // Peace: only keep PK mode — no enemy focus / attack spam.
        if (profile.combatFocusPkMode == CombatFocusPkMode.PEACE) {
            if (engagingEnemy) {
                engagingEnemy = false
            }
            return TickResult.Idle
        }

        // farm_bosses FIGHT: boss red HP shares the player template. Keep spamming
        // focus_player for the whole fight until the boss emblem drops (kill → nav)
        // or a player target is acquired.
        if (profile.isFarmBossesMode() && BossTargetingActions.hasBossFocus()) {
            return spamFocusDuringBossFight(profile)
        }

        if (!ElfBuffFocusHud.isRedHpBarVisible()) {
            if (engagingEnemy) {
                if (confirmEnemyFocusLost(alreadyMissedOnce = true)) {
                    engagingEnemy = false
                    Log.d(TAG, "[COMBAT_FOCUS] enemy focus lost (confirmed) → need return")
                    return TickResult.EnemyClearedNeedReturn
                }
                // Flicker — still engaging; resume attack.
                return spamAttackWhileRedHud(profile)
            }
            val found = ElfBuffTargetingActions.spamFocusUntilRedHud()
            if (!found) {
                Log.d(TAG, "[COMBAT_FOCUS] no enemy focus this tick")
                return TickResult.Idle
            }
            Log.d(TAG, "[COMBAT_FOCUS] enemy red HUD acquired")
        }

        engagingEnemy = true
        return spamAttackWhileRedHud(profile)
    }

    /**
     * Continuous [FOCUS_PLAYER] spam for the active boss FIGHT.
     * Runs until:
     * - a player focus is acquired (red HUD, no boss emblem) → attack spam, or
     * - boss emblem is gone → Idle so [FarmBossesLoop] can finish kill / navigate.
     */
    private suspend fun spamFocusDuringBossFight(profile: BotProfile): TickResult {
        Log.d(TAG, "[COMBAT_FOCUS] boss fight — continuous focus_player spam start")
        val (w, h) = RefCoords.activeScreenSize()
        val roi = MuCombatRois.targetingHudRoi(w, h)
        var taps = 0
        var lastMaintMs = 0L
        while (true) {
            if (BotController.state.value != BotRuntimeState.RUNNING) {
                Log.d(
                    TAG,
                    "[COMBAT_FOCUS] boss-fight focus spam aborted — bot not running " +
                        "after $taps taps",
                )
                return if (engagingEnemy) TickResult.Engaging else TickResult.Idle
            }
            if (DeathActions.isDead()) {
                engagingEnemy = false
                Log.w(TAG, "[COMBAT_FOCUS] dead during boss-fight focus spam after $taps taps")
                return TickResult.Idle
            }

            if (shouldProbeFightMaintenance(taps, lastMaintMs)) {
                lastMaintMs = System.currentTimeMillis()
                when (maybeRunFightMaintenance(profile)) {
                    FightMaintResult.None -> Unit
                    FightMaintResult.AbortIdle -> {
                        engagingEnemy = false
                        return TickResult.Idle
                    }
                    FightMaintResult.Ran -> {
                        Log.d(TAG, "[COMBAT_FOCUS] spam resume after maintenance")
                        continue
                    }
                }
            }

            val bossEmblem = BossTargetingActions.hasBossFocus()
            val red = ElfBuffFocusHud.isRedHpBarVisible()
            if (red && !bossEmblem) {
                Log.d(
                    TAG,
                    "[COMBAT_FOCUS] enemy focus acquired mid-boss fight after $taps focus taps",
                )
                engagingEnemy = true
                return spamAttackWhileRedHud(profile)
            }
            if (!bossEmblem) {
                if (engagingEnemy) {
                    engagingEnemy = false
                }
                Log.d(
                    TAG,
                    "[COMBAT_FOCUS] boss emblem gone after $taps focus taps → yield for kill/nav",
                )
                return TickResult.Idle
            }

            val match = NavigationVision.findTemplate(FOCUS_PLAYER, FOCUS_PLAYER_THRESHOLD, roi)
            if (match == null) {
                if (taps == 0 || (taps + 1) % FOCUS_PROBE_LOG_EVERY == 0) {
                    Log.d(
                        TAG,
                        "[COMBAT_FOCUS] focus_player miss spam tap=${taps + 1}",
                    )
                    NavigationVision.logBestScore(FOCUS_PLAYER, roi)
                }
            } else {
                if (taps == 0 || (taps + 1) % FOCUS_PROBE_LOG_EVERY == 0) {
                    Log.d(
                        TAG,
                        "[COMBAT_FOCUS] focus spam tap=${taps + 1} " +
                            "at=(${match.centerX},${match.centerY}) " +
                            "score=${"%.3f".format(match.score)}",
                    )
                }
                NavigationVision.tapScreen(match.centerX, match.centerY, label = "focus_player")
            }
            delay(BotTiming.ms(POST_FOCUS_PROBE_MS, BotTimingCategory.POST_TAP))
            taps++
        }
    }

    /**
     * @param alreadyMissedOnce true when caller already observed one red-HUD miss.
     * @return true when focus stays gone for [FOCUS_LOST_CONFIRM_MISSES] checks.
     */
    private suspend fun confirmEnemyFocusLost(alreadyMissedOnce: Boolean): Boolean {
        var misses = if (alreadyMissedOnce) 1 else 0
        if (misses > 0) {
            Log.d(
                TAG,
                "[COMBAT_FOCUS] red HUD miss $misses/$FOCUS_LOST_CONFIRM_MISSES (confirming loss)",
            )
        }
        while (misses < FOCUS_LOST_CONFIRM_MISSES) {
            if (BotController.state.value != BotRuntimeState.RUNNING) {
                return false
            }
            delay(BotTiming.ms(POST_ATTACK_TAP_MS, BotTimingCategory.POST_TAP))
            if (ElfBuffFocusHud.isRedHpBarVisible()) {
                Log.d(
                    TAG,
                    "[COMBAT_FOCUS] red HUD returned after miss_streak=$misses — keep attacking",
                )
                return false
            }
            misses++
            Log.d(
                TAG,
                "[COMBAT_FOCUS] red HUD miss $misses/$FOCUS_LOST_CONFIRM_MISSES (confirming loss)",
            )
        }
        return true
    }

    /**
     * Continuous Attack spam until enemy red focus disappears (or death).
     * Focus loss requires [FOCUS_LOST_CONFIRM_MISSES] consecutive misses.
     */
    private suspend fun spamAttackWhileRedHud(profile: BotProfile): TickResult {
        val (w, h) = RefCoords.activeScreenSize()
        val roi = MuCombatRois.targetingHudRoi(w, h)
        var taps = 0
        var lastMaintMs = 0L
        Log.d(TAG, "[COMBAT_FOCUS] attack spam continuous start")
        while (true) {
            if (BotController.state.value != BotRuntimeState.RUNNING) {
                Log.d(
                    TAG,
                    "[COMBAT_FOCUS] attack spam aborted — bot not running after $taps taps",
                )
                return TickResult.Engaging
            }
            if (DeathActions.isDead()) {
                engagingEnemy = false
                Log.w(TAG, "[COMBAT_FOCUS] dead during attack spam after $taps taps")
                return TickResult.Idle
            }

            if (shouldProbeFightMaintenance(taps, lastMaintMs)) {
                lastMaintMs = System.currentTimeMillis()
                when (maybeRunFightMaintenance(profile)) {
                    FightMaintResult.None -> Unit
                    FightMaintResult.AbortIdle -> {
                        engagingEnemy = false
                        return TickResult.Idle
                    }
                    FightMaintResult.Ran -> {
                        Log.d(TAG, "[COMBAT_FOCUS] spam resume after maintenance")
                        continue
                    }
                }
            }

            if (!ElfBuffFocusHud.isRedHpBarVisible()) {
                if (!confirmEnemyFocusLost(alreadyMissedOnce = true)) {
                    continue
                }
                engagingEnemy = false
                Log.d(
                    TAG,
                    "[COMBAT_FOCUS] enemy focus cleared after $taps attack taps → need return",
                )
                return TickResult.EnemyClearedNeedReturn
            }
            val match = NavigationVision.findTemplate(ATTACK_MAIN, ATTACK_THRESHOLD, roi)
            if (match == null) {
                if (taps % ATTACK_LOG_EVERY == 0) {
                    Log.w(TAG, "[COMBAT_FOCUS] attack_main miss tap=${taps + 1}")
                    NavigationVision.logBestScore(ATTACK_MAIN, roi)
                }
                delay(BotTiming.ms(POST_ATTACK_TAP_MS, BotTimingCategory.POST_TAP))
                taps++
                continue
            }
            if (taps == 0 || (taps + 1) % ATTACK_LOG_EVERY == 0) {
                Log.d(
                    TAG,
                    "[COMBAT_FOCUS] attack spam tap=${taps + 1} " +
                        "at=(${match.centerX},${match.centerY}) score=${"%.3f".format(match.score)}",
                )
            }
            if (!NavigationVision.tapScreen(match.centerX, match.centerY, label = "attack_main")) {
                Log.w(TAG, "[COMBAT_FOCUS] attack_main tap failed")
            }
            delay(BotTiming.ms(POST_ATTACK_TAP_MS, BotTimingCategory.POST_TAP))
            taps++
        }
    }

    /** True every [MAINT_EVERY_TAPS] taps, or when [MAINT_MIN_INTERVAL_MS] elapsed after a prior probe. */
    private fun shouldProbeFightMaintenance(taps: Int, lastMaintMs: Long): Boolean {
        if (taps <= 0) {
            return false
        }
        if (taps % MAINT_EVERY_TAPS == 0) {
            return true
        }
        return lastMaintMs > 0L &&
            System.currentTimeMillis() - lastMaintMs >= MAINT_MIN_INTERVAL_MS
    }

    /**
     * Potions first, then elf buff. Returns [FightMaintResult.Ran] after work so the
     * spam loop re-evaluates HUD before the next tap.
     */
    private suspend fun maybeRunFightMaintenance(profile: BotProfile): FightMaintResult {
        if (BotController.state.value != BotRuntimeState.RUNNING) {
            return FightMaintResult.AbortIdle
        }

        if (profile.enablePotionRecovery && PotionCheckActions.isAnyPotionEmpty()) {
            Log.d(TAG, "[COMBAT_FOCUS] spam pause → potions")
            val ok = PotionPurchaseActions.handleEmptyPotions()
            if (BotController.state.value != BotRuntimeState.RUNNING) {
                return FightMaintResult.AbortIdle
            }
            if (DeathActions.isDead()) {
                return FightMaintResult.AbortIdle
            }
            if (!ok) {
                Log.w(TAG, "[COMBAT_FOCUS] potion recovery failed mid-spam — resume anyway")
            }
            return FightMaintResult.Ran
        }

        if (ElfBuffSeekGate.shouldAttemptSeek(profile) && !ElfBuffCheckActions.hasElfBuff()) {
            Log.d(TAG, "[COMBAT_FOCUS] spam pause → buff")
            val ok = ElfBuffNavigationActions.goToElfBuffAndReturn()
            if (!ok) {
                ElfBuffSeekGate.noteSeekFailed()
                Log.w(TAG, "[COMBAT_FOCUS] elf buff route failed mid-spam")
            }
            if (BotController.state.value != BotRuntimeState.RUNNING) {
                return FightMaintResult.AbortIdle
            }
            if (DeathActions.isDead()) {
                return FightMaintResult.AbortIdle
            }

            if (profile.isFarmBossesMode()) {
                val mapId = FarmBossesLoop.currentMapId(profile)
                    ?: BossHuntState.checkpoint?.mapId
                val mapDef = mapId?.let { MapDefinitionRepository.getById(it) }
                val wire = BossHuntState.wireId.coerceAtLeast(1)
                if (mapDef == null) {
                    Log.w(TAG, "[COMBAT_FOCUS] post-elf no map — abort spam")
                    return FightMaintResult.AbortIdle
                }
                Log.d(TAG, "[COMBAT_FOCUS] post-elf return to boss target wire=$wire")
                val returned = BossMapHuntActions.returnToStoredBossTarget(mapDef, wire)
                if (!returned) {
                    Log.w(TAG, "[COMBAT_FOCUS] post-elf returnToBoss failed — abort spam")
                    return FightMaintResult.AbortIdle
                }
                val bossEmblem = BossTargetingActions.hasBossFocus()
                val red = ElfBuffFocusHud.isRedHpBarVisible()
                if (!bossEmblem && !red) {
                    Log.d(
                        TAG,
                        "[COMBAT_FOCUS] post-elf no boss emblem / red HUD — yield Idle",
                    )
                    return FightMaintResult.AbortIdle
                }
            }
            return FightMaintResult.Ran
        }

        return FightMaintResult.None
    }
}
