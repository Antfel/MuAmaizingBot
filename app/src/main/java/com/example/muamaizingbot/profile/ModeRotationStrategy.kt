package com.example.muamaizingbot.profile

/**
 * How Farm ↔ Farm Bosses rotation decides the next segment.
 * Runtime wiring is separate; this is persisted for the profile UI.
 */
enum class ModeRotationStrategy {
    /** Finish one pass of configured boss maps, then rest on farm spot. */
    MAP_LAP,
    /** Wall-clock daily windows for farm vs bosses. */
    CLOCK,
    ;

    fun toStorage(): String = when (this) {
        MAP_LAP -> "map_lap"
        CLOCK -> "clock"
    }

    companion object {
        val DEFAULT: ModeRotationStrategy = MAP_LAP

        fun parse(raw: String?): ModeRotationStrategy {
            return when (raw?.trim()?.lowercase()) {
                "clock", "schedule", "horario" -> CLOCK
                "map_lap", "lap", "duration", "ciclos" -> MAP_LAP
                else -> DEFAULT
            }
        }
    }
}
