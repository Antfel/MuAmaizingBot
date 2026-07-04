package com.example.muamaizingbot.util

import android.util.Log
import kotlinx.coroutines.delay

/** Poll-based waits that finish as soon as the UI is ready — no fixed sleeps. */
object AdaptiveWait {

    const val POLL_MS = 150L
    const val SCROLL_POLL_MS = 150L
    const val SCROLL_SETTLE_TIMEOUT_MS = 2500L
    const val UI_TIMEOUT_MS = 15_000L

    private const val TAG = "AdaptiveWait"

    suspend fun until(
        timeoutMs: Long = UI_TIMEOUT_MS,
        pollMs: Long = POLL_MS,
        label: String = "condition",
        condition: suspend () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            delay(pollMs)
        }
        Log.w(TAG, "timeout label=$label timeoutMs=$timeoutMs")
        return false
    }
}
