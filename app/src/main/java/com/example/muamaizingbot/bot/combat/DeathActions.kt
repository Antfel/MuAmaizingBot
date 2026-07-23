package com.example.muamaizingbot.bot.combat

import android.util.Log
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
import kotlinx.coroutines.delay

object DeathActions {

    private const val TAG = "Death"
    private const val DEAD_TEMPLATE = "templates/mu/ui/dead_state.png"
    private const val DEAD_THRESHOLD = 0.72f
    private const val DEAD_THRESHOLD_FALLBACK = 0.62f
    private const val REVIVE_X = 1120
    private const val REVIVE_Y = 865
    private const val REVIVE_WAIT_MS = 3000L
    private const val REVIVE_MAX_ATTEMPTS = 3

    /**
     * Game lockout after death: revive button shows a countdown; after this delay the
     * character auto-revives at the city / resurrection point (not at the death position).
     * Manual revive taps before the countdown ends do nothing useful.
     */
    const val DEATH_LOCKOUT_MS = 60_000L

    /** Extra wait after [DEATH_LOCKOUT_MS] for the city load / HUD to come back. */
    private const val AUTO_REVIVE_SLACK_MS = 30_000L
    private const val AUTO_REVIVE_POLL_MS = 2_000L

    suspend fun isDead(): Boolean {
        val frame = NavigationVision.captureFrame() ?: run {
            Log.w(TAG, "[DEATH] no frame")
            return false
        }

        return try {
            val roi = MuCombatRois.deathDialogRoi(frame)
            val match = NavigationVision.findOnFrame(frame, DEAD_TEMPLATE, DEAD_THRESHOLD, roi)
            if (match != null) {
                Log.d(TAG, "[DEATH] isDead=true score=${match.score}")
                return true
            }

            val fallback = NavigationVision.findOnFrame(frame, DEAD_TEMPLATE, DEAD_THRESHOLD_FALLBACK, null)
            if (fallback != null) {
                Log.d(TAG, "[DEATH] isDead=true fallback score=${fallback.score}")
                return true
            }

            val debug = NavigationVision.probeOnFrame(frame, DEAD_TEMPLATE, roi)
            Log.d(TAG, "[DEATH] isDead=false bestScore=${debug.score}")
            false
        } finally {
            frame.recycle()
        }
    }

    suspend fun recoverIfDead(): Boolean {
        repeat(REVIVE_MAX_ATTEMPTS) { attempt ->
            if (!isDead()) {
                Log.d(TAG, "[DEATH] alive attempt=${attempt + 1}")
                return true
            }

            Log.d(TAG, "[DEATH] reviving attempt=${attempt + 1} at ($REVIVE_X,$REVIVE_Y)")
            if (!NavigationVision.tap(REVIVE_X, REVIVE_Y)) {
                Log.w(TAG, "[DEATH] revive tap failed attempt=${attempt + 1}")
                delay(1000)
                return@repeat
            }

            delay(REVIVE_WAIT_MS)
            if (!isDead()) {
                Log.d(TAG, "[DEATH] revive ok attempt=${attempt + 1}")
                return true
            }
            Log.w(TAG, "[DEATH] still dead after revive attempt=${attempt + 1}")
        }

        Log.w(TAG, "[DEATH] revive failed after $REVIVE_MAX_ATTEMPTS attempts")
        return false
    }

    /**
     * Wait out the death countdown until the game auto-revives (city / resurrection point).
     * Does not tap the revive button — taps are useless during [DEATH_LOCKOUT_MS].
     */
    suspend fun waitForAutoRevive(
        timeoutMs: Long = DEATH_LOCKOUT_MS + AUTO_REVIVE_SLACK_MS,
    ): Boolean {
        if (!isDead()) {
            Log.d(TAG, "[DEATH] already alive (skip auto-revive wait)")
            return true
        }
        Log.d(TAG, "[DEATH] waiting auto-revive up to ${timeoutMs}ms (lockout=${DEATH_LOCKOUT_MS}ms)")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(AUTO_REVIVE_POLL_MS)
            if (!isDead()) {
                Log.d(TAG, "[DEATH] auto-revive complete (alive)")
                return true
            }
        }
        Log.w(TAG, "[DEATH] auto-revive wait timeout still dead")
        return false
    }
}
