package com.example.muamaizingbot.bot

import android.util.Log
import com.example.muamaizingbot.bot.loop.BotPriorityLoop
import com.example.muamaizingbot.capture.ScreenCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object BotWorker {

    private const val TAG = "BotWorker"
    private const val PAUSE_POLL_MS = 250L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var workerJob: Job? = null

    fun isAlive(): Boolean = workerJob?.isActive == true

    /** Kill any living job and start a fresh worker (optional startup navigation). */
    fun restartCold(runStartup: Boolean) {
        destroy()
        startInternal(runStartup = runStartup)
    }

    fun destroy() {
        val job = workerJob
        workerJob = null
        job?.cancel()
        Log.d(TAG, "[BOT] worker destroyed")
        BotDiagnosticJournal.record(TAG, "destroyed")
    }

    suspend fun stop() {
        workerJob?.cancelAndJoin()
        workerJob = null
    }

    @Deprecated("Use destroy() / restartCold()", ReplaceWith("destroy()"))
    fun stopAsync() {
        destroy()
    }

    private fun startInternal(runStartup: Boolean) {
        if (workerJob?.isActive == true) {
            Log.d(TAG, "[BOT] worker already running")
            return
        }

        workerJob = scope.launch {
            Log.d(TAG, "[BOT] worker started runStartup=$runStartup")
            BotDiagnosticJournal.record(TAG, "started runStartup=$runStartup")
            try {
                if (runStartup) {
                    if (!ensureCaptureReady("startup")) {
                        BotController.setError("Captura inactiva")
                        return@launch
                    }
                    if (BotPriorityLoop.runStartup() == BotPriorityLoop.IterationResult.ERROR) {
                        BotController.setError("Startup failed")
                        return@launch
                    }
                }

                var iteration = 0
                var consecutiveErrors = 0
                while (isActive) {
                    when (BotController.state.value) {
                        BotRuntimeState.IDLE -> {
                            Log.d(TAG, "[BOT] worker exit: IDLE")
                            break
                        }
                        BotRuntimeState.PAUSED,
                        BotRuntimeState.ERROR,
                        -> {
                            delay(PAUSE_POLL_MS)
                        }
                        BotRuntimeState.RUNNING -> {
                            if (!ensureCaptureReady("loop")) {
                                BotController.setError("Captura inactiva")
                                // setError parks us in ERROR; keep job alive for expire/resume.
                                continue
                            }
                            iteration++
                            Log.d(TAG, "[BOT] loop iteration=$iteration")
                            when (BotPriorityLoop.runIteration()) {
                                BotPriorityLoop.IterationResult.OK -> {
                                    consecutiveErrors = 0
                                }
                                BotPriorityLoop.IterationResult.ERROR -> {
                                    consecutiveErrors++
                                    BotDiagnosticJournal.record(
                                        TAG,
                                        "iteration ERROR count=$consecutiveErrors",
                                    )
                                    Log.w(TAG, "[BOT] loop error count=$consecutiveErrors")
                                    if (consecutiveErrors >= 3) {
                                        BotController.setError(
                                            "Bot loop failed after recovery attempts",
                                        )
                                        consecutiveErrors = 0
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                Log.d(TAG, "[BOT] worker stopped")
                BotDiagnosticJournal.record(TAG, "stopped")
            }
        }
    }

    private fun ensureCaptureReady(phase: String): Boolean {
        if (ScreenCaptureManager.isReady()) {
            return true
        }
        Log.e(
            TAG,
            "[BOT] capture not ready phase=$phase active=${ScreenCaptureManager.isActiveNow()} " +
                "hasFrame=${ScreenCaptureManager.hasFrame()}",
        )
        return false
    }
}
