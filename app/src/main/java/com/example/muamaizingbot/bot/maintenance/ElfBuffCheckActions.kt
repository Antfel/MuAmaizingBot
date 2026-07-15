package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.profile.ProfileRepository

object ElfBuffCheckActions {

    private const val TAG = "ElfBuff"
    private const val BUFF_ICON = "templates/mu/ui/common/elf_buff_icon.png"
    private const val BUFF_THRESHOLD = 0.55f

    /**
     * Template probe for the active buff icon. No-ops (returns true = "ok / don't seek")
     * when elf buff is disabled on the profile so callers never navigate for buff.
     */
    suspend fun hasElfBuff(): Boolean {
        if (!ProfileRepository.shouldSeekElfBuff()) {
            Log.d(TAG, "[ELF] hasBuff skipped (disabled or not configured)")
            return true
        }
        val active = NavigationVision.findTemplate(BUFF_ICON, BUFF_THRESHOLD) != null
        Log.d(TAG, "[ELF] hasBuff=$active")
        if (active) {
            ElfBuffSeekGate.noteBuffPresent()
        }
        return active
    }
}
