package com.example.muamaizingbot.bot.navigation

/**
 * Short-lived, in-process memory of the last positively observed map.
 *
 * This may suppress map-window OCR when the current HUD read is garbage. It must
 * never be used as proof of farm-spot coordinates or survive a map-changing event.
 */
object TrustedCurrentMapMemory {

    const val DEFAULT_MAX_AGE_MS = 120_000L

    data class Snapshot(
        val mapId: String,
        val confirmedAtMs: Long,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    fun record(mapId: String, nowMs: Long = System.currentTimeMillis()) {
        if (mapId.isBlank()) return
        snapshot = Snapshot(mapId = mapId, confirmedAtMs = nowMs)
    }

    fun invalidate() {
        snapshot = null
    }

    fun trustedMapId(
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    ): String? {
        val current = snapshot ?: return null
        val ageMs = nowMs - current.confirmedAtMs
        return current.mapId.takeIf { ageMs in 0..maxAgeMs }
    }

    fun isTrusted(
        expectedMapId: String,
        nowMs: Long = System.currentTimeMillis(),
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    ): Boolean = trustedMapId(nowMs, maxAgeMs) == expectedMapId
}
