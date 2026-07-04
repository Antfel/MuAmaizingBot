package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.vision.navigation.NavigationVision

object PotionCheckActions {

    private const val TAG = "PotionCheck"
    private const val HP_OUT = "templates/mu/ui/hp_potion_out.png"
    private const val MP_OUT = "templates/mu/ui/mana_potion_out.png"
    private const val POTION_THRESHOLD = 0.96f

    suspend fun isHpPotionEmpty(): Boolean {
        return NavigationVision.findTemplate(HP_OUT, POTION_THRESHOLD) != null
    }

    suspend fun isManaPotionEmpty(): Boolean {
        return NavigationVision.findTemplate(MP_OUT, POTION_THRESHOLD) != null
    }

    suspend fun isAnyPotionEmpty(): Boolean {
        val empty = isHpPotionEmpty() || isManaPotionEmpty()
        Log.d(TAG, "[POTION] anyEmpty=$empty")
        return empty
    }
}
