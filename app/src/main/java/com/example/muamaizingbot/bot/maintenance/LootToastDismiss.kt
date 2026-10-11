package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.settings.BotTiming
import com.example.muamaizingbot.settings.BotTimingCategory
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Loot toast on the right skill cluster. The item changes; the Use Now bar does not.
 * A hit taps the X at the top-right of that panel (offset measured at 1280×720).
 */
object LootToastDismiss {

    private const val TAG = "LootToast"
    private const val USE_NOW = "templates/mu/ui/loot_use_now.png"
    private const val THRESHOLD = 0.88f
    /** Close-X center minus the Use Now button center, at 1280×720. */
    private const val X_DX_1280 = 51
    private const val X_DY_720 = -123
    private const val SETTLE_MS = 300L

    suspend fun dismissIfPresent(): Boolean {
        val (w, h) = RefCoords.activeScreenSize()
        val roi = Rect(
            740 * w / 1280,
            360 * h / 720,
            1140 * w / 1280,
            700 * h / 720,
        )
        val match = NavigationVision.findTemplate(USE_NOW, THRESHOLD, roi) ?: return false
        val tapX = match.centerX + X_DX_1280 * w / 1280
        val tapY = match.centerY + X_DY_720 * h / 720
        Log.d(
            TAG,
            "[LOOT] use now score=${"%.3f".format(match.score)} → tap X at=($tapX,$tapY)",
        )
        val ok = NavigationVision.tapScreen(tapX, tapY, label = "loot_toast_x")
        if (ok) {
            delay(BotTiming.ms(SETTLE_MS, BotTimingCategory.POST_TAP))
        }
        return ok
    }
}
