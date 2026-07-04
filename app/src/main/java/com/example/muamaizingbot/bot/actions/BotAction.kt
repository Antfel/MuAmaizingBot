package com.example.muamaizingbot.bot.actions

import android.graphics.Rect

sealed class BotAction {

    data class Tap(val x: Int, val y: Int) : BotAction()

    data class Swipe(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val durationMs: Long = 250L
    ) : BotAction()

    data class Wait(val durationMs: Long) : BotAction()

    data class TapTemplate(
        val templateName: String,
        val threshold: Float = 0.85f,
        val roi: Rect? = null
    ) : BotAction()

    data object EnsureAutoMode : BotAction()

    data object TapFarmSpot : BotAction()
}
