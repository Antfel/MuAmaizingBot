package com.example.muamaizingbot.bot.bosses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossAliveVsDeadTest {

    @Test
    fun bossLightRedAlive() {
        val d = BossMapHuntActions.decideBossAlive(0.970f, 0.960f, redness = 40f)
        assertEquals("red", d.path)
        assertTrue(d.keep)
    }

    @Test
    fun bossDarkRedDeadEvenIfScoresClose() {
        val d = BossMapHuntActions.decideBossAlive(0.931f, 0.967f, redness = 18f)
        assertEquals("red", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun riddbDeadBoss648NotKept() {
        // Incident 17:05: dead>alive, redness 25.5, tap tpl=boss_dead.
        val d = BossMapHuntActions.decideBossAlive(0.931f, 0.967f, redness = 25.5f)
        assertEquals("red", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun bossDeadTemplateWinsNotKeptEvenIfSomewhatRed() {
        val d = BossMapHuntActions.decideBossAlive(0.926f, 0.965f, redness = 32f)
        assertEquals("red", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun bossLightRedAndAliveWins() {
        val d = BossMapHuntActions.decideBossAlive(0.945f, 0.939f, redness = 31f)
        assertEquals("red", d.path)
        assertTrue(d.keep)
    }

    @Test
    fun bossYellownessIgnored() {
        // Yellow must not veto a light-red boss when alive wins (families are separate).
        val d = BossMapHuntActions.decideBossAlive(
            0.970f,
            0.960f,
            redness = 32f,
            yellowness = 80f,
        )
        assertTrue(d.keep)
    }

    @Test
    fun goldenLightYellowAlive() {
        val d = BossMapHuntActions.decideGoldenAlive(0.95f, 0.92f, yellowness = 40f)
        assertEquals("yellow", d.path)
        assertTrue(d.keep)
    }

    @Test
    fun goldenDarkYellowDead() {
        val d = BossMapHuntActions.decideGoldenAlive(0.93f, 0.96f, yellowness = 12f)
        assertEquals("yellow", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun goldenRednessIgnored() {
        val d = BossMapHuntActions.decideGoldenAlive(
            0.93f,
            0.96f,
            yellowness = 40f,
            redness = 5f,
        )
        assertTrue(d.keep)
    }

    @Test
    fun largeDeltaDeadWins() {
        val d = BossMapHuntActions.decideBossAlive(0.70f, 0.96f, redness = 80f)
        assertEquals("score", d.path)
        assertFalse(d.keep)
    }

    @Test
    fun largeDeltaAliveWins() {
        val d = BossMapHuntActions.decideBossAlive(0.95f, 0.80f, redness = 5f)
        assertEquals("score", d.path)
        assertTrue(d.keep)
    }

    @Test
    fun aliveMissAndDeadHighIsDead() {
        val d = BossMapHuntActions.decideBossAlive(null, 0.96f, redness = 80f)
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
        val slots = BossMapHuntActions.mergeSlots(listOf(a, deadSame, other), HuntTargetKind.BOSS)
        assertEquals(2, slots.size)
        assertEquals(HuntTargetKind.BOSS, slots[0].kind)
        assertEquals(a.centerX, slots[0].centerX)
        assertEquals(other.centerX, slots[1].centerX)
    }

    @Test
    fun goldenAliveMissButLightYellowKept() {
        val d = BossMapHuntActions.decideGoldenAlive(
            null,
            0.88f,
            yellowness = 36f,
            redness = 22f,
        )
        assertEquals("yellow", d.path)
        assertTrue(d.keep)
    }

    @Test
    fun goldenColorFamilyYellowBeatsRed() {
        assertTrue(BossMapHuntActions.isGoldenColorFamily(36f, 22f))
        assertFalse(BossMapHuntActions.isGoldenColorFamily(22f, 32f))
        assertFalse(BossMapHuntActions.isGoldenColorFamily(20f, 10f))
    }

    @Test
    fun mergeBossAndGoldenPrefersGoldenOnClash() {
        val boss = HuntSlot(100, 100, 80, 80, 40, 40, "boss_dead.png", HuntTargetKind.BOSS)
        val gold = HuntSlot(105, 102, 85, 82, 40, 40, "golden_alive.png", HuntTargetKind.GOLDEN)
        val farBoss = HuntSlot(400, 400, 380, 380, 40, 40, "boss_alive.png", HuntTargetKind.BOSS)
        val merged = BossMapHuntActions.mergeBossAndGoldenSlots(listOf(boss, farBoss), listOf(gold))
        assertEquals(2, merged.size)
        assertEquals(HuntTargetKind.GOLDEN, merged[0].kind)
        assertEquals(HuntTargetKind.BOSS, merged[1].kind)
        assertEquals(400, merged[1].centerX)
    }
}
