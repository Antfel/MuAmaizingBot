package com.example.muamaizingbot.bot.bosses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossAliveVsDeadTest {

    @Test
    fun closeScoresDeferToRednessAlive() {
        // Ridax: alive=0.924 dead=0.960 — delta short, vivid red stays a hunt target.
        val d = BossMapHuntActions.decideAlive(0.924f, 0.960f, redness = 40f)
        assertEquals("rgb", d.path)
        assertTrue(d.keep)
    }

    @Test
    fun closeScoresDeferToRednessDead() {
        val d = BossMapHuntActions.decideAlive(0.976f, 0.964f, redness = 10f)
        assertEquals("rgb", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun largeDeltaDeadWins() {
        val d = BossMapHuntActions.decideAlive(0.70f, 0.96f, redness = 80f)
        assertEquals("score", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun largeDeltaAliveWins() {
        val d = BossMapHuntActions.decideAlive(0.95f, 0.80f, redness = 5f)
        assertEquals("score", d.path)
        assertTrue(d.keep)
    }

    @Test
    fun aliveMissAndDeadHighIsDead() {
        val d = BossMapHuntActions.decideAlive(null, 0.96f, redness = 80f)
        assertEquals("score", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun probeRoiIsSmallAndCentered() {
        val rect = BossMapHuntActions.probeRectPx(
            centerX = 533,
            centerY = 399,
            templateWidth = 40,
            templateHeight = 40,
            frameW = 1280,
            frameH = 720,
            padPx = 28,
        )
        assertEquals(533 - 20 - 28, rect[0])
        assertEquals(399 - 20 - 28, rect[1])
        assertEquals(40 + 56, rect[2])
        assertEquals(40 + 56, rect[3])
    }

    @Test
    fun mergeSlotsDedupsNearbyHits() {
        val a = com.example.muamaizingbot.vision.template.PcTemplateMatchResult(
            score = 0.97f,
            bestX = 513,
            bestY = 379,
            templateWidth = 40,
            templateHeight = 40,
            templateName = "boss_alive.png",
        )
        val deadSame = com.example.muamaizingbot.vision.template.PcTemplateMatchResult(
            score = 0.96f,
            bestX = 515,
            bestY = 381,
            templateWidth = 40,
            templateHeight = 40,
            templateName = "boss_dead.png",
        )
        val other = com.example.muamaizingbot.vision.template.PcTemplateMatchResult(
            score = 0.88f,
            bestX = 700,
            bestY = 200,
            templateWidth = 40,
            templateHeight = 40,
            templateName = "boss_dead.png",
        )
        val slots = BossMapHuntActions.mergeSlots(listOf(a, deadSame, other))
        assertEquals(2, slots.size)
        assertEquals(a.centerX, slots[0].centerX)
        assertEquals(other.centerX, slots[1].centerX)
    }
}
