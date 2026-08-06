package com.example.muamaizingbot.bot.combat

import android.util.Log
import com.example.muamaizingbot.bot.bosses.BossTargetingActions
import com.example.muamaizingbot.bot.maintenance.ElfBuffFocusHud
import com.example.muamaizingbot.bot.maintenance.ElfBuffTargetingActions
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
 * Does **not** clear [com.example.muamaizingbot.bot.bosses.BossHuntState] targets.
 *
 * PK mode is switched once at bot start ([prevalidatePkModeAtStartup]).
 * On-spot ticks only confirm the bar; they switch only if it drifted.
 */
object CombatFocusActions {

    private const val TAG = "CombatFocus"
    private const val ATTACK_MAIN = "templates/mu/ui/targeting/attack_main.png"
    private const val ATTACK_THRESHOLD = 0.75f
    private const val POST_ATTACK_TAP_MS = 160L
    /** Log attack spam progress every N taps (loop is unbounded until focus lost). */
    private const val ATTACK_LOG_EVERY = 10
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
     * One combat-focus tick. Safe no-op when disabled or not farm/farm_bosses.
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

        // farm_bosses: boss red HP uses the same HUD template as players.
        // While boss_focus emblem is up, do not treat that red bar as an enemy.
        if (profile.isFarmBossesMode() && BossTargetingActions.hasBossFocus()) {
            if (engagingEnemy) {
                engagingEnemy = false
            }
            Log.d(TAG, "[COMBAT_FOCUS] boss focus active — skip enemy engage")
            return TickResult.Idle
        }

        if (!ElfBuffFocusHud.isRedHpBarVisible()) {
            if (engagingEnemy) {
                if (confirmEnemyFocusLost(alreadyMissedOnce = true)) {
                    engagingEnemy = false
                    Log.d(TAG, "[COMBAT_FOCUS] enemy focus lost (confirmed) → need return")
                    return TickResult.EnemyClearedNeedReturn
                }
                // Flicker — still engaging; resume attack.
                return spamAttackWhileRedHud()
            }
            val found = ElfBuffTargetingActions.spamFocusUntilRedHud()
            if (!found) {
                Log.d(TAG, "[COMBAT_FOCUS] no enemy focus this tick")
                return TickResult.Idle
            }
            Log.d(TAG, "[COMBAT_FOCUS] enemy red HUD acquired")
        }

        engagingEnemy = true
        return spamAttackWhileRedHud()
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
    private suspend fun spamAttackWhileRedHud(): TickResult {
        val (w, h) = RefCoords.activeScreenSize()
        val roi = MuCombatRois.targetingHudRoi(w, h)
        var taps = 0
        Log.d(TAG, "[COMBAT_FOCUS] attack spam continuous start")
        while (true) {
            if (DeathActions.isDead()) {
                engagingEnemy = false
                Log.w(TAG, "[COMBAT_FOCUS] dead during attack spam after $taps taps")
                return TickResult.Idle
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
}
