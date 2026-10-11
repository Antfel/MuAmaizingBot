package com.example.muamaizingbot.profile

import com.example.muamaizingbot.R
import com.example.muamaizingbot.settings.UiStrings

/**
 * Start readiness for the active profile (P3 gates).
 * Blocks Play when the chosen style is missing its núcleo.
 */
object ProfileStartGate {

    data class Result(
        val ok: Boolean,
        val messageRes: Int? = null,
    ) {
        val message: String?
            get() = messageRes?.let { UiStrings.get(it) }
    }

    fun evaluate(
        profile: BotProfile? = ProfileRepository.currentProfile.value,
        hasFarmSpot: Boolean? = null,
    ): Result {
        if (profile == null) {
            return Result(ok = false, messageRes = R.string.start_gate_no_profile)
        }
        val rotation = profile.modeRotation
        val spotOk = hasFarmSpot ?: runCatching {
            LocationRepository.getFarmSpot(profile.filename) != null
        }.getOrDefault(false)
        val maps = profile.killBossesConfig.maps

        return when {
            rotation.enabled -> evaluateRotation(spotOk, maps, rotation)
            profile.isElfBuffPostMode() -> {
                if (spotOk) {
                    Result(ok = true)
                } else {
                    Result(ok = false, messageRes = R.string.start_gate_need_elf_post)
                }
            }
            profile.isFarmBossesMode() -> {
                if (maps.isNotEmpty()) {
                    Result(ok = true)
                } else {
                    Result(ok = false, messageRes = R.string.start_gate_need_boss_maps)
                }
            }
            else -> {
                if (spotOk) {
                    Result(ok = true)
                } else {
                    Result(ok = false, messageRes = R.string.start_gate_need_farm_spot)
                }
            }
        }
    }

    private fun evaluateRotation(
        hasSpot: Boolean,
        maps: List<String>,
        rotation: ModeRotationConfig,
    ): Result {
        if (!hasSpot) {
            return Result(ok = false, messageRes = R.string.start_gate_need_farm_spot)
        }
        if (maps.isEmpty()) {
            return Result(ok = false, messageRes = R.string.start_gate_need_boss_maps)
        }
        if (rotation.strategy == ModeRotationStrategy.CLOCK) {
            val farmOk = ModeRotationConfig.isValidHhMm(
                ModeRotationConfig.primaryTime(rotation.farmWindows),
            )
            val bossesOk = ModeRotationConfig.isValidHhMm(
                ModeRotationConfig.primaryTime(rotation.bossesWindows),
            )
            if (!farmOk || !bossesOk) {
                return Result(ok = false, messageRes = R.string.start_gate_need_schedule)
            }
        }
        return Result(ok = true)
    }
}
