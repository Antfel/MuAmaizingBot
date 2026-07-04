package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.vision.navigation.NavigationVision

object ElfBuffCheckActions {

    private const val TAG = "ElfBuff"
    private const val BUFF_ICON = "templates/mu/ui/common/elf_buff_icon.png"
    private const val BUFF_THRESHOLD = 0.55f

    suspend fun hasElfBuff(): Boolean {
        val active = NavigationVision.findTemplate(BUFF_ICON, BUFF_THRESHOLD) != null
        Log.d(TAG, "[ELF] hasBuff=$active")
        return active
    }
}
