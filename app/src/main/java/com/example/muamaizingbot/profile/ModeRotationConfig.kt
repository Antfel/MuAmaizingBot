package com.example.muamaizingbot.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Profile settings for automatic Farm ↔ Farm Bosses rotation ("Programación").
 */
data class ModeRotationConfig(
    val enabled: Boolean = false,
    val strategy: ModeRotationStrategy = ModeRotationStrategy.DEFAULT,
    /** Farm-spot rest after completing one boss-map lap (MAP_LAP). */
    val restMinutes: Int = DEFAULT_REST_MINUTES,
    /**
     * CLOCK: Spot / Bosses daily switch times as `HH:MM`
     * (legacy multi-window `HH:MM-HH:MM` still parsed for the start time).
     */
    val farmWindows: List<String> = emptyList(),
    val bossesWindows: List<String> = emptyList(),
    /** MAP_LAP: `bosses` while hunting, `rest` while cooling down on farm spot. */
    val segment: String = SEGMENT_BOSSES,
    /** Persisted RUNNING rest progress (MAP_LAP). */
    val restAccumulatedMs: Long = 0L,
    /** Set when a boss-map lap wraps; consumed on switch to farm rest. */
    val lapCompletePending: Boolean = false,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("enabled", enabled)
            put("strategy", strategy.toStorage())
            put(
                "rest_minutes",
                restMinutes.coerceIn(MIN_REST_MINUTES, MAX_REST_MINUTES),
            )
            put("farm_windows", JSONArray().apply { farmWindows.forEach { put(it) } })
            put("bosses_windows", JSONArray().apply { bossesWindows.forEach { put(it) } })
            put("segment", normalizeSegment(segment))
            put("rest_accumulated_ms", restAccumulatedMs.coerceAtLeast(0L))
            put("lap_complete_pending", lapCompletePending)
        }
    }

    companion object {
        const val DEFAULT_REST_MINUTES = 120
        const val MIN_REST_MINUTES = 15
        const val MAX_REST_MINUTES = 24 * 60

        const val SEGMENT_BOSSES = "bosses"
        const val SEGMENT_REST = "rest"

        fun normalizeSegment(raw: String?): String =
            when (raw?.trim()?.lowercase()) {
                SEGMENT_REST, "farm", "spot" -> SEGMENT_REST
                else -> SEGMENT_BOSSES
            }

        fun fromJson(json: JSONObject?): ModeRotationConfig {
            if (json == null) return ModeRotationConfig()
            return ModeRotationConfig(
                enabled = json.optBoolean("enabled", false),
                strategy = ModeRotationStrategy.parse(json.optString("strategy")),
                restMinutes = json.optInt("rest_minutes", DEFAULT_REST_MINUTES)
                    .coerceIn(MIN_REST_MINUTES, MAX_REST_MINUTES),
                farmWindows = readWindows(json.optJSONArray("farm_windows")),
                bossesWindows = readWindows(json.optJSONArray("bosses_windows")),
                segment = normalizeSegment(json.optString("segment", SEGMENT_BOSSES)),
                restAccumulatedMs = json.optLong("rest_accumulated_ms", 0L).coerceAtLeast(0L),
                lapCompletePending = json.optBoolean("lap_complete_pending", false),
            )
        }

        private fun readWindows(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val raw = arr.optString(i).trim()
                    if (raw.isNotEmpty()) add(raw)
                }
            }
        }

        /** Normalize multiline / comma text into window strings; keeps invalid lines for UI edit. */
        fun parseWindowsText(text: String): List<String> =
            text.lineSequence()
                .flatMap { it.split(',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()

        /** First stored clock time for UI (accepts `HH:MM` or legacy `HH:MM-HH:MM`). */
        fun primaryTime(windows: List<String>): String {
            val raw = windows.firstOrNull()?.trim().orEmpty()
            if (raw.isEmpty()) return ""
            val start = raw.substringBefore('-').trim()
            return if (isValidHhMm(start)) start else raw.take(5)
        }

        fun parseHhMmToMinutes(raw: String?): Int? {
            val start = raw?.trim()?.substringBefore('-')?.trim().orEmpty()
            if (!isValidHhMm(start)) return null
            val parts = start.split(':')
            return parts[0].toInt() * 60 + parts[1].toInt()
        }

        fun isValidHhMm(raw: String): Boolean {
            val m = HH_MM_REGEX.matchEntire(raw.trim()) ?: return false
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            return hour in 0..23 && minute in 0..59
        }

        /** Digits-only mask → `HH:MM` while typing. */
        fun formatHhMmInput(raw: String): String {
            val digits = raw.filter { it.isDigit() }.take(4)
            return when {
                digits.length <= 2 -> digits
                else -> digits.substring(0, 2) + ":" + digits.substring(2)
            }
        }

        private val HH_MM_REGEX = Regex("""^(\d{1,2}):(\d{2})$""")
    }
}
