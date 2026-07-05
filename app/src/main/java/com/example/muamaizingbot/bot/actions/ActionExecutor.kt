package com.example.muamaizingbot.bot.actions

import android.util.Log
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.input.InputController
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.template.PcTemplateMatcher
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

object ActionExecutor {

    private const val TAG = "ActionExecutor"

    suspend fun execute(action: BotAction): Boolean {
        return when (action) {
            is BotAction.Tap -> tap(action.x, action.y)
            is BotAction.Swipe -> swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs)
            is BotAction.Wait -> {
                Log.d(TAG, "[ACTION] wait durationMs=${action.durationMs}")
                delay(action.durationMs)
                true
            }
            is BotAction.TapTemplate -> tapTemplate(action.templateName, action.threshold, action.roi)
            BotAction.EnsureAutoMode -> GameActions.ensureAutoMode()
            BotAction.TapFarmSpot -> GameActions.tapFarmSpot()
        }
    }

    private suspend fun tap(refX: Int, refY: Int): Boolean {
        val (x, y) = RefCoords.scalePoint(refX, refY)
        val success = suspendCancellableCoroutine { continuation ->
            InputController.tap(x, y) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
        Log.d(TAG, "[ACTION] tap ref=($refX,$refY) screen=($x,$y) success=$success")
        return success
    }

    private suspend fun swipe(
        refX1: Int,
        refY1: Int,
        refX2: Int,
        refY2: Int,
        durationMs: Long
    ): Boolean {
        val (x1, y1) = RefCoords.scalePoint(refX1, refY1)
        val (x2, y2) = RefCoords.scalePoint(refX2, refY2)
        val success = suspendCancellableCoroutine { continuation ->
            InputController.swipe(x1, y1, x2, y2, durationMs) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
        Log.d(TAG, "[ACTION] swipe ($x1,$y1)->($x2,$y2) success=$success")
        return success
    }

    private suspend fun tapTemplate(
        templateName: String,
        threshold: Float,
        roi: android.graphics.Rect?
    ): Boolean {
        val templateInfo = TemplateRepository.getByName(templateName)
        if (templateInfo == null) {
            Log.w(TAG, "[ACTION] tapTemplate failed name=$templateName reason=missing")
            return false
        }

        val frame = ScreenCaptureManager.getLatestBitmap()
        if (frame == null) {
            Log.w(TAG, "[ACTION] tapTemplate failed name=$templateName reason=no_frame")
            return false
        }

        return try {
            val match = PcTemplateMatcher.findTemplate(
                source = frame,
                template = templateInfo.bitmap,
                threshold = threshold,
                templateName = templateInfo.sourceName,
                category = templateInfo.category,
                roi = roi
            )
            if (match == null) {
                Log.w(TAG, "[ACTION] tapTemplate not found name=$templateName threshold=$threshold")
                false
            } else {
                Log.d(
                    TAG,
                    "[ACTION] tapTemplate name=$templateName score=${match.score} " +
                        "tap=(${match.centerX},${match.centerY})"
                )
                tapScreen(match.centerX, match.centerY)
            }
        } finally {
            frame.recycle()
        }
    }

    private suspend fun tapScreen(x: Int, y: Int): Boolean {
        val success = suspendCancellableCoroutine { continuation ->
            InputController.tap(x, y) { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
        Log.d(TAG, "[ACTION] tapScreen x=$x y=$y success=$success")
        return success
    }
}
