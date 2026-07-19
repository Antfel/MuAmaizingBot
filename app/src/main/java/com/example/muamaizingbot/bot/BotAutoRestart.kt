package com.example.muamaizingbot.bot

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Tracks auto-restart budget and overlay countdown while Pause/Error expire
 * ([BotController.PAUSE_EXPIRE_MS]) before a cold restart.
 */
object BotAutoRestart {

    private const val TAG = "BotAutoRestart"

    const val MAX_RESTARTS_PER_WINDOW = 3
    const val WINDOW_MS = 60 * 60 * 1000L

    data class Status(
        val isPending: Boolean = false,
        val usedInWindow: Int = 0,
        val secondsLeft: Int = 0,
        val detail: String = "",
    )

    private val restartAtMs = ArrayDeque<Long>()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun cancel(why: String = "cancelled") {
        if (_status.value.isPending || _status.value.detail.isNotEmpty()) {
            _status.value = Status(usedInWindow = pruneAndCount())
            Log.d(TAG, "[BOT] auto-restart cancel why=$why")
        }
    }

    fun canAutoRestart(): Boolean = pruneAndCount() < MAX_RESTARTS_PER_WINDOW

    fun recordRestartAttempt() {
        restartAtMs.addLast(System.currentTimeMillis())
        pruneAndCount()
    }

    fun noteErrorScheduled(reason: String) {
        Log.w(TAG, "[BOT] error → pause-expire scheduled reason=$reason")
    }

    fun beginExpireCountdown(totalMs: Long, reason: String, autoResume: Boolean) {
        val used = pruneAndCount()
        val seconds = ((totalMs + 999L) / 1000L).toInt().coerceAtLeast(1)
        _status.value = Status(
            isPending = autoResume,
            usedInWindow = used,
            secondsLeft = seconds,
            detail = if (autoResume) {
                "Retry en ${seconds}s"
            } else {
                "Expire en ${seconds}s"
            },
        )
        Log.d(TAG, "[BOT] expire countdown ${seconds}s autoResume=$autoResume reason=$reason")
    }

    fun tickExpireCountdown(leftMs: Long, reason: String, autoResume: Boolean) {
        if (!autoResume && !_status.value.isPending) {
            // Still update seconds for UI if we showed expire.
        }
        val used = pruneAndCount()
        val seconds = ((leftMs + 999L) / 1000L).toInt().coerceAtLeast(0)
        if (seconds <= 0) {
            _status.value = Status(
                isPending = autoResume,
                usedInWindow = used,
                secondsLeft = 0,
                detail = if (autoResume) "Reiniciando…" else "",
            )
            return
        }
        _status.value = Status(
            isPending = autoResume,
            usedInWindow = used,
            secondsLeft = seconds,
            detail = if (autoResume) {
                "Retry en ${seconds}s"
            } else {
                "Expire en ${seconds}s"
            },
        )
    }

    fun markExhausted() {
        val used = pruneAndCount()
        _status.value = Status(
            usedInWindow = used,
            detail = "Auto-retry agotado ($used/$MAX_RESTARTS_PER_WINDOW)",
        )
        Log.w(TAG, "[BOT] auto-restart exhausted used=$used")
    }

    fun markCaptureBlocked() {
        _status.value = Status(
            usedInWindow = pruneAndCount(),
            detail = "Retry falló (captura)",
        )
    }

    private fun pruneAndCount(): Int {
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        while (restartAtMs.isNotEmpty() && restartAtMs.first() < cutoff) {
            restartAtMs.removeFirst()
        }
        return restartAtMs.size
    }
}
