package com.example.muamaizingbot.ui.settings

import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAssistantApplierTest {

    @Test
    fun botMode_mapsStyles() {
        assertEquals(BotMode.FARM, ProfileAssistantApplier.botModeFor(AssistantStyle.FARM_ONLY, false))
        assertEquals(BotMode.FARM_BOSSES, ProfileAssistantApplier.botModeFor(AssistantStyle.BOSSES_ONLY, false))
        assertEquals(
            BotMode.FARM_BOSSES,
            ProfileAssistantApplier.botModeFor(AssistantStyle.FARM_BOSSES_ROTATION, false),
        )
        assertEquals(BotMode.ELF_BUFF_GIVER, ProfileAssistantApplier.botModeFor(AssistantStyle.ELF, false))
        assertEquals(BotMode.ELF_BUFF_WAR, ProfileAssistantApplier.botModeFor(AssistantStyle.ELF, true))
    }

    @Test
    fun modeRotation_onlyEnabledForRotationStyle() {
        val off = ProfileAssistantApplier.modeRotationFor(
            AssistantDraft(style = AssistantStyle.FARM_ONLY),
        )
        assertFalse(off.enabled)

        val on = ProfileAssistantApplier.modeRotationFor(
            AssistantDraft(
                style = AssistantStyle.FARM_BOSSES_ROTATION,
                rotationStrategy = ModeRotationStrategy.MAP_LAP,
                restMinutes = 45,
            ),
        )
        assertTrue(on.enabled)
        assertEquals(ModeRotationStrategy.MAP_LAP, on.strategy)
        assertEquals(45, on.restMinutes)
    }

    @Test
    fun coreReady_requiresSpotMapsAndSchedule() {
        val farm = AssistantDraft(style = AssistantStyle.FARM_ONLY)
        assertFalse(ProfileAssistantApplier.coreReady(farm, hasFarmSpot = false))
        assertTrue(ProfileAssistantApplier.coreReady(farm, hasFarmSpot = true))

        val bosses = AssistantDraft(
            style = AssistantStyle.BOSSES_ONLY,
            bossMapIds = listOf("eversong_forest"),
        )
        assertTrue(ProfileAssistantApplier.coreReady(bosses, hasFarmSpot = false))

        val rotation = AssistantDraft(
            style = AssistantStyle.FARM_BOSSES_ROTATION,
            bossMapIds = listOf("eversong_forest"),
            rotationStrategy = ModeRotationStrategy.CLOCK,
            farmWindow = "08:00",
            bossesWindow = "14:00",
        )
        assertFalse(ProfileAssistantApplier.coreReady(rotation, hasFarmSpot = false))
        assertTrue(ProfileAssistantApplier.coreReady(rotation, hasFarmSpot = true))

        val badClock = rotation.copy(farmWindow = "8")
        assertFalse(ProfileAssistantApplier.coreReady(badClock, hasFarmSpot = true))
        assertTrue(ModeRotationConfig.isValidHhMm("08:00"))
    }
}
