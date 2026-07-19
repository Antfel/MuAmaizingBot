package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.ScaledRoi
import kotlinx.coroutines.delay

/**
 * Dismiss Divine War / APEX summon popup before random world taps.
 *
 * Asset: [SUMMON_CLOSE] — crop the summon dialog close control at 1280×720 and replace
 * the placeholder when available.
 */
object ElfBuffSummonDismiss {

    private const val TAG = "ElfBuffWar"
    const val SUMMON_CLOSE = "templates/mu/ui/summon_close.png"
    private const val THRESHOLD = 0.74f
    private const val POST_DISMISS_MS = 350L

    /** Center dialog band @ REF 2560×1440 — avoids map/skill close_x false hits. */
    fun dialogRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = 700,
            top = 280,
            right = 1860,
            bottom = 1100,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    private fun roi(): Rect {
        val (w, h) = RefCoords.activeScreenSize()
        return dialogRoi(w, h)
    }

    /**
     * @return true if a summon close was tapped (caller should skip random tap this tick).
     */
    suspend fun dismissIfPresent(): Boolean {
        val match = NavigationVision.findTemplate(SUMMON_CLOSE, THRESHOLD, roi())
        if (match == null) {
            return false
        }
        Log.d(
            TAG,
            "[WAR] summon dismiss at=(${match.centerX},${match.centerY}) " +
                "score=${"%.3f".format(match.score)}",
        )
        val ok = NavigationVision.tapScreen(
            match.centerX,
            match.centerY,
            label = "summon_close",
        )
        if (ok) {
            delay(POST_DISMISS_MS)
        }
        return ok
    }
}
