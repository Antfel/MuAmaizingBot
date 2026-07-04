package com.example.muamaizingbot.bot.actions

import android.graphics.Rect

class ActionScope internal constructor() {

    internal val actions = mutableListOf<BotAction>()

    fun tap(x: Int, y: Int) {
        actions.add(BotAction.Tap(x, y))
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 250L) {
        actions.add(BotAction.Swipe(x1, y1, x2, y2, durationMs))
    }

    fun waitMs(durationMs: Long) {
        actions.add(BotAction.Wait(durationMs))
    }

    fun waitSeconds(seconds: Double) {
        waitMs((seconds * 1000).toLong())
    }

    fun tapTemplate(templateName: String, threshold: Float = 0.85f, roi: Rect? = null) {
        actions.add(BotAction.TapTemplate(templateName, threshold, roi))
    }

    fun ensureAutoMode() {
        actions.add(BotAction.EnsureAutoMode)
    }

    fun tapFarmSpot() {
        actions.add(BotAction.TapFarmSpot)
    }
}

object ActionRunner {

    suspend fun run(block: ActionScope.() -> Unit): Boolean {
        val scope = ActionScope().apply(block)
        return ActionQueue.executeSequence(scope.actions)
    }

    fun enqueue(block: ActionScope.() -> Unit) {
        val scope = ActionScope().apply(block)
        ActionQueue.enqueueAll(scope.actions)
    }
}
