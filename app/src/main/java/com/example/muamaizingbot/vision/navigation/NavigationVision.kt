package com.example.muamaizingbot.vision.navigation

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.util.AdaptiveWait
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.input.InputController
import com.example.muamaizingbot.maps.SwipeCoords
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
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
        for (attempt in 1..maxAttempts) {
            val match = findTemplate(assetPath, threshold)
            if (match != null) {
                Log.d(
                    TAG,
                    "[VISION] found path=$assetPath score=${match.score} attempt=$attempt"
                )
                return match
            }
            Log.d(TAG, "[VISION] not found path=$assetPath attempt=$attempt/$maxAttempts")
            if (swipe != null && attempt < maxAttempts) {
                swipe(swipe)
                delay(MAP_LIST_SCROLL_WAIT_MS)
            }
        }
        Log.w(TAG, "[VISION] exhausted scroll path=$assetPath")
        return null
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
        return tap(match.centerX, match.centerY)
    }

    suspend fun tapTemplate(assetPath: String, threshold: Float, roi: Rect? = null): Boolean {
        val match = findTemplate(assetPath, threshold, roi) ?: return false
        return tapMatch(match)
    }

    suspend fun tap(x: Int, y: Int): Boolean {
        return suspendCancellableCoroutine { continuation ->
            InputController.tap(x, y) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
    }

    suspend fun swipe(coords: SwipeCoords): Boolean {
        return suspendCancellableCoroutine { continuation ->
            InputController.swipe(
                coords.x1,
                coords.y1,
                coords.x2,
                coords.y2,
                coords.durationMs,
            ) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
    }

    fun wireRowRegion(match: PcTemplateMatchResult): Rect {
        return Rect(
            maxOf(0, match.bestX - 40),
            maxOf(0, match.bestY - 30),
            match.bestX + match.templateWidth + 300,
            match.bestY + match.templateHeight + 80,
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
