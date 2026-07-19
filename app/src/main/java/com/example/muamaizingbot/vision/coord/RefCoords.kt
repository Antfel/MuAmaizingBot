package com.example.muamaizingbot.vision.coord

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.SwipeCoords

/**
 * Screen taps/ROIs/swipes are still authored in [REF_WIDTH]×[REF_HEIGHT] logical space
 * (legacy map JSON + action constants). Physical capture contract is **1280×720 @ 240 DPI**;
 * coordinates scale proportionally to the live frame.
 *
 * After base templates are recaptured at 1280×720, REF and JSON can move to native 1280.
 */
object RefCoords {

    const val REF_WIDTH = 2560
    const val REF_HEIGHT = 1440

    /** Preferred emulator capture; used when no live frame is available. */
    const val TARGET_WIDTH = 1280
    const val TARGET_HEIGHT = 720

    fun activeScreenSize(): Pair<Int, Int> {
        ScreenCaptureManager.peekLatestBitmapSize()?.let { return it }
        return TARGET_WIDTH to TARGET_HEIGHT
    }

    fun activeScreenSize(frame: Bitmap): Pair<Int, Int> = frame.width to frame.height

    fun scaleX(refX: Int, screenWidth: Int = activeScreenSize().first): Int {
        return (refX.toLong() * screenWidth / REF_WIDTH).toInt()
    }

    fun scaleY(refY: Int, screenHeight: Int = activeScreenSize().second): Int {
        return (refY.toLong() * screenHeight / REF_HEIGHT).toInt()
    }

    fun scalePoint(refX: Int, refY: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        return scaleX(refX, screenWidth) to scaleY(refY, screenHeight)
    }

    fun scalePoint(refX: Int, refY: Int): Pair<Int, Int> {
        val (w, h) = activeScreenSize()
        return scalePoint(refX, refY, w, h)
    }

    fun scalePoint(refX: Int, refY: Int, frame: Bitmap): Pair<Int, Int> {
        return scalePoint(refX, refY, frame.width, frame.height)
    }

    fun unscaleX(screenX: Int, screenWidth: Int = activeScreenSize().first): Int {
        return (screenX.toLong() * REF_WIDTH / screenWidth).toInt()
    }

    fun unscaleY(screenY: Int, screenHeight: Int = activeScreenSize().second): Int {
        return (screenY.toLong() * REF_HEIGHT / screenHeight).toInt()
    }

    fun unscalePoint(screenX: Int, screenY: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        return unscaleX(screenX, screenWidth) to unscaleY(screenY, screenHeight)
    }

    fun unscalePoint(screenX: Int, screenY: Int): Pair<Int, Int> {
        val (w, h) = activeScreenSize()
        return unscalePoint(screenX, screenY, w, h)
    }

    fun scaleRect(refRect: Rect, screenWidth: Int, screenHeight: Int): Rect {
        return Rect(
            scaleX(refRect.left, screenWidth),
            scaleY(refRect.top, screenHeight),
            scaleX(refRect.right, screenWidth),
            scaleY(refRect.bottom, screenHeight),
        )
    }

    fun scaleSwipe(swipe: SwipeCoords, screenWidth: Int, screenHeight: Int): SwipeCoords {
        return SwipeCoords(
            x1 = scaleX(swipe.x1, screenWidth),
            y1 = scaleY(swipe.y1, screenHeight),
            x2 = scaleX(swipe.x2, screenWidth),
            y2 = scaleY(swipe.y2, screenHeight),
            durationMs = swipe.durationMs,
            maxAttempts = swipe.maxAttempts,
        )
    }

    fun scaleSwipe(swipe: SwipeCoords): SwipeCoords {
        val (w, h) = activeScreenSize()
        return scaleSwipe(swipe, w, h)
    }
}
