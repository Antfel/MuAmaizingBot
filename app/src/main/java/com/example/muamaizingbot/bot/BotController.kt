package com.example.muamaizingbot.bot

import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.bot.actions.ActionQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BotController {

    private const val TAG = "BotController"

    private val _state = MutableStateFlow(BotRuntimeState.IDLE)
    val state: StateFlow<BotRuntimeState> = _state.asStateFlow()

    fun start() {
        if (!ScreenCaptureManager.isReady()) {
            Log.w(TAG, "[BOT] start blocked: capture inactive or no frame")
            return
        }
        val previous = _state.value
        val next = when (previous) {
            BotRuntimeState.IDLE,
            BotRuntimeState.PAUSED,
            BotRuntimeState.ERROR -> BotRuntimeState.RUNNING
            BotRuntimeState.RUNNING -> BotRuntimeState.RUNNING
        }
        if (next != previous) {
            Log.d(TAG, "[BOT] start from=$previous to=$next")
            _state.value = next
            BotWorker.start()
        } else {
            Log.d(TAG, "[BOT] start ignored state=$previous")
        }
    }

    fun pause() {
        val previous = _state.value
        if (previous != BotRuntimeState.RUNNING) {
            Log.d(TAG, "[BOT] pause ignored state=$previous")
            return
        }
        Log.d(TAG, "[BOT] pause from=$previous to=${BotRuntimeState.PAUSED}")
        BotWorker.stopAsync()
        ActionQueue.clear()
        _state.value = BotRuntimeState.PAUSED
    }

    fun setError(reason: String) {
        Log.e(TAG, "[BOT] error reason=$reason")
        _state.value = BotRuntimeState.ERROR
    }

    fun resetToIdle() {
        Log.d(TAG, "[BOT] reset to=${BotRuntimeState.IDLE}")
        BotWorker.stopAsync()
        _state.value = BotRuntimeState.IDLE
    }
}
