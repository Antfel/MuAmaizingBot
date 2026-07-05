package com.example.muamaizingbot.bot.combat

import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.input.InputController
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.vision.coord.RefCoords
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

object GameActions {

    private const val TAG = "GameActions"
    private const val AUTO_TOGGLE_WAIT_MS = 2000L
    private const val AUTO_RETRY_DELAY_MS = 2000L
    private const val AUTO_MAX_ATTEMPTS = 6

    suspend fun ensureAutoMode(): Boolean {
        repeat(AUTO_MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                delay(AUTO_RETRY_DELAY_MS)
            }
            if (ensureAutoModeOnce()) {
                return true
            }
            Log.d(TAG, "[COMBAT] ensureAutoMode retry attempt=${attempt + 1}/$AUTO_MAX_ATTEMPTS")
        }
        Log.w(TAG, "[COMBAT] ensureAutoMode failed after $AUTO_MAX_ATTEMPTS attempts")
        return false
    }

    private suspend fun ensureAutoModeOnce(): Boolean {
        val frame = ScreenCaptureManager.getLatestBitmap()
        if (frame == null) {
            Log.w(TAG, "[COMBAT] ensureAutoMode failed reason=no_frame")
            return false
        }

        return try {
            val detection = AutoModeDetector.detect(frame)
            if (detection.isAutoMode) {
                Log.d(
                    TAG,
                    "[COMBAT] auto already active autoScore=${detection.autoScore} " +
                        "manualScore=${detection.manualScore}"
                )
                return true
            }

            val tapX = detection.manualTapX
            val tapY = detection.manualTapY
            if (tapX == null || tapY == null) {
                Log.w(
                    TAG,
                    "[COMBAT] ensureAutoMode failed reason=no_manual_match " +
                        "autoScore=${detection.autoScore} manualScore=${detection.manualScore}"
                )
                return false
            }

            Log.d(TAG, "[COMBAT] manual detected, tapping auto toggle at ($tapX,$tapY)")
            val tapped = suspendCancellableCoroutine { continuation ->
                InputController.tap(tapX, tapY) { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }
            if (!tapped) {
                Log.w(TAG, "[COMBAT] ensureAutoMode tap failed")
                return false
            }

            delay(AUTO_TOGGLE_WAIT_MS)

            val verifyFrame = ScreenCaptureManager.getLatestBitmap()
            if (verifyFrame == null) {
                Log.w(TAG, "[COMBAT] ensureAutoMode verify failed reason=no_frame")
                return false
            }

            try {
                val verified = AutoModeDetector.detect(verifyFrame)
                Log.d(
                    TAG,
                    "[COMBAT] ensureAutoMode result=${verified.isAutoMode} " +
                        "autoScore=${verified.autoScore} manualScore=${verified.manualScore}"
                )
                verified.isAutoMode
            } finally {
                verifyFrame.recycle()
            }
        } finally {
            frame.recycle()
        }
    }

    suspend fun tapFarmSpot(): Boolean {
        val spot = LocationRepository.farmSpot.value
        if (spot == null) {
            Log.w(TAG, "[NAV] tapFarmSpot failed reason=no_farm_spot")
            return false
        }
        Log.d(TAG, "[NAV] tapping farm spot map=${spot.map} wire=${spot.wire} ref=(${spot.x},${spot.y})")
        val (x, y) = RefCoords.scalePoint(spot.x, spot.y)
        return suspendCancellableCoroutine { continuation ->
            InputController.tap(x, y) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
    }
}
