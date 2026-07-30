package com.example.muamaizingbot.bot.loop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BotPriorityLoopTest {

    @Test
    fun confirmsOffSpotOnlyAfterTwoValidReadsWhenSpotWasKnown() {
        assertFalse(
            BotPriorityLoop.shouldTreatAsOffSpot(
                lastSpotWasConfirmed = true,
                consecutiveValidOffSpotReads = 1,
            ),
        )
        assertTrue(
            BotPriorityLoop.shouldTreatAsOffSpot(
                lastSpotWasConfirmed = true,
                consecutiveValidOffSpotReads = 2,
            ),
        )
    }

    @Test
    fun startupWithoutSpotMemoryRemainsStrict() {
        assertTrue(
            BotPriorityLoop.shouldTreatAsOffSpot(
                lastSpotWasConfirmed = false,
                consecutiveValidOffSpotReads = 1,
            ),
        )
    }
}
