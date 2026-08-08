package com.example.muamaizingbot.profile

import org.json.JSONObject

/**
 * Companion pet settings shared by Farm (`general_config`) and Farm Bosses
 * (`kill_bosses_config`). Runtime picks the active one via
 * [BotProfile.effectivePetConfig].
 */
data class PetConfig(
    val enablePet: Boolean = false,
    val petType: PetType = PetType.DEFAULT,
    val petCheckIntervalMinutes: Int = BotProfile.DEFAULT_PET_CHECK_INTERVAL_MINUTES,
) {
    fun writeTo(json: JSONObject) {
        json.put("enable_pet", enablePet)
        json.put("pet_type", petType.toStorage())
        json.put(
            "pet_check_interval_minutes",
            petCheckIntervalMinutes.coerceIn(
                BotProfile.MIN_PET_CHECK_INTERVAL_MINUTES,
                BotProfile.MAX_PET_CHECK_INTERVAL_MINUTES,
            ),
        )
    }

    companion object {
        /**
         * Reads pet keys from [json]. When no pet keys are present, returns
         * [defaults] (used to migrate boss config from general pet).
         */
        fun fromJson(json: JSONObject?, defaults: PetConfig = PetConfig()): PetConfig {
            if (json == null) return defaults
            val hasPetKeys = json.has("enable_pet") ||
                json.has("pet_type") ||
                json.has("pet_check_interval_minutes")
            if (!hasPetKeys) return defaults
            return PetConfig(
                enablePet = json.optBoolean("enable_pet", defaults.enablePet),
                petType = PetType.parse(
                    json.optString("pet_type", defaults.petType.toStorage()),
                ),
                petCheckIntervalMinutes = json
                    .optInt(
                        "pet_check_interval_minutes",
                        defaults.petCheckIntervalMinutes,
                    )
                    .coerceIn(
                        BotProfile.MIN_PET_CHECK_INTERVAL_MINUTES,
                        BotProfile.MAX_PET_CHECK_INTERVAL_MINUTES,
                    ),
            )
        }
    }
}
