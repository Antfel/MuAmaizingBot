package com.example.muamaizingbot.profile

/**
 * Closed targeting-bar PK mode used by combat-focus defense (farm / farm_bosses).
 * Storage values: `peace` | `team` | `union` | `all`.
 */
enum class CombatFocusPkMode {
    PEACE,
    TEAM,
    UNION,
    ALL,
    ;

    fun toStorage(): String = name.lowercase()

    companion object {
        val DEFAULT: CombatFocusPkMode = ALL

        fun parse(raw: String?): CombatFocusPkMode {
            return when (raw?.trim()?.lowercase()) {
                "peace" -> PEACE
                "team" -> TEAM
                "union" -> UNION
                "all" -> ALL
                else -> DEFAULT
            }
        }
    }
}
