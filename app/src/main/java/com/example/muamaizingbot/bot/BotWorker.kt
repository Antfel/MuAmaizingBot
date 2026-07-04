package com.example.muamaizingbot.bot

import android.util.Log
import com.example.muamaizingbot.bot.loop.BotPriorityLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object BotWorker {

    private const val TAG = "BotWorker"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var workerJob: Job? = null

    fun start() {
        if (workerJob?.isActive == true) {
            Log.d(TAG, "[BOT] worker already running")
            return
        }

        workerJob = scope.launch {
            Log.d(TAG, "[BOT] worker started")
            try {
                if (BotPriorityLoop.runStartup() == BotPriorityLoop.IterationResult.ERROR) {
                    BotController.setError("Startup failed")
                    return@launch
                }

                var iteration = 0
                var consecutiveErrors = 0
                while (isActive && BotController.state.value == BotRuntimeState.RUNNING) {
                    iteration++
                    Log.d(TAG, "[BOT] loop iteration=$iteration")
                    when (BotPriorityLoop.runIteration()) {
                        BotPriorityLoop.IterationResult.OK -> {
                            consecutiveErrors = 0
                        }
                        BotPriorityLoop.IterationResult.ERROR -> {
                            consecutiveErrors++
                            Log.w(TAG, "[BOT] loop error count=$consecutiveErrors")
                            if (consecutiveErrors >= 3) {
                                BotController.setError("Bot loop failed after recovery attempts")
                                break
                            }
                        }
                    }
                }
            } finally {
                Log.d(TAG, "[BOT] worker stopped")
            }
        }
    }

    suspend fun stop() {
        workerJob?.cancelAndJoin()
        workerJob = null
    }

    fun stopAsync() {
        workerJob?.cancel()
        workerJob = null
    }
}
