package com.example.muamaizingbot.bot.loop

import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ModeRotationGateTest {

    @Before
    fun reset() {
        ModeRotationGate.resetMemory()
    }

    @Test
    fun mapLap_pendingGoesToFarm() {
        val rot = ModeRotationConfig(
            enabled = true,
            strategy = ModeRotationStrategy.MAP_LAP,
            segment = ModeRotationConfig.SEGMENT_BOSSES,
            lapCompletePending = true,
            restMinutes = 60,
        )
        assertEquals(
            BotMode.FARM,
            ModeRotationGate.desiredMapLapForTest(BotMode.FARM_BOSSES, rot, 0L),
        )
    }

    @Test
    fun mapLap_restNotDoneStaysFarm() {
        val rot = ModeRotationConfig(
            enabled = true,
            strategy = ModeRotationStrategy.MAP_LAP,
            segment = ModeRotationConfig.SEGMENT_REST,
            restMinutes = 60,
        )
        assertEquals(
            BotMode.FARM,
            ModeRotationGate.desiredMapLapForTest(BotMode.FARM, rot, 10 * 60_000L),
        )
    }

    @Test
    fun mapLap_restDoneGoesBosses() {
        val rot = ModeRotationConfig(
            enabled = true,
            strategy = ModeRotationStrategy.MAP_LAP,
            segment = ModeRotationConfig.SEGMENT_REST,
            restMinutes = 60,
        )
        assertEquals(
            BotMode.FARM_BOSSES,
            ModeRotationGate.desiredMapLapForTest(BotMode.FARM, rot, 60 * 60_000L),
        )
    }

    @Test
    fun clock_spotThenBosses() {
        val rot = ModeRotationConfig(
            enabled = true,
            strategy = ModeRotationStrategy.CLOCK,
            farmWindows = listOf("08:00"),
            bossesWindows = listOf("14:00"),
        )
        assertEquals(BotMode.FARM, ModeRotationGate.desiredClockAtMinutes(rot, 8 * 60))
        assertEquals(BotMode.FARM, ModeRotationGate.desiredClockAtMinutes(rot, 13 * 60 + 59))
        assertEquals(BotMode.FARM_BOSSES, ModeRotationGate.desiredClockAtMinutes(rot, 14 * 60))
        assertEquals(BotMode.FARM_BOSSES, ModeRotationGate.desiredClockAtMinutes(rot, 2 * 60))
    }

    @Test
    fun clock_overnightFarm() {
        val rot = ModeRotationConfig(
            enabled = true,
            strategy = ModeRotationStrategy.CLOCK,
            farmWindows = listOf("22:00"),
            bossesWindows = listOf("06:00"),
        )
        assertEquals(BotMode.FARM, ModeRotationGate.desiredClockAtMinutes(rot, 23 * 60))
        assertEquals(BotMode.FARM, ModeRotationGate.desiredClockAtMinutes(rot, 5 * 60 + 59))
        assertEquals(BotMode.FARM_BOSSES, ModeRotationGate.desiredClockAtMinutes(rot, 6 * 60))
        assertEquals(BotMode.FARM_BOSSES, ModeRotationGate.desiredClockAtMinutes(rot, 12 * 60))
    }

    @Test
    fun clock_missingTimes_noDesire() {
        val rot = ModeRotationConfig(
            enabled = true,
            strategy = ModeRotationStrategy.CLOCK,
            farmWindows = listOf("08:00"),
            bossesWindows = emptyList(),
        )
        assertNull(ModeRotationGate.desiredClockAtMinutes(rot, 10 * 60))
    }

    @Test
    fun clock_sameTime_prefersBosses() {
        val rot = ModeRotationConfig(
            enabled = true,
            strategy = ModeRotationStrategy.CLOCK,
            farmWindows = listOf("12:00"),
            bossesWindows = listOf("12:00"),
        )
        assertEquals(BotMode.FARM_BOSSES, ModeRotationGate.desiredClockAtMinutes(rot, 12 * 60))
    }
}
