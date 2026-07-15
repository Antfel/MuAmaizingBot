package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.ProfileRepository

/**
 * Caps repeated elf-buff trips when the elf is missing/offline.
 *
 * After [MAX_FAILED_ATTEMPTS] completed seeks without the buff icon, seeking pauses for
 * [COOLDOWN_MS], then the attempt counter resets and the cycle repeats indefinitely.
 */
object ElfBuffSeekGate {

    private const val TAG = "ElfBuffSeek"
    private const val MAX_FAILED_ATTEMPTS = 3
    private const val COOLDOWN_MS = 60 * 60 * 1000L
    private const val SKIP_LOG_INTERVAL_MS = 60_000L

    @Volatile
    private var failedAttempts = 0

    @Volatile
    private var cooldownUntilMs = 0L

    @Volatile
    private var lastSkipLogMs = 0L

    fun shouldAttemptSeek(profile: BotProfile? = ProfileRepository.currentProfile.value): Boolean {
        if (!ProfileRepository.shouldSeekElfBuff(profile)) {
            return false
        }
        return isSeekAllowed()
    }

    fun isSeekAllowed(): Boolean {
        val now = System.currentTimeMillis()
        if (cooldownUntilMs > 0L && now < cooldownUntilMs) {
            if (now - lastSkipLogMs >= SKIP_LOG_INTERVAL_MS) {
                val remainMin = ((cooldownUntilMs - now + 59_999L) / 60_000L).coerceAtLeast(1)
                Log.d(TAG, "[ELF] seek paused (~${remainMin}m left after $MAX_FAILED_ATTEMPTS fails)")
                lastSkipLogMs = now
            }
            return false
        }
        if (cooldownUntilMs > 0L && now >= cooldownUntilMs) {
            Log.d(TAG, "[ELF] seek cooldown expired; attempts reset")
            cooldownUntilMs = 0L
            failedAttempts = 0
            lastSkipLogMs = 0L
        }
        return true
    }

    fun noteBuffPresent() {
        if (failedAttempts == 0 && cooldownUntilMs == 0L) {
            return
        }
        Log.d(TAG, "[ELF] buff present; clearing seek backoff")
        failedAttempts = 0
        cooldownUntilMs = 0L
        lastSkipLogMs = 0L
    }

    /** Completed trip to the elf that still left the buff missing. */
    fun noteSeekFailed() {
        failedAttempts++
        Log.w(TAG, "[ELF] seek without buff attempt=$failedAttempts/$MAX_FAILED_ATTEMPTS")
        if (failedAttempts < MAX_FAILED_ATTEMPTS) {
            return
        }
        cooldownUntilMs = System.currentTimeMillis() + COOLDOWN_MS
        failedAttempts = 0
        lastSkipLogMs = 0L
        Log.w(TAG, "[ELF] pausing elf seek for 1h")
    }
}
