package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.profile.BotProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interval / manual-trigger gate for elf buff casting (giver mode).
 * Pause/start does not clear the last-cast clock; overlay "Cast" forces the next cycle.
 */
object ElfBuffCastGate {

    private const val TAG = "ElfBuffCast"

    data class Status(
        val autoCastEnabled: Boolean = true,
        val hasSkillCoords: Boolean = false,
        val intervalSec: Int = BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC,
        val remainingMs: Long = 0L,
        val forcePending: Boolean = false,
        val lastCastAtMs: Long = 0L,
    ) {
        val isReady: Boolean
            get() = hasSkillCoords && (forcePending || remainingMs <= 0L)

        fun label(): String {
            if (!hasSkillCoords) return "Cast: sin map"
            if (forcePending) return "Cast: ya!"
            if (remainingMs > 0L) {
                val sec = ((remainingMs + 999L) / 1000L).coerceAtLeast(1)
                return "Cast: ${sec}s"
            }
            return if (autoCastEnabled) "Cast: listo" else "Cast: manual"
        }
    }

    @Volatile
    private var lastCastAtMs = 0L

    @Volatile
    private var forcePending = false

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun requestCastNow() {
        forcePending = true
        Log.d(TAG, "[ELF_GIVER] cast requested (manual)")
        publishStatus()
    }

    fun shouldCast(profile: BotProfile): Boolean {
        publishStatus(profile)
        if (!ElfBuffSkillMapper.isReady()) {
            return false
        }
        if (forcePending) {
            return true
        }
        if (!profile.elfBuffAutoCast) {
            return false
        }
        val intervalMs = profile.elfBuffCastIntervalSec
            .coerceIn(BotProfile.MIN_ELF_CAST_INTERVAL_SEC, BotProfile.MAX_ELF_CAST_INTERVAL_SEC) * 1000L
        val elapsed = System.currentTimeMillis() - lastCastAtMs
        return lastCastAtMs == 0L || elapsed >= intervalMs
    }

    fun noteCastDone() {
        lastCastAtMs = System.currentTimeMillis()
        forcePending = false
        Log.d(TAG, "[ELF_GIVER] cast gate updated lastCastAt=$lastCastAtMs")
        publishStatus()
    }

    fun refreshStatus(profile: BotProfile? = null) {
        publishStatus(profile)
    }

    fun reset() {
        lastCastAtMs = 0L
        forcePending = false
        Log.d(TAG, "[ELF_GIVER] cast gate reset")
        publishStatus()
    }

    private fun publishStatus(profile: BotProfile? = null) {
        val p = profile ?: com.example.muamaizingbot.profile.ProfileRepository.currentProfile.value
        val intervalSec = p?.elfBuffCastIntervalSec ?: BotProfile.DEFAULT_ELF_CAST_INTERVAL_SEC
        val intervalMs = intervalSec
            .coerceIn(BotProfile.MIN_ELF_CAST_INTERVAL_SEC, BotProfile.MAX_ELF_CAST_INTERVAL_SEC) * 1000L
        val remaining = if (lastCastAtMs == 0L) {
            0L
        } else {
            (lastCastAtMs + intervalMs - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        _status.value = Status(
            autoCastEnabled = p?.elfBuffAutoCast ?: true,
            hasSkillCoords = ElfBuffSkillMapper.isReady(),
            intervalSec = intervalSec,
            remainingMs = remaining,
            forcePending = forcePending,
            lastCastAtMs = lastCastAtMs,
        )
    }
}
