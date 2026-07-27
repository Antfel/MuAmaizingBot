package com.example.muamaizingbot.bot.navigation

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.map.MapPathLengthVision
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlinx.coroutines.delay

/**
 * After a map destination tap, keep using map **Random** (Teleport Seal) while the
 * green path is Far (`dots >= 10`), until Near (`dots < 10`).
 *
 * Shared by all open-map navigation ([NavigationOrchestrator.tapVisualLocation],
 * Farm Bosses hunt, elf buff spots, etc.). War posts opt out.
 *
 * Active vs empty buttons: same alive/dead discrimination — empty → stop and walk
 * (purchase flow later). No usage cap: stop only on Near / empty / miss.
 */
object RandomSealActions {

    private const val TAG = "RandomSeal"

    const val MAP_RANDOM_BUTTON = "templates/mu/ui/common/map_random_button.png"
    const val MAP_RANDOM_BUTTON_EMPTY = "templates/mu/ui/common/map_random_button_empty.png"
    private const val RANDOM_THRESHOLD = 0.82f
    /** Same idea as [BossMapHuntActions] alive-over-dead; device deltas are tiny. */
    private const val ACTIVE_OVER_EMPTY_MARGIN = 0.0f

    /** Initial wait after destination tap / Random before first path sample. */
    private const val PATH_SETTLE_MS = 500L
    /** Poll until path dots appear (game paints green trail after tap/TP). */
    private const val PATH_WAIT_MS = 2_500L
    private const val PATH_POLL_MS = 200L
    /** Settle after Random so path re-paints from new position. */
    private const val POST_RANDOM_MS = 1_400L

    /** Walk-only arrival wait (no Random used this hop). */
    const val ARRIVAL_TIMEOUT_WALK_MS = 90_000L
    /** Arrival wait after at least one Random Teleport Seal this hop. */
    const val ARRIVAL_TIMEOUT_AFTER_RANDOM_MS = 30_000L

    fun arrivalTimeoutMs(sealsUsed: Int): Long =
        if (sealsUsed > 0) ARRIVAL_TIMEOUT_AFTER_RANDOM_MS else ARRIVAL_TIMEOUT_WALK_MS

    /**
     * While path is Far and Random is stocked, tap Random repeatedly until Near.
     * Map must stay open (caller closes after this returns).
     *
     * @return number of Random taps performed (0 if Near already / skipped / empty).
     */
    suspend fun maybeUseRandomIfFarPath(): Int {
        var sealsUsed = 0
        while (true) {
            val measure = waitForPathMeasure()
            when (measure.pathClass) {
                MapPathLengthVision.PathClass.NEAR -> {
                    Log.d(
                        TAG,
                        "[SEAL] path Near dots=${measure.dots} sealsUsed=$sealsUsed — done",
                    )
                    return sealsUsed
                }
                MapPathLengthVision.PathClass.UNKNOWN -> {
                    Log.w(
                        TAG,
                        "[SEAL] path not visible dots=${measure.dots} " +
                            "maskPix=${measure.maskPixels} sealsUsed=$sealsUsed — stop, walk",
                    )
                    return sealsUsed
                }
                MapPathLengthVision.PathClass.FAR -> {
                    Log.d(
                        TAG,
                        "[SEAL] path Far dots=${measure.dots} attempt=${sealsUsed + 1} — Random",
                    )
                }
            }

            val frame = NavigationVision.captureFrame()
            if (frame == null) {
                Log.w(TAG, "[SEAL] no frame for Random tap — stop, walk")
                return sealsUsed
            }
            val decision = try {
                resolveRandomTap(frame)
            } finally {
                frame.recycle()
            }

            when (decision.kind) {
                RandomDecision.Kind.TAP -> {
                    val match = decision.match ?: return sealsUsed
                    if (!NavigationVision.tapMatch(match)) {
                        Log.w(TAG, "[SEAL] Random tap failed — stop, walk")
                        return sealsUsed
                    }
                    sealsUsed++
                    Log.d(TAG, "[SEAL] Random tapped (stocked) sealsUsed=$sealsUsed")
                    delay(POST_RANDOM_MS)
                    // Loop: re-measure path from new position.
                }
                RandomDecision.Kind.EMPTY -> {
                    Log.d(
                        TAG,
                        "[SEAL] Random empty — stop seal (buy later); walk " +
                            "sealsUsed=$sealsUsed",
                    )
                    return sealsUsed
                }
                RandomDecision.Kind.MISS -> {
                    Log.w(TAG, "[SEAL] Random button miss — stop, walk sealsUsed=$sealsUsed")
                    return sealsUsed
                }
            }
        }
    }

    /** Wait for green path to paint; return last measure (Far/Near/Unknown). */
    private suspend fun waitForPathMeasure(): MapPathLengthVision.PathMeasure {
        delay(PATH_SETTLE_MS)
        val deadline = System.currentTimeMillis() + PATH_WAIT_MS
        var last = MapPathLengthVision.PathMeasure(0, MapPathLengthVision.PathClass.UNKNOWN)
        while (true) {
            val frame = NavigationVision.captureFrame()
            if (frame != null) {
                try {
                    last = MapPathLengthVision.measure(frame)
                    if (last.dots > 0) {
                        return last
                    }
                } finally {
                    frame.recycle()
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                return last
            }
            delay(PATH_POLL_MS)
        }
    }

    private data class RandomDecision(
        val kind: Kind,
        val match: PcTemplateMatchResult? = null,
    ) {
        enum class Kind { TAP, EMPTY, MISS }

        companion object {
            val EMPTY = RandomDecision(Kind.EMPTY)
            val MISS = RandomDecision(Kind.MISS)
            fun tap(match: PcTemplateMatchResult) = RandomDecision(Kind.TAP, match)
        }
    }

    /**
     * Find active Random in ROI; keep only if active score beats empty at the same patch.
     */
    private fun resolveRandomTap(frame: android.graphics.Bitmap): RandomDecision {
        val roi = randomButtonRoi(frame.width, frame.height)
        val activeHit = NavigationVision.findOnFrame(frame, MAP_RANDOM_BUTTON, RANDOM_THRESHOLD, roi)
        if (activeHit == null) {
            val activeProbe = NavigationVision.probeOnFrame(frame, MAP_RANDOM_BUTTON, roi)
            Log.w(
                TAG,
                "[SEAL] active miss best=${"%.3f".format(activeProbe.score)} " +
                    "at=(${activeProbe.bestX},${activeProbe.bestY})",
            )
            val emptyOnly = NavigationVision.findOnFrame(
                frame,
                MAP_RANDOM_BUTTON_EMPTY,
                RANDOM_THRESHOLD,
                roi,
            )
            if (emptyOnly != null) {
                Log.d(
                    TAG,
                    "[SEAL] active miss, empty hit score=${"%.3f".format(emptyOnly.score)} — EMPTY",
                )
                return RandomDecision.EMPTY
            }
            return RandomDecision.MISS
        }

        val pad = 6
        val local = Rect(
            (activeHit.bestX - pad).coerceAtLeast(0),
            (activeHit.bestY - pad).coerceAtLeast(0),
            (activeHit.bestX + activeHit.templateWidth + pad).coerceAtMost(frame.width),
            (activeHit.bestY + activeHit.templateHeight + pad).coerceAtMost(frame.height),
        )
        val empty = NavigationVision.probeOnFrame(frame, MAP_RANDOM_BUTTON_EMPTY, local)
        val activeWins = activeHit.score >= empty.score + ACTIVE_OVER_EMPTY_MARGIN
        Log.d(
            TAG,
            "[SEAL] active_vs_empty at=(${activeHit.centerX},${activeHit.centerY}) " +
                "active=${"%.3f".format(activeHit.score)} empty=${"%.3f".format(empty.score)} " +
                "tap=$activeWins",
        )
        return if (activeWins) {
            RandomDecision.tap(activeHit)
        } else {
            RandomDecision.EMPTY
        }
    }

    /** Bottom-right of parchment where Random sits @ 1280×720. */
    fun randomButtonRoi(
        frameWidth: Int = ScreenCaptureManager.peekLatestBitmapSize()?.first
            ?: RefCoords.activeScreenSize().first,
        frameHeight: Int = ScreenCaptureManager.peekLatestBitmapSize()?.second
            ?: RefCoords.activeScreenSize().second,
    ): Rect {
        val sx = frameWidth.toFloat() / 1280f
        val sy = frameHeight.toFloat() / 720f
        return Rect(
            (1040 * sx).toInt().coerceIn(0, frameWidth),
            (520 * sy).toInt().coerceIn(0, frameHeight),
            (1180 * sx).toInt().coerceIn(0, frameWidth),
            (680 * sy).toInt().coerceIn(0, frameHeight),
        )
    }
}
