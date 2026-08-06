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
    /**
     * When true, open-map navigation may use Random Teleport Seal on Far green paths.
     * Arrival wait shortens to 30s if at least one seal was used (else 90s).
     */
    val enableRandomTeleport: Boolean = true,
    /**
     * Green-path dots threshold: `dots >=` this value → Far (use Random);
     * `0 < dots <` this → Near (walk). Clamped to [MIN_RANDOM_FAR_MIN_DOTS, MAX_RANDOM_FAR_MIN_DOTS].
     */
    val randomTeleportFarMinDots: Int = DEFAULT_RANDOM_FAR_MIN_DOTS,
    /**
     * Farm / farm_bosses only: ensure PK mode, Focus enemies (red HUD), spam Attack,
     * then return to farm spot or stored boss coords.
     */
    val enableCombatFocus: Boolean = false,
    val combatFocusPkMode: CombatFocusPkMode = CombatFocusPkMode.DEFAULT,
    /**
     * When true, ensure the selected companion pet (Angel / Imp) is equipped:
     * inventory equip, else MU Coin Store purchase, at startup and every
     * [petCheckIntervalMinutes] while the bot runs.
     */
    val enablePet: Boolean = false,
    val petType: PetType = PetType.DEFAULT,
    /**
     * Minutes between periodic pet-slot checks while the bot is running.
     * Startup always validates once when [enablePet] is true.
     */
    val petCheckIntervalMinutes: Int = DEFAULT_PET_CHECK_INTERVAL_MINUTES,
    val farmEnabled: Boolean = true,
    /** Buff skill tap in logical 2560×1440 (giver mode). */
    val elfBuffSkillRefX: Int? = null,
    val elfBuffSkillRefY: Int? = null,
    /** Seconds between automatic casts while holding the post. */
    val elfBuffCastIntervalSec: Int = DEFAULT_ELF_CAST_INTERVAL_SEC,
    /** When false, only overlay "Cast" / force requests cast. */
    val elfBuffAutoCast: Boolean = true,
    val killBossesConfig: KillBossesConfig = KillBossesConfig(),
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
            put(
                "general_config",
                JSONObject().apply {
                    put("enable_elf_buff", enableElfBuff)
                    put("enable_random_teleport", enableRandomTeleport)
                    put(
                        "random_teleport_far_min_dots",
                        randomTeleportFarMinDots.coerceIn(
                            MIN_RANDOM_FAR_MIN_DOTS,
                            MAX_RANDOM_FAR_MIN_DOTS,
                        ),
                    )
                    put("enable_combat_focus", enableCombatFocus)
                    put("combat_focus_pk_mode", combatFocusPkMode.toStorage())
                    put("enable_pet", enablePet)
                    put("pet_type", petType.toStorage())
                    put(
                        "pet_check_interval_minutes",
                        petCheckIntervalMinutes.coerceIn(
                            MIN_PET_CHECK_INTERVAL_MINUTES,
                            MAX_PET_CHECK_INTERVAL_MINUTES,
                        ),
                    )
                },
            )
            put("farm_config", JSONObject().put("enabled", farmEnabled))
            put(
                "elf_giver_config",
                JSONObject().apply {
                    elfBuffSkillRefX?.let { put("skill_ref_x", it) }
                    elfBuffSkillRefY?.let { put("skill_ref_y", it) }
                    put(
                        "cast_interval_sec",
                        elfBuffCastIntervalSec.coerceIn(MIN_ELF_CAST_INTERVAL_SEC, MAX_ELF_CAST_INTERVAL_SEC),
                    )
                    put("auto_cast", elfBuffAutoCast)
                },
            )
            put("kill_bosses_config", killBossesConfig.toJson())
            put("map", map)
            put("wire", wire)
            put("spot", spot)
        }
    }

    companion object {
        const val DEFAULT_ELF_CAST_INTERVAL_SEC = 1
        const val MIN_ELF_CAST_INTERVAL_SEC = 1
        const val MAX_ELF_CAST_INTERVAL_SEC = 600

        const val DEFAULT_RANDOM_FAR_MIN_DOTS = 10
        const val MIN_RANDOM_FAR_MIN_DOTS = 3
        const val MAX_RANDOM_FAR_MIN_DOTS = 40

        const val DEFAULT_PET_CHECK_INTERVAL_MINUTES = 30
        const val MIN_PET_CHECK_INTERVAL_MINUTES = 1
        const val MAX_PET_CHECK_INTERVAL_MINUTES = 180

        fun fromJson(filename: String, json: JSONObject): BotProfile {
            val general = json.optJSONObject("general_config")
            val farm = json.optJSONObject("farm_config")
            val giver = json.optJSONObject("elf_giver_config")
            return BotProfile(
                filename = filename,
                displayName = json.optString("display_name").ifBlank { filename.removeSuffix(".json") },
                characterLevel = json.optInt("character_level").takeIf { json.has("character_level") && !json.isNull("character_level") },
                botMode = BotMode.normalize(json.optString("bot_mode", BotMode.FARM)),
                map = json.optString("map", ""),
                wire = json.optInt("wire", 1),
                spot = json.optString("spot", "spot_1"),
                hpPotionStacks = json.optInt("hp_potion_stacks", 10).coerceIn(1, 99),
                mpPotionStacks = json.optInt("mp_potion_stacks", 10).coerceIn(1, 99),
                enablePotionRecovery = json.optBoolean("enable_potion_recovery", true),
                enableElfBuff = general?.optBoolean("enable_elf_buff", true) ?: true,
                enableRandomTeleport = general?.optBoolean("enable_random_teleport", true) ?: true,
                randomTeleportFarMinDots = general
                    ?.optInt("random_teleport_far_min_dots", DEFAULT_RANDOM_FAR_MIN_DOTS)
                    ?.coerceIn(MIN_RANDOM_FAR_MIN_DOTS, MAX_RANDOM_FAR_MIN_DOTS)
                    ?: DEFAULT_RANDOM_FAR_MIN_DOTS,
                enableCombatFocus = general?.optBoolean("enable_combat_focus", false) ?: false,
                combatFocusPkMode = CombatFocusPkMode.parse(
                    general?.optString("combat_focus_pk_mode", CombatFocusPkMode.DEFAULT.toStorage()),
                ),
                enablePet = general?.optBoolean("enable_pet", false) ?: false,
                petType = PetType.parse(
                    general?.optString("pet_type", PetType.DEFAULT.toStorage()),
                ),
                petCheckIntervalMinutes = general
                    ?.optInt("pet_check_interval_minutes", DEFAULT_PET_CHECK_INTERVAL_MINUTES)
                    ?.coerceIn(MIN_PET_CHECK_INTERVAL_MINUTES, MAX_PET_CHECK_INTERVAL_MINUTES)
                    ?: DEFAULT_PET_CHECK_INTERVAL_MINUTES,
                farmEnabled = farm?.optBoolean("enabled", true) ?: true,
                elfBuffSkillRefX = giver?.optInt("skill_ref_x")
                    ?.takeIf { giver.has("skill_ref_x") && !giver.isNull("skill_ref_x") },
                elfBuffSkillRefY = giver?.optInt("skill_ref_y")
                    ?.takeIf { giver.has("skill_ref_y") && !giver.isNull("skill_ref_y") },
                elfBuffCastIntervalSec = giver
                    ?.optInt("cast_interval_sec", DEFAULT_ELF_CAST_INTERVAL_SEC)
                    ?.coerceIn(MIN_ELF_CAST_INTERVAL_SEC, MAX_ELF_CAST_INTERVAL_SEC)
                    ?: DEFAULT_ELF_CAST_INTERVAL_SEC,
                elfBuffAutoCast = giver?.optBoolean("auto_cast", true) ?: true,
                killBossesConfig = KillBossesConfig.fromJson(json.optJSONObject("kill_bosses_config")),
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
