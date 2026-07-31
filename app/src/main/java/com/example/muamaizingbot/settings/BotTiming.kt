package com.example.muamaizingbot.settings

import android.util.Log
import kotlinx.coroutines.delay

/**
 * Category-aware timing for bot waits.
 *
 * Prefer adaptive polls ([com.example.muamaizingbot.util.AdaptiveWait] /
 * waitForTemplate) for readiness. Use this to scale **timeout ceilings** and
 * remaining fixed settles so Fast devices do not pay Normal-era sleeps.
 *
 * Normal adds a conservative +1s on load/settle categories to avoid premature
 * timeouts that trigger double validations / retries. Fast scales the raw
 * baseline (without that pad).
 */
enum class BotTimingCategory {
    /** Max wait for template/OCR/UI readiness (map load, shop, map window, PK popup). */
    SCREEN_LOAD,

    /** Fixed sleeps after teleport / HUD settle (pre-wire, switch_wait). */
    FIXED_SETTLE,

    /** Short ack after a UI tap before re-checking templates. */
    POST_TAP,

    /** Game animation (cast VFX) — barely scaled. */
    ANIMATION,

    /** AdaptiveWait / waitForTemplate poll interval. */
    POLL,
}

object BotTiming {

    private const val TAG = "BotTiming"
    /** Extra headroom on Normal for screen-load ceilings and fixed settles. */
    private const val NORMAL_LOAD_PAD_MS = 1_000L

    fun mode(): BotSpeedMode = AppSettingsStore.botSpeed()

    fun ms(baseMs: Long, category: BotTimingCategory): Long {
        if (baseMs <= 0L) return baseMs
        val scaled = when (mode()) {
            BotSpeedMode.NORMAL -> when (category) {
                BotTimingCategory.SCREEN_LOAD,
                BotTimingCategory.FIXED_SETTLE,
                -> baseMs + NORMAL_LOAD_PAD_MS
                else -> baseMs
            }
            BotSpeedMode.FAST -> {
                val factor = factorFast(category)
                if (factor >= 0.999) baseMs
                else (baseMs * factor).toLong().coerceAtLeast(floorMs(category, baseMs))
            }
        }
        if (scaled != baseMs) {
            Log.d(
                TAG,
                "scale mode=${mode()} cat=$category base=${baseMs}ms → ${scaled}ms",
            )
        }
        return scaled
    }

    fun seconds(baseSeconds: Int, category: BotTimingCategory): Int {
        if (baseSeconds <= 0) return baseSeconds
        val scaledMs = ms(baseSeconds * 1000L, category)
        return ((scaledMs + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    suspend fun delay(baseMs: Long, category: BotTimingCategory) {
        delay(ms(baseMs, category))
    }

    private fun factorFast(category: BotTimingCategory): Double {
        return when (category) {
            BotTimingCategory.SCREEN_LOAD -> 0.60
            BotTimingCategory.FIXED_SETTLE -> 0.55
            BotTimingCategory.POST_TAP -> 0.70
            BotTimingCategory.ANIMATION -> 0.95
            BotTimingCategory.POLL -> 1.0
        }
    }

    /** Never shrink tiny waits into noise; keep a fraction of the baseline. */
    private fun floorMs(category: BotTimingCategory, baseMs: Long): Long {
        return when (category) {
            BotTimingCategory.SCREEN_LOAD -> {
                val minFloor = if (baseMs >= 3_000L) 1_500L else 400L
                (baseMs * 0.40).toLong().coerceAtLeast(minFloor).coerceAtMost(baseMs)
            }
            BotTimingCategory.FIXED_SETTLE -> {
                val minFloor = if (baseMs >= 2_000L) 700L else 300L
                (baseMs * 0.40).toLong().coerceAtLeast(minFloor).coerceAtMost(baseMs)
            }
            BotTimingCategory.POST_TAP ->
                (baseMs * 0.50).toLong().coerceAtLeast(100L).coerceAtMost(baseMs)
            BotTimingCategory.ANIMATION ->
                (baseMs * 0.85).toLong().coerceAtLeast(200L).coerceAtMost(baseMs)
            BotTimingCategory.POLL -> baseMs
        }
    }
}
