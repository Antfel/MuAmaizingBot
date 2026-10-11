package com.example.muamaizingbot.bot.maintenance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PotionEmptySlotsTest {

    @Test
    fun samePointBuysOnlyTheHigherScore() {
        // Linux emulator 2: both templates hit the empty mana bottle. MP 0.995 beats HP 0.927.
        val slots = PotionCheckActions.resolveEmptySlots(
            hpScore = 0.927f,
            hpCenterX = 607,
            hpCenterY = 685,
            mpScore = 0.995f,
            mpCenterX = 607,
            mpCenterY = 685,
        )
        assertFalse(slots.hpEmpty)
        assertTrue(slots.mpEmpty)
    }

    @Test
    fun twoPointsBuyBoth() {
        val slots = PotionCheckActions.resolveEmptySlots(
            hpScore = 0.960f,
            hpCenterX = 542,
            hpCenterY = 685,
            mpScore = 0.995f,
            mpCenterX = 607,
            mpCenterY = 685,
        )
        assertTrue(slots.hpEmpty)
        assertTrue(slots.mpEmpty)
    }

    @Test
    fun scoreUnderThresholdDoesNotCount() {
        val slots = PotionCheckActions.resolveEmptySlots(
            hpScore = 0.900f,
            hpCenterX = 607,
            hpCenterY = 685,
            mpScore = 0.995f,
            mpCenterX = 607,
            mpCenterY = 685,
        )
        assertFalse(slots.hpEmpty)
        assertTrue(slots.mpEmpty)
    }

    @Test
    fun tiedScoreOnTheSamePointBuysHpOnly() {
        val slots = PotionCheckActions.resolveEmptySlots(
            hpScore = 0.950f,
            hpCenterX = 540,
            hpCenterY = 680,
            mpScore = 0.950f,
            mpCenterX = 550,
            mpCenterY = 690,
        )
        assertTrue(slots.hpEmpty)
        assertFalse(slots.mpEmpty)
    }
}
