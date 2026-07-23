package com.example.muamaizingbot.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "[INPUT] accessibility service connected")
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "[INPUT] accessibility service destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun performTap(x: Int, y: Int, onResult: (Boolean) -> Unit) {
        performTap(x, y, TAP_DURATION_MS, onResult)
    }

    fun performTap(x: Int, y: Int, durationMs: Long, onResult: (Boolean) -> Unit) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val hold = durationMs.coerceIn(30L, 500L)
        val accepted = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, hold))
                .build(),
            gestureCallback(onResult),
            null,
        )
        if (!accepted) {
            Log.w(TAG, "[INPUT] dispatchGesture rejected x=$x y=$y")
            onResult(false)
        }
    }

    /** Single-stroke drag; finger lifts automatically at the end of [durationMs]. */
    fun performSwipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long,
        onResult: (Boolean) -> Unit,
    ) {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val accepted = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build(),
            gestureCallback(onResult),
            null,
        )
        if (!accepted) {
            Log.w(TAG, "[INPUT] dispatchGesture rejected swipe ($x1,$y1)->($x2,$y2)")
            onResult(false)
        }
    }

    private fun gestureCallback(onResult: (Boolean) -> Unit) = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            onResult(true)
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            onResult(false)
        }
    }

    companion object {
        private const val TAG = "BotAccessibility"
        private const val TAP_DURATION_MS = 50L

        @Volatile
        var instance: BotAccessibilityService? = null
            private set

        val isConnected: Boolean
            get() = instance != null
    }
}
