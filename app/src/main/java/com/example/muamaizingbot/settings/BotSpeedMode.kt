package com.example.muamaizingbot.settings

/**
 * App-wide bot timing profile (Sistema → Velocidad del bot).
 * Normal keeps current baseline delays; Fast shortens load/settle ceilings.
 */
enum class BotSpeedMode {
    NORMAL,
    FAST,
}
