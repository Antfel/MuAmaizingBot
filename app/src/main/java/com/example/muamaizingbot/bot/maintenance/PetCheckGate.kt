package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.profile.BotProfile

/**
 * Interval gate for periodic pet validation while the bot runs.
 * Startup always validates once when [BotProfile.enablePet] is true; this gate
 * spaces follow-up Gear/Store checks by [BotProfile.petCheckIntervalMinutes].
 */
object PetCheckGate {

    private const val TAG = "PetCheckGate"

    @Volatile
    private var lastCheckAtMs = 0L

    fun shouldCheck(profile: BotProfile): Boolean {
        if (!profile.enablePet) {
            return false
        }
        if (lastCheckAtMs == 0L) {
            return true
        }
        val intervalMs = profile.petCheckIntervalMinutes
            .coerceIn(
                BotProfile.MIN_PET_CHECK_INTERVAL_MINUTES,
                BotProfile.MAX_PET_CHECK_INTERVAL_MINUTES,
            ) * 60_000L
        val elapsed = System.currentTimeMillis() - lastCheckAtMs
        return elapsed >= intervalMs
    }

    fun noteCheckDone() {
        lastCheckAtMs = System.currentTimeMillis()
        Log.d(TAG, "[PET] check gate updated lastCheckAt=$lastCheckAtMs")
    }

    fun reset() {
        lastCheckAtMs = 0L
        Log.d(TAG, "[PET] check gate reset")
    }
}
