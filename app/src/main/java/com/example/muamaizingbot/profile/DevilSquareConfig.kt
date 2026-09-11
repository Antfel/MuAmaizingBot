package com.example.muamaizingbot.profile

import org.json.JSONObject

data class DevilSquareConfig(
    val enabled: Boolean = false,
    val petType: PetType = PetType.DEFAULT,
    val buyPotions: Boolean = true,
    val hpPotionStacks: Int = DEFAULT_POTION_STACKS,
    val mpPotionStacks: Int = DEFAULT_POTION_STACKS,
    val prepElfBuff: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("pet_type", petType.toStorage())
        put("buy_potions", buyPotions)
        put("hp_potion_stacks", hpPotionStacks.coerceIn(MIN_POTION_STACKS, MAX_POTION_STACKS))
        put("mp_potion_stacks", mpPotionStacks.coerceIn(MIN_POTION_STACKS, MAX_POTION_STACKS))
        put("prep_elf_buff", prepElfBuff)
    }

    companion object {
        const val MIN_POTION_STACKS = 1
        const val MAX_POTION_STACKS = 99
        const val DEFAULT_POTION_STACKS = 3

        fun fromJson(json: JSONObject?): DevilSquareConfig {
            if (json == null) return DevilSquareConfig()
            return DevilSquareConfig(
                enabled = json.optBoolean("enabled", false),
                petType = PetType.parse(
                    json.optString("pet_type", PetType.DEFAULT.toStorage()),
                ),
                buyPotions = json.optBoolean("buy_potions", true),
                hpPotionStacks = json.optInt("hp_potion_stacks", DEFAULT_POTION_STACKS)
                    .coerceIn(MIN_POTION_STACKS, MAX_POTION_STACKS),
                mpPotionStacks = json.optInt("mp_potion_stacks", DEFAULT_POTION_STACKS)
                    .coerceIn(MIN_POTION_STACKS, MAX_POTION_STACKS),
                prepElfBuff = json.optBoolean("prep_elf_buff", true),
            )
        }
    }
}
