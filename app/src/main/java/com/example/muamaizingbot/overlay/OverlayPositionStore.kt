package com.example.muamaizingbot.overlay

import android.content.Context

class OverlayPositionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Pair<Int, Int> {
        return prefs.getInt(KEY_X, DEFAULT_X) to prefs.getInt(KEY_Y, DEFAULT_Y)
    }

    fun save(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_X, x)
            .putInt(KEY_Y, y)
            .apply()
    }

    /** Clears saved drag position so next show uses default placement. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "overlay_position"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        // Legacy defaults — showOverlay always resets to screen-relative default.
        private const val DEFAULT_X = 0
        private const val DEFAULT_Y = 0
    }
}
