package com.example.muamaizingbot.vision.navigation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.util.AdaptiveWait
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.input.InputController
import com.example.muamaizingbot.maps.SwipeCoords
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.ScrollSettleWait
import com.example.muamaizingbot.vision.roi.ScaledRoi
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import kotlin.math.roundToInt
import com.example.muamaizingbot.vision.template.PcTemplateMatcher
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object NavigationVision {

    private const val TAG = "NavigationVision"
    private const val MAP_LIST_SCROLL_WAIT_MS = 1000L
    /** Avoid hanging forever if Accessibility never delivers gesture callback. */
    private const val GESTURE_AWAIT_TIMEOUT_MS = 2_500L

    suspend fun captureFrame(): Bitmap? = ScreenCaptureManager.getLatestBitmap()

    fun findOnFrame(
        frame: Bitmap,
        assetPath: String,
        threshold: Float,
        roi: Rect? = null,
    ): PcTemplateMatchResult? {
        val info = TemplateRepository.getByPath(assetPath)
        if (info == null) {
            Log.w(TAG, "[VISION] template missing path=$assetPath")
            return null
        }
        return PcTemplateMatcher.findTemplate(
            source = frame,
            template = info.bitmap,
            threshold = threshold,
            roi = roi,
            templateName = info.sourceName,
            category = info.category,
            circularMask = wantsCircularMask(assetPath),
        )
    }

    fun probeOnFrame(
        frame: Bitmap,
        assetPath: String,
        roi: Rect? = null,
    ): PcTemplateMatchResult {
        val info = TemplateRepository.getByPath(assetPath)
        if (info == null) {
            return emptyMatch(assetPath)
        }
        return PcTemplateMatcher.matchDebug(
            source = frame,
            template = info.bitmap,
            templateName = info.sourceName,
            category = info.category,
            roi = roi,
            circularMask = wantsCircularMask(assetPath),
        )
    }

    suspend fun findTemplate(
        assetPath: String,
        threshold: Float,
        roi: Rect? = null,
    ): PcTemplateMatchResult? {
        val frame = captureFrame() ?: return null
        return try {
            findOnFrame(frame, assetPath, threshold, roi)
        } finally {
            frame.recycle()
        }
    }

    suspend fun findTemplateWithScroll(
        assetPath: String,
        threshold: Float = 0.8f,
        maxAttempts: Int = 10,
        swipe: SwipeCoords? = null,
    ): PcTemplateMatchResult? {
        var bestScore = 0f

        for (attempt in 1..maxAttempts) {
            val frame = captureFrame()
            if (frame == null) {
                Log.w(TAG, "[VISION] no frame path=$assetPath attempt=$attempt")
                break
            }

            val roi = mapListRoi(swipe, frame.width, frame.height)
            val probe = try {
                probeOnFrame(frame, assetPath, roi)
            } finally {
                frame.recycle()
            }

            if (probe.score > bestScore) {
                bestScore = probe.score
            }

            Log.d(
                TAG,
                "[VISION] scroll probe path=${assetPath.substringAfterLast('/')} " +
                    "attempt=$attempt/$maxAttempts score=${"%.3f".format(probe.score)} " +
                    "best=${"%.3f".format(bestScore)}"
            )

            if (probe.score >= threshold) {
                Log.d(
                    TAG,
                    "[VISION] found path=$assetPath score=${probe.score} attempt=$attempt"
                )
                return probe
            }

            if (swipe != null && attempt < maxAttempts) {
                swipe(swipe)
                delay(MAP_LIST_SCROLL_WAIT_MS)
            }
        }

        Log.w(
            TAG,
            "[VISION] exhausted scroll path=$assetPath bestScore=${"%.3f".format(bestScore)} " +
                "needed=$threshold"
        )
        return null
    }

    fun mapListRoi(swipe: SwipeCoords?, screenWidth: Int, screenHeight: Int): Rect? {
        if (swipe == null) {
            return null
        }
        return ScrollSettleWait.listRegionRect(swipe, screenWidth, screenHeight)
    }

    /** Scrollable wire rows inside the channel popup (excludes title + enter button). */
    fun wirePopupListRoi(swipe: SwipeCoords?, screenWidth: Int, screenHeight: Int): Rect {
        val panel = if (swipe != null) {
            ScrollSettleWait.listRegionRect(swipe, screenWidth, screenHeight)
        } else {
            ScaledRoi.fromRefRect(680, 350, 1180, 700, screenWidth, screenHeight)
        }
        val rowTop = panel.top + (panel.height() * 0.12f).roundToInt()
        val rowBottom = panel.top + (panel.height() * 0.72f).roundToInt()
        return Rect(panel.left, rowTop, panel.right, rowBottom)
    }

    fun wirePopupListRoi(swipe: SwipeCoords?): Rect? {
        val size = ScreenCaptureManager.peekLatestBitmapSize() ?: return null
        return wirePopupListRoi(swipe, size.first, size.second)
    }

    fun mapListRoi(swipe: SwipeCoords?): Rect? {
        val size = ScreenCaptureManager.peekLatestBitmapSize() ?: return null
        return mapListRoi(swipe, size.first, size.second)
    }

    /** Lower strip of the wire popup where the Switch Line confirm button lives. */
    fun wirePopupEnterRoi(swipe: SwipeCoords?, screenWidth: Int, screenHeight: Int): Rect {
        if (swipe != null) {
            val panel = ScrollSettleWait.listRegionRect(swipe, screenWidth, screenHeight)
            val enterTop = panel.top + (panel.height() * 0.50f).roundToInt()
            return Rect(panel.left, enterTop, panel.right, panel.bottom)
        }
        return ScaledRoi.fromRefRect(680, 760, 1180, 850, screenWidth, screenHeight)
    }

    fun wirePopupEnterRoi(swipe: SwipeCoords?): Rect? {
        val size = ScreenCaptureManager.peekLatestBitmapSize() ?: return null
        return wirePopupEnterRoi(swipe, size.first, size.second)
    }

    suspend fun logBestScore(assetPath: String, roi: Rect? = null) {
        val frame = captureFrame() ?: run {
            Log.w(TAG, "[VISION] best score skipped path=$assetPath reason=no_frame")
            return
        }
        try {
            val probe = probeOnFrame(frame, assetPath, roi)
            val maskNote = if (wantsCircularMask(assetPath)) " circularMask=true" else ""
            Log.w(
                TAG,
                "[VISION] best score path=$assetPath score=${"%.3f".format(probe.score)} " +
                    "at=(${probe.bestX},${probe.bestY}) template=${probe.templateWidth}x${probe.templateHeight} " +
                    "frame=${frame.width}x${frame.height}$maskNote"
            )
        } finally {
            frame.recycle()
        }
    }

    suspend fun waitForTemplate(
        assetPath: String,
        threshold: Float,
        timeoutMs: Long,
        pollMs: Long = AdaptiveWait.POLL_MS,
        roi: Rect? = null,
    ): PcTemplateMatchResult? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val match = findTemplate(assetPath, threshold, roi)
            if (match != null) {
                return match
            }
            delay(pollMs)
        }
        return null
    }

    suspend fun waitUntilAbsent(
        assetPath: String,
        threshold: Float,
        timeoutMs: Long,
        pollMs: Long = AdaptiveWait.POLL_MS,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val match = findTemplate(assetPath, threshold)
            if (match == null) {
                return true
            }
            delay(pollMs)
        }
        return false
    }

    suspend fun tapMatch(match: PcTemplateMatchResult): Boolean {
        return tapScreen(match.centerX, match.centerY)
    }

    suspend fun tapTemplate(assetPath: String, threshold: Float, roi: Rect? = null): Boolean {
        val match = findTemplate(assetPath, threshold, roi) ?: return false
        return tapMatch(match)
    }

    /** Tap at reference coordinates (logical 2560×1440 → scaled to capture). */
    suspend fun tap(refX: Int, refY: Int, label: String? = null): Boolean {
        val (x, y) = RefCoords.scalePoint(refX, refY)
        return tapScreen(x, y, label = label)
    }

    /** Tap at absolute screen pixels (e.g. template match center). */
    suspend fun tapScreen(x: Int, y: Int, label: String? = null): Boolean {
        val ok = withTimeoutOrNull(GESTURE_AWAIT_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                InputController.tap(x, y, label = label) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
        }
        if (ok == null) {
            Log.w(TAG, "[VISION] tap timeout x=$x y=$y label=$label")
            return false
        }
        return ok
    }

    /** Longer press at absolute pixels (character focus / select). */
    suspend fun tapHoldScreen(x: Int, y: Int, durationMs: Long = 160L, label: String? = null): Boolean {
        val ok = withTimeoutOrNull(GESTURE_AWAIT_TIMEOUT_MS + durationMs) {
            suspendCancellableCoroutine { continuation ->
                InputController.tapHold(x, y, durationMs, label = label) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
        }
        if (ok == null) {
            Log.w(TAG, "[VISION] tapHold timeout x=$x y=$y label=$label")
            return false
        }
        return ok
    }

    suspend fun swipe(coords: SwipeCoords): Boolean {
        val scaled = RefCoords.scaleSwipe(coords)
        return swipeScreen(scaled.x1, scaled.y1, scaled.x2, scaled.y2, scaled.durationMs)
    }

    /** Swipe in absolute capture pixels (already scaled / derived from a template match). */
    suspend fun swipeScreen(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long = 300L,
    ): Boolean {
        val ok = withTimeoutOrNull(GESTURE_AWAIT_TIMEOUT_MS + durationMs) {
            suspendCancellableCoroutine { continuation ->
                InputController.swipe(x1, y1, x2, y2, durationMs) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
        }
        if (ok == null) {
            Log.w(TAG, "[VISION] swipe timeout ($x1,$y1)->($x2,$y2)")
            return false
        }
        return ok
    }

    /** Enter button strip under a wire-list ROI anchored to the Switch Channel title. */
    fun wirePopupEnterRoiFromList(listRoi: Rect): Rect {
        val (screenW, screenH) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        val top = minOf(screenH - 8, listRoi.bottom)
        val bottom = minOf(screenH, top + RefCoords.scaleY(180, screenH))
        return Rect(
            maxOf(0, listRoi.left - RefCoords.scaleX(40, screenW)),
            top,
            minOf(screenW, listRoi.right + RefCoords.scaleX(40, screenW)),
            bottom,
        )
    }

    fun wireRowRegion(match: PcTemplateMatchResult): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize() ?: RefCoords.activeScreenSize()
        return wireRowRegion(match, w, h)
    }

    fun wireRowRegion(match: PcTemplateMatchResult, screenWidth: Int, screenHeight: Int): Rect {
        val padLeft = RefCoords.scaleX(40, screenWidth)
        val padTop = RefCoords.scaleY(30, screenHeight)
        val padRight = RefCoords.scaleX(300, screenWidth)
        val padBottom = RefCoords.scaleY(80, screenHeight)
        return Rect(
            maxOf(0, match.bestX - padLeft),
            maxOf(0, match.bestY - padTop),
            match.bestX + match.templateWidth + padRight,
            match.bestY + match.templateHeight + padBottom,
        )
    }

    /**
     * Narrow strip immediately left of a modal row label — where the checkbox lives.
     * Scaled from 2560×1440 logical padding so it stays useful at 1280×720.
     */
    fun checkboxLeftOfRow(match: PcTemplateMatchResult): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize() ?: RefCoords.activeScreenSize()
        return checkboxLeftOfRow(match, w, h)
    }

    fun checkboxLeftOfRow(
        match: PcTemplateMatchResult,
        screenWidth: Int,
        screenHeight: Int,
    ): Rect {
        val padLeft = RefCoords.scaleX(90, screenWidth)
        val padTop = RefCoords.scaleY(20, screenHeight)
        val padBottom = RefCoords.scaleY(20, screenHeight)
        val gap = RefCoords.scaleX(8, screenWidth)
        return Rect(
            maxOf(0, match.bestX - padLeft),
            maxOf(0, match.bestY - padTop),
            maxOf(0, match.bestX - gap),
            minOf(screenHeight, match.bestY + match.templateHeight + padBottom),
        )
    }

    private fun wantsCircularMask(assetPath: String): Boolean {
        val name = assetPath.substringAfterLast('/')
        return name == "close_x.png" ||
            name == "greater_defense.png" ||
            name == "greater_damage.png"
    }

    private fun emptyMatch(assetPath: String): PcTemplateMatchResult {
        return PcTemplateMatchResult(
            score = 0f,
            bestX = 0,
            bestY = 0,
            templateWidth = 0,
            templateHeight = 0,
            templateName = assetPath.substringAfterLast('/'),
            category = "navigation",
        )
    }
}
