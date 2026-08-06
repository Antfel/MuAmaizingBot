package com.example.muamaizingbot.bot.maintenance

import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.PetType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PetCheckGateTest {

    @Before
    fun resetGate() {
        PetCheckGate.reset()
    }

    @Test
    fun disabledPet_neverChecks() {
        val profile = sample(enablePet = false, intervalMin = 1)
        assertFalse(PetCheckGate.shouldCheck(profile))
    }

    @Test
    fun enabledPet_checksWhenNeverDone() {
        val profile = sample(enablePet = true, intervalMin = 30)
        assertTrue(PetCheckGate.shouldCheck(profile))
    }

    @Test
    fun afterNoteDone_waitsForInterval() {
        val profile = sample(enablePet = true, intervalMin = 30)
        PetCheckGate.noteCheckDone()
        assertFalse(PetCheckGate.shouldCheck(profile))
    }

    private fun sample(enablePet: Boolean, intervalMin: Int): BotProfile =
        BotProfile(
            filename = "test.json",
            displayName = "test",
            enablePet = enablePet,
            petType = PetType.ANGEL,
            petCheckIntervalMinutes = intervalMin,
        )
}
