package com.example.muamaizingbot.bot.bosses

import org.junit.Assert.assertTrue
import org.junit.Test

class BossIconRednessTest {

    @Test
    fun vividRedDiskIsAboveAliveFloor() {
        val pixels = filledArgb(width = 40, height = 40, r = 200, g = 20, b = 20)
        val redness = BossMapHuntActions.meanRednessArgb(pixels, 40, 40)
        assertTrue("redness=$redness", redness >= BossMapHuntActions.REDNESS_ALIVE_MIN)
    }

    @Test
    fun shadowedDiskIsBelowAliveFloor() {
        val pixels = filledArgb(width = 40, height = 40, r = 80, g = 70, b = 70)
        val redness = BossMapHuntActions.meanRednessArgb(pixels, 40, 40)
        assertTrue("redness=$redness", redness < BossMapHuntActions.REDNESS_ALIVE_MIN)
    }

    @Test
    fun emptyPixelsAreZero() {
        assertTrue(BossMapHuntActions.meanRednessArgb(intArrayOf(), 0, 0) == 0f)
    }

    @Test
    fun lightYellowDiskIsAboveGoldenAliveFloor() {
        val pixels = filledArgb(width = 40, height = 40, r = 220, g = 190, b = 40)
        val (_, yellow) = BossMapHuntActions.meanColorSignalsArgb(pixels, 40, 40)
        assertTrue("yellowness=$yellow", yellow >= BossMapHuntActions.YELLOWNESS_ALIVE_MIN)
    }

    @Test
    fun darkYellowDiskIsBelowGoldenAliveFloor() {
        val pixels = filledArgb(width = 40, height = 40, r = 70, g = 60, b = 45)
        val (_, yellow) = BossMapHuntActions.meanColorSignalsArgb(pixels, 40, 40)
        assertTrue("yellowness=$yellow", yellow < BossMapHuntActions.YELLOWNESS_ALIVE_MIN)
    }

    @Test
    fun redDiskIsNotLightYellow() {
        val pixels = filledArgb(width = 40, height = 40, r = 200, g = 20, b = 20)
        val (redness, yellow) = BossMapHuntActions.meanColorSignalsArgb(pixels, 40, 40)
        assertTrue("redness=$redness", redness >= BossMapHuntActions.REDNESS_ALIVE_MIN)
        assertTrue("yellowness=$yellow", yellow < BossMapHuntActions.YELLOWNESS_ALIVE_MIN)
    }

    private fun filledArgb(width: Int, height: Int, r: Int, g: Int, b: Int): IntArray {
        val color = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        return IntArray(width * height) { color }
    }
}
