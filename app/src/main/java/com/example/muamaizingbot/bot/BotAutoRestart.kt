package com.example.muamaizingbot.bot

import android.util.Log
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
import java.util.ArrayDeque

/**
 * From [BotRuntimeState.ERROR], wait briefly then call [BotController.start] again
 * (same path as tapping Play). Caps attempts per rolling window so a hard failure
 * does not loop forever.
 */
object BotAutoRestart {

    private const val TAG = "BotAutoRestart"

    const val MAX_RESTARTS_PER_WINDOW = 3
    const val WINDOW_MS = 60 * 60 * 1000L
    const val DELAY_MS = 5_000L

    data class Status(
        val isPending: Boolean = false,
        val usedInWindow: Int = 0,
        val secondsLeft: Int = 0,
        val detail: String = "",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingJob: Job? = null
    private val restartAtMs = ArrayDeque<Long>()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun cancel(why: String = "cancelled") {
        val wasPending = pendingJob?.isActive == true
        pendingJob?.cancel()
        pendingJob = null
        if (wasPending || _status.value.isPending || _status.value.detail.isNotEmpty()) {
            _status.value = Status(usedInWindow = pruneAndCount())
            Log.d(TAG, "[BOT] auto-restart cancel why=$why")
        }
    }

    fun onError(reason: String) {
        pendingJob?.cancel()
        pendingJob = null

        if (reason.contains("Captura", ignoreCase = true)) {
            _status.value = Status(
                usedInWindow = pruneAndCount(),
                detail = "Sin auto-retry (captura)",
            )
            Log.w(TAG, "[BOT] auto-restart skipped: capture inactive")
            return
        }

        val used = pruneAndCount()
        if (used >= MAX_RESTARTS_PER_WINDOW) {
            _status.value = Status(
                usedInWindow = used,
                detail = "Auto-retry agotado ($used/$MAX_RESTARTS_PER_WINDOW)",
            )
            Log.w(TAG, "[BOT] auto-restart exhausted used=$used")
            return
        }

        val nextAttempt = used + 1
        pendingJob = scope.launch {
            var left = DELAY_MS
            while (left > 0L) {
                if (BotController.state.value != BotRuntimeState.ERROR) {
                    _status.value = Status(usedInWindow = pruneAndCount())
                    pendingJob = null
                    return@launch
                }
                val seconds = ((left + 999L) / 1000L).toInt().coerceAtLeast(1)
                _status.value = Status(
                    isPending = true,
                    usedInWindow = used,
                    secondsLeft = seconds,
                    detail = "Retry $nextAttempt/$MAX_RESTARTS_PER_WINDOW en ${seconds}s",
                )
                val step = 250L.coerceAtMost(left)
                delay(step)
                left -= step
            }

            if (BotController.state.value != BotRuntimeState.ERROR) {
                _status.value = Status(usedInWindow = pruneAndCount())
                pendingJob = null
                return@launch
            }
            if (!ScreenCaptureManager.isReady()) {
                _status.value = Status(
                    usedInWindow = used,
                    detail = "Retry falló (captura)",
                )
                Log.w(TAG, "[BOT] auto-restart aborted: capture not ready")
                pendingJob = null
                return@launch
            }

            restartAtMs.addLast(System.currentTimeMillis())
            val recorded = pruneAndCount()
            Log.i(
                TAG,
                "[BOT] auto-restart attempt=$nextAttempt/$MAX_RESTARTS_PER_WINDOW " +
                    "reason=$reason",
            )
            pendingJob = null
            _status.value = Status(
                usedInWindow = recorded,
                detail = "Reiniciando…",
            )
            BotController.start()
            if (BotController.state.value == BotRuntimeState.RUNNING) {
                _status.value = Status(usedInWindow = recorded)
            }
        }
    }

    private fun pruneAndCount(): Int {
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        while (restartAtMs.isNotEmpty() && restartAtMs.first() < cutoff) {
            restartAtMs.removeFirst()
        }
        return restartAtMs.size
    }
}
