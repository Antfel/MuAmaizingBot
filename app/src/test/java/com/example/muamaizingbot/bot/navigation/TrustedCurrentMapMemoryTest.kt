package com.example.muamaizingbot.bot.navigation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedCurrentMapMemoryTest {

    @After
    fun clearMemory() {
        TrustedCurrentMapMemory.invalidate()
    }

    @Test
    fun trustsOnlyTheRecordedMapWithinItsLifetime() {
        TrustedCurrentMapMemory.record("corrupted_lands", nowMs = 1_000L)

        assertTrue(
            TrustedCurrentMapMemory.isTrusted(
                "corrupted_lands",
                nowMs = 1_500L,
                maxAgeMs = 1_000L,
            ),
        )
        assertFalse(
            TrustedCurrentMapMemory.isTrusted(
                "noria",
                nowMs = 1_500L,
                maxAgeMs = 1_000L,
            ),
        )
        assertNull(
            TrustedCurrentMapMemory.trustedMapId(
                nowMs = 2_001L,
                maxAgeMs = 1_000L,
            ),
        )
    }

    @Test
    fun aNewConfirmationOverridesThePreviousMapAndInvalidateClearsIt() {
        TrustedCurrentMapMemory.record("noria", nowMs = 1_000L)
        TrustedCurrentMapMemory.record("corrupted_lands", nowMs = 2_000L)

        assertEquals(
            "corrupted_lands",
            TrustedCurrentMapMemory.trustedMapId(nowMs = 2_100L),
        )

        TrustedCurrentMapMemory.invalidate()
        assertNull(TrustedCurrentMapMemory.trustedMapId(nowMs = 2_100L))
    }
}
