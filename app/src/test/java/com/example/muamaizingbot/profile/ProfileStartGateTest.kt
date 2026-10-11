package com.example.muamaizingbot.profile

import com.example.muamaizingbot.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStartGateTest {

    @Test
    fun farmOnly_needsSpot() {
        val profile = BotProfile(filename = "t.json", displayName = "T", botMode = BotMode.FARM)
        assertFalse(ProfileStartGate.evaluate(profile, hasFarmSpot = false).ok)
        assertTrue(ProfileStartGate.evaluate(profile, hasFarmSpot = true).ok)
    }

    @Test
    fun bossesOnly_needsMaps() {
        val empty = BotProfile(
            filename = "t.json",
            displayName = "T",
            botMode = BotMode.FARM_BOSSES,
        )
        assertFalse(ProfileStartGate.evaluate(empty, hasFarmSpot = false).ok)

        val withMaps = empty.copy(
            killBossesConfig = KillBossesConfig(maps = listOf("eversong_forest")),
        )
        assertTrue(ProfileStartGate.evaluate(withMaps, hasFarmSpot = false).ok)
    }

    @Test
    fun rotation_needsSpotMapsAndClock() {
        val base = BotProfile(
            filename = "t.json",
            displayName = "T",
            botMode = BotMode.FARM_BOSSES,
            modeRotation = ModeRotationConfig(
                enabled = true,
                strategy = ModeRotationStrategy.MAP_LAP,
            ),
            killBossesConfig = KillBossesConfig(maps = listOf("a")),
        )
        assertFalse(ProfileStartGate.evaluate(base, hasFarmSpot = false).ok)
        assertTrue(ProfileStartGate.evaluate(base, hasFarmSpot = true).ok)

        val badClock = base.copy(
            modeRotation = ModeRotationConfig(
                enabled = true,
                strategy = ModeRotationStrategy.CLOCK,
                farmWindows = listOf("08:00"),
                bossesWindows = listOf("xx"),
            ),
        )
        assertFalse(ProfileStartGate.evaluate(badClock, hasFarmSpot = true).ok)
        assertEquals(
            R.string.start_gate_need_schedule,
            ProfileStartGate.evaluate(badClock, hasFarmSpot = true).messageRes,
        )
    }

    @Test
    fun elf_needsPost() {
        val profile = BotProfile(
            filename = "t.json",
            displayName = "T",
            botMode = BotMode.ELF_BUFF_GIVER,
        )
        assertFalse(ProfileStartGate.evaluate(profile, hasFarmSpot = false).ok)
        assertTrue(ProfileStartGate.evaluate(profile, hasFarmSpot = true).ok)
    }
}
