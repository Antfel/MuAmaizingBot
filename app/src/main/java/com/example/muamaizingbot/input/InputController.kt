package com.example.muamaizingbot.input

import android.util.Log
import com.example.muamaizingbot.accessibility.BotAccessibilityService

object InputController {

    private const val TAG = "InputController"

    fun tap(x: Int, y: Int, label: String? = null, onResult: (Boolean) -> Unit = {}) {
        val service = BotAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "[INPUT] tap failed x=$x y=$y reason=service_unavailable")
            onResult(false)
            return
        }
        service.performTap(x, y) { success ->
            Log.d(TAG, "[INPUT] tap x=$x y=$y success=$success")
            onResult(success)
        }
    }

    /** Longer press — used to select / focus a character in-game. */
    fun tapHold(
        x: Int,
        y: Int,
        durationMs: Long = 160L,
        label: String? = null,
        onResult: (Boolean) -> Unit = {},
    ) {
        val service = BotAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "[INPUT] tapHold failed x=$x y=$y reason=service_unavailable")
            onResult(false)
            return
        }
        service.performTap(x, y, durationMs) { success ->
            Log.d(TAG, "[INPUT] tapHold x=$x y=$y durationMs=$durationMs success=$success")
            onResult(success)
        }
    }

    fun swipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long = 250L,
        label: String? = null,
        onResult: (Boolean) -> Unit = {},
    ) {
        val service = BotAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "[INPUT] swipe failed reason=service_unavailable")
            onResult(false)
            return
        }
        service.performSwipe(x1, y1, x2, y2, durationMs) { success ->
            Log.d(TAG, "[INPUT] swipe ($x1,$y1)->($x2,$y2) durationMs=$durationMs success=$success")
            onResult(success)
        }
    }
}
