package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Caps repeated elf-buff trips when the elf is missing/offline.
 *
 * After [MAX_FAILED_ATTEMPTS] completed seeks without the buff icon, seeking pauses for
 * [COOLDOWN_MS], then the attempt counter resets and the cycle repeats indefinitely.
 *
 * Pause/start of the bot does **not** clear this gate; use [reset] (overlay) or wait out cooldown.
 */
object ElfBuffSeekGate {

    private const val TAG = "ElfBuffSeek"
    private const val MAX_FAILED_ATTEMPTS = 3
    private const val COOLDOWN_MS = 60 * 60 * 1000L
    private const val SKIP_LOG_INTERVAL_MS = 60_000L

    data class Status(
        val failedAttempts: Int = 0,
        val maxAttempts: Int = MAX_FAILED_ATTEMPTS,
        val cooldownUntilMs: Long = 0L,
        val remainingCooldownMs: Long = 0L,
    ) {
        val isOnCooldown: Boolean
            get() = remainingCooldownMs > 0L

        fun label(): String {
            if (isOnCooldown) {
                val totalSec = ((remainingCooldownMs + 999L) / 1000L).coerceAtLeast(1)
                val min = totalSec / 60
                val sec = totalSec % 60
                return if (min > 0) "Elf: ${min}m ${sec.toString().padStart(2, '0')}s" else "Elf: ${sec}s"
            }
            if (failedAttempts > 0) {
                return "Elf: $failedAttempts/$maxAttempts"
            }
            return "Elf: ok"
        }
    }

    @Volatile
    private var failedAttempts = 0

    @Volatile
    private var cooldownUntilMs = 0L

    @Volatile
    private var lastSkipLogMs = 0L

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

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
            publishStatus(now)
            return false
        }
        if (cooldownUntilMs > 0L && now >= cooldownUntilMs) {
            Log.d(TAG, "[ELF] seek cooldown expired; attempts reset")
            cooldownUntilMs = 0L
            failedAttempts = 0
            lastSkipLogMs = 0L
        }
        publishStatus(now)
        return true
    }

    fun noteBuffPresent() {
        if (failedAttempts == 0 && cooldownUntilMs == 0L) {
            publishStatus()
            return
        }
        Log.d(TAG, "[ELF] buff present; clearing seek backoff")
        failedAttempts = 0
        cooldownUntilMs = 0L
        lastSkipLogMs = 0L
        publishStatus()
    }

    /** Completed trip to the elf that still left the buff missing. */
    fun noteSeekFailed() {
        failedAttempts++
        Log.w(TAG, "[ELF] seek without buff attempt=$failedAttempts/$MAX_FAILED_ATTEMPTS")
        if (failedAttempts < MAX_FAILED_ATTEMPTS) {
            publishStatus()
            return
        }
        cooldownUntilMs = System.currentTimeMillis() + COOLDOWN_MS
        failedAttempts = 0
        lastSkipLogMs = 0L
        Log.w(TAG, "[ELF] pausing elf seek for 1h")
        publishStatus()
    }

    /** Manual clear from overlay; does not change profile elf-buff toggle. */
    fun reset() {
        Log.d(TAG, "[ELF] seek gate reset (manual)")
        failedAttempts = 0
        cooldownUntilMs = 0L
        lastSkipLogMs = 0L
        publishStatus()
    }

    /** Refresh remaining cooldown for UI ticking. */
    fun refreshStatus() {
        publishStatus()
    }

    private fun publishStatus(now: Long = System.currentTimeMillis()) {
        val remaining = if (cooldownUntilMs > now) cooldownUntilMs - now else 0L
        _status.value = Status(
            failedAttempts = failedAttempts,
            maxAttempts = MAX_FAILED_ATTEMPTS,
            cooldownUntilMs = cooldownUntilMs,
            remainingCooldownMs = remaining,
        )
    }
}
