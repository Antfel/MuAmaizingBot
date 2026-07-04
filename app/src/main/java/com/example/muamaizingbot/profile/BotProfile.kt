package com.example.muamaizingbot.profile

import org.json.JSONObject

data class BotProfile(
    val filename: String,
    val displayName: String,
    val characterLevel: Int? = null,
    val botMode: String = "farm",
    val map: String = "",
    val wire: Int = 1,
    val spot: String = "spot_1",
    val hpPotionStacks: Int = 10,
    val mpPotionStacks: Int = 10,
    val enablePotionRecovery: Boolean = true,
    val enableElfBuff: Boolean = true,
    val farmEnabled: Boolean = true,
) {
    val fileStem: String
        get() = filename.removeSuffix(".json")

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("display_name", displayName)
            characterLevel?.let { put("character_level", it) }
            put("bot_mode", botMode)
            put("hp_potion_stacks", hpPotionStacks.coerceIn(1, 99))
            put("mp_potion_stacks", mpPotionStacks.coerceIn(1, 99))
            put("enable_potion_recovery", enablePotionRecovery)
            put("enable_death_recovery", true)
            put("enable_auto_attack", true)
            put("general_config", JSONObject().put("enable_elf_buff", enableElfBuff))
            put("farm_config", JSONObject().put("enabled", farmEnabled))
            put(
                "kill_bosses_config",
                JSONObject()
                    .put("enabled", false)
                    .put("maps", org.json.JSONArray())
                    .put("include_golden_mobs", false)
            )
            put("map", map)
            put("wire", wire)
            put("spot", spot)
        }
    }

    companion object {
        fun fromJson(filename: String, json: JSONObject): BotProfile {
            val general = json.optJSONObject("general_config")
            val farm = json.optJSONObject("farm_config")
            return BotProfile(
                filename = filename,
                displayName = json.optString("display_name").ifBlank { filename.removeSuffix(".json") },
                characterLevel = json.optInt("character_level").takeIf { json.has("character_level") && !json.isNull("character_level") },
                botMode = json.optString("bot_mode", "farm"),
                map = json.optString("map", ""),
                wire = json.optInt("wire", 1),
                spot = json.optString("spot", "spot_1"),
                hpPotionStacks = json.optInt("hp_potion_stacks", 10).coerceIn(1, 99),
                mpPotionStacks = json.optInt("mp_potion_stacks", 10).coerceIn(1, 99),
                enablePotionRecovery = json.optBoolean("enable_potion_recovery", true),
                enableElfBuff = general?.optBoolean("enable_elf_buff", true) ?: true,
                farmEnabled = farm?.optBoolean("enabled", true) ?: true,
            )
        }

        fun defaultNew(filename: String, displayName: String): BotProfile {
            return BotProfile(
                filename = filename,
                displayName = displayName,
                map = "plain_of_four_winds_2",
                wire = 1,
            )
        }
    }
}
