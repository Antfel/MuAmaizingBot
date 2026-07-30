package com.example.muamaizingbot.bot

import androidx.annotation.StringRes
import com.example.muamaizingbot.R

enum class BotRuntimeState {
    IDLE,
    RUNNING,
    PAUSED,
    ERROR;

    @StringRes
    fun labelRes(): Int = when (this) {
        IDLE -> R.string.home_bot_idle
        RUNNING -> R.string.home_bot_running
        PAUSED -> R.string.home_bot_paused
        ERROR -> R.string.home_bot_error
    }

    /** Debug / logs — keep English. */
    val label: String
        get() = when (this) {
            IDLE -> "Idle"
            RUNNING -> "Running"
            PAUSED -> "Paused"
            ERROR -> "Error"
        }
}
