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

object NavigationVision {

    private const val TAG = "NavigationVision"
    private const val MAP_LIST_SCROLL_WAIT_MS = 1000L

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
            Log.w(
                TAG,
                "[VISION] best score path=$assetPath score=${"%.3f".format(probe.score)} " +
                    "at=(${probe.bestX},${probe.bestY}) template=${probe.templateWidth}x${probe.templateHeight} " +
                    "frame=${frame.width}x${frame.height}"
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

    /** Tap at reference coordinates (authored for 2560×1440). */
    suspend fun tap(refX: Int, refY: Int): Boolean {
        val (x, y) = RefCoords.scalePoint(refX, refY)
        return tapScreen(x, y)
    }

    /** Tap at absolute screen pixels (e.g. template match center). */
    suspend fun tapScreen(x: Int, y: Int): Boolean {
        return suspendCancellableCoroutine { continuation ->
            InputController.tap(x, y) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
    }

    suspend fun swipe(coords: SwipeCoords): Boolean {
        val scaled = RefCoords.scaleSwipe(coords)
        return suspendCancellableCoroutine { continuation ->
            InputController.swipe(
                scaled.x1,
                scaled.y1,
                scaled.x2,
                scaled.y2,
                scaled.durationMs,
            ) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
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
