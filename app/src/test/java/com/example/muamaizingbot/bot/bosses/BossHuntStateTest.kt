package com.example.muamaizingbot.bot.bosses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BossHuntStateTest {

    @Before
    fun reset() {
        BossHuntState.reset()
    }

    @Test
    fun resetCycleKeepsKillCounter() {
        BossHuntState.markPostKill("corrupted_lands", 1)
        BossHuntState.markPostKill("corrupted_lands", 1)
        assertEquals(2, BossHuntState.bossesKilled.value)
        BossHuntState.mapIndex = 3
        BossHuntState.wireId = 4

        BossHuntState.resetCycle()

        assertEquals(2, BossHuntState.bossesKilled.value)
        assertEquals(0, BossHuntState.mapIndex)
        assertEquals(1, BossHuntState.wireId)
        assertEquals(BossHuntPhase.ENSURE_LOCATION, BossHuntState.phase)
        assertEquals(null, BossHuntState.checkpoint)
        assertNull(BossHuntState.mapLayout)
    }

    @Test
    fun resetClearsKillCounter() {
        BossHuntState.markPostKill("corrupted_lands", 1)
        BossHuntState.reset()
        assertEquals(0, BossHuntState.bossesKilled.value)
    }

    @Test
    fun layoutSurvivesWireChangeAndClearsOnMap() {
        val slots = listOf(
            HuntSlot(533, 399, 513, 379, 40, 40, "boss_alive.png"),
            HuntSlot(700, 300, 680, 280, 40, 40, "boss_dead.png"),
        )
        BossHuntState.mapLayout = MapHuntLayout("corrupted_lands", slots)
        BossHuntState.huntPlan = WireHuntPlan(
            mapId = "corrupted_lands",
            wireId = 1,
            icons = listOf(slots[0].toHuntIcon(0.97f)),
        )
        BossHuntState.wireId = 2
        BossHuntState.clearHuntPlan()

        assertNotNull(BossHuntState.mapLayout)
        assertEquals(2, BossHuntState.mapLayout?.slots?.size)
        assertEquals("corrupted_lands", BossHuntState.mapLayout?.mapId)
        assertNull(BossHuntState.huntPlan)

        BossHuntState.clearMapLayout()
        assertNull(BossHuntState.mapLayout)
        assertNull(BossHuntState.huntPlan)
    }

    @Test
    fun wirePlanConsumeThenExhaust() {
        val plan = WireHuntPlan(
            mapId = "corrupted_lands",
            wireId = 1,
            icons = listOf(
                HuntIcon(100, 100, 80, 80, 40, 40, 0.95f, "boss_alive"),
                HuntIcon(200, 200, 180, 180, 40, 40, 0.93f, "boss_alive"),
            ),
        )
        assertFalse(plan.isExhausted())
        plan.consume()
        assertEquals(1, plan.nextIndex)
        plan.consume()
        assertTrue(plan.isExhausted())
        plan.consume()
        assertTrue(plan.isExhausted())
    }

    @Test
    fun markPostKillExhaustsLastLive() {
        BossHuntState.huntPlan = WireHuntPlan(
            mapId = "corrupted_lands",
            wireId = 1,
            icons = listOf(HuntIcon(1, 1, 0, 0, 10, 10, 1f, "x")),
        )
        BossHuntState.markPostKill("corrupted_lands", 1)
        assertTrue(BossHuntState.huntPlan!!.isExhausted())
        assertEquals(1, BossHuntState.bossesKilled.value)
        assertEquals(1, BossHuntState.checkpoint?.wireId)
    }

    @Test
    fun resetCycleClearsHuntPlanAndLayout() {
        BossHuntState.mapLayout = MapHuntLayout(
            "corrupted_lands",
            listOf(HuntSlot(1, 1, 0, 0, 10, 10, "x")),
        )
        BossHuntState.huntPlan =
            WireHuntPlan("corrupted_lands", 1, listOf(HuntIcon(1, 1, 0, 0, 10, 10, 1f, "x")))
        BossHuntState.resetCycle()
        assertEquals(null, BossHuntState.huntPlan)
        assertEquals(null, BossHuntState.mapLayout)
    }
}
