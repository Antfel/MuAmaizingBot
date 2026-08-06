package com.example.muamaizingbot.profile

/**
 * Companion pet selected when Pet is enabled in the profile.
 * Storage values: `angel` | `imp`.
 */
enum class PetType {
    ANGEL,
    IMP,
    ;

    fun toStorage(): String = name.lowercase()

    companion object {
        val DEFAULT: PetType = ANGEL

        fun parse(raw: String?): PetType {
            return when (raw?.trim()?.lowercase()) {
                "angel" -> ANGEL
                "imp" -> IMP
                else -> DEFAULT
            }
        }
    }
}
