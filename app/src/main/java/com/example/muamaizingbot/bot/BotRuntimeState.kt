package com.example.muamaizingbot.bot

enum class BotRuntimeState {
    IDLE,
    RUNNING,
    PAUSED,
    ERROR;

    val label: String
        get() = when (this) {
            IDLE -> "Idle"
            RUNNING -> "Running"
            PAUSED -> "Paused"
            ERROR -> "Error"
        }
}
