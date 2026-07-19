package com.example.muamaizingbot.bot

import android.util.Log
import com.example.muamaizingbot.bot.actions.ActionQueue
import com.example.muamaizingbot.capture.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Play / Pause / Stop lifecycle.
 *
 * - **Play**: soft-resume living worker if pause &lt; [PAUSE_EXPIRE_MS]; otherwise cold start (startup).
 * - **Pause**: soft-stop worker loop (job stays alive). After [PAUSE_EXPIRE_MS], job is destroyed
 *   and flow auto-restarts (same as error expire).
 * - **Stop**: destroy worker immediately, [IDLE], no auto-restart.
 * - **Error**: dump diagnostics, enter ERROR (pause semantics), then expire → cold restart.
 */
object BotController {

    private const val TAG = "BotController"

    /** Soft pause window; beyond this Play/expire must cold-start. */
    const val PAUSE_EXPIRE_MS = 120_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(BotRuntimeState.IDLE)
    val state: StateFlow<BotRuntimeState> = _state.asStateFlow()

    @Volatile
    private var pausedAtMs: Long = 0L

    @Volatile
    private var coldStartRequired: Boolean = true

    @Volatile
    private var autoResumeOnExpire: Boolean = false

    private var expireJob: Job? = null

    fun start() {
        BotAutoRestart.cancel("start")
        cancelExpireWatchdog()
        if (!ScreenCaptureManager.isReady()) {
            Log.w(TAG, "[BOT] start blocked: capture inactive or no frame")
            BotDiagnosticJournal.record(TAG, "start blocked: capture inactive")
            return
        }

        val previous = _state.value
        val pauseAgeMs = pauseAgeMs()
        val workerAlive = BotWorker.isAlive()
        val softResume = (previous == BotRuntimeState.PAUSED || previous == BotRuntimeState.ERROR) &&
            workerAlive &&
            !coldStartRequired &&
            pauseAgeMs < PAUSE_EXPIRE_MS

        BotDiagnosticJournal.record(
            TAG,
            "play from=$previous soft=$softResume pauseAgeMs=$pauseAgeMs alive=$workerAlive",
        )

        _state.value = BotRuntimeState.RUNNING
        pausedAtMs = 0L
        autoResumeOnExpire = false

        if (softResume) {
            Log.d(TAG, "[BOT] soft resume (pause was ${pauseAgeMs}ms)")
            // Living worker is waiting on PAUSED; flipping to RUNNING continues the loop.
            return
        }

        coldStartRequired = false
        Log.d(TAG, "[BOT] cold start from=$previous")
        BotWorker.restartCold(runStartup = true)
    }

    fun pause() {
        val previous = _state.value
        if (previous != BotRuntimeState.RUNNING) {
            Log.d(TAG, "[BOT] pause ignored state=$previous")
            return
        }
        Log.d(TAG, "[BOT] pause (soft) from=$previous")
        BotDiagnosticJournal.record(TAG, "pause soft")
        BotAutoRestart.cancel("pause")
        enterSuspended(
            next = BotRuntimeState.PAUSED,
            autoResume = true,
            reason = "pause",
        )
    }

    fun stop() {
        val previous = _state.value
        Log.d(TAG, "[BOT] stop from=$previous")
        BotDiagnosticJournal.record(TAG, "stop")
        BotAutoRestart.cancel("stop")
        cancelExpireWatchdog()
        autoResumeOnExpire = false
        pausedAtMs = 0L
        coldStartRequired = true
        BotWorker.destroy()
        ActionQueue.clear()
        _state.value = BotRuntimeState.IDLE
    }

    fun setError(reason: String) {
        Log.e(TAG, "[BOT] error reason=$reason")
        BotDiagnosticJournal.record(TAG, "error: $reason")
        BotDiagnosticJournal.dumpError(reason)
        ActionQueue.clear()
        BotAutoRestart.cancel("error")
        // Pause semantics: keep worker alive briefly, then expire → cold restart.
        enterSuspended(
            next = BotRuntimeState.ERROR,
            autoResume = true,
            reason = reason,
        )
        BotAutoRestart.noteErrorScheduled(reason)
    }

    fun resetToIdle() {
        Log.d(TAG, "[BOT] reset to=${BotRuntimeState.IDLE}")
        BotDiagnosticJournal.record(TAG, "resetToIdle")
        BotAutoRestart.cancel("reset")
        cancelExpireWatchdog()
        autoResumeOnExpire = false
        pausedAtMs = 0L
        coldStartRequired = true
        BotWorker.destroy()
        ActionQueue.clear()
        _state.value = BotRuntimeState.IDLE
    }

    fun pauseAgeMs(): Long {
        if (pausedAtMs == 0L) return 0L
        val s = _state.value
        if (s != BotRuntimeState.PAUSED && s != BotRuntimeState.ERROR) return 0L
        return (System.currentTimeMillis() - pausedAtMs).coerceAtLeast(0L)
    }

    private fun enterSuspended(
        next: BotRuntimeState,
        autoResume: Boolean,
        reason: String,
    ) {
        cancelExpireWatchdog()
        pausedAtMs = System.currentTimeMillis()
        autoResumeOnExpire = autoResume
        _state.value = next
        // Soft: do not destroy worker — loop parks on PAUSED/ERROR.
        scheduleExpireWatchdog(reason)
    }

    private fun scheduleExpireWatchdog(reason: String) {
        val resume = autoResumeOnExpire
        expireJob = scope.launch {
            BotAutoRestart.beginExpireCountdown(PAUSE_EXPIRE_MS, reason, resume)
            var left = PAUSE_EXPIRE_MS
            while (left > 0L) {
                val s = _state.value
                if (s != BotRuntimeState.PAUSED && s != BotRuntimeState.ERROR) {
                    BotAutoRestart.cancel("state-changed")
                    return@launch
                }
                val step = 250L.coerceAtMost(left)
                delay(step)
                left -= step
                BotAutoRestart.tickExpireCountdown(left, reason, resume)
            }

            val s = _state.value
            if (s != BotRuntimeState.PAUSED && s != BotRuntimeState.ERROR) {
                return@launch
            }

            Log.w(TAG, "[BOT] pause/error expired (${PAUSE_EXPIRE_MS}ms) reason=$reason → destroy worker")
            BotDiagnosticJournal.record(TAG, "expire destroy reason=$reason autoResume=$resume")
            BotWorker.destroy()
            coldStartRequired = true
            ActionQueue.clear()

            if (!resume) {
                BotAutoRestart.cancel("expire-no-resume")
                return@launch
            }

            if (!BotAutoRestart.canAutoRestart()) {
                Log.w(TAG, "[BOT] auto-restart capped after expire reason=$reason")
                BotAutoRestart.markExhausted()
                return@launch
            }

            if (!ScreenCaptureManager.isReady()) {
                Log.w(TAG, "[BOT] expire resume skipped: capture not ready")
                BotAutoRestart.markCaptureBlocked()
                return@launch
            }

            BotAutoRestart.recordRestartAttempt()
            Log.i(TAG, "[BOT] expire → cold start reason=$reason")
            // Stay in ERROR/PAUSED until start() flips to RUNNING.
            start()
        }
    }

    private fun cancelExpireWatchdog() {
        expireJob?.cancel()
        expireJob = null
    }
}
