package com.example.muamaizingbot.ui.settings

import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.FarmLocation
import com.example.muamaizingbot.profile.KillBossesConfig
import com.example.muamaizingbot.profile.ModeRotationConfig
import com.example.muamaizingbot.profile.ModeRotationStrategy
import com.example.muamaizingbot.profile.PetConfig
import com.example.muamaizingbot.profile.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSummaryModelTest {

    @Test
    fun farmOnly_showsSpotNotCiclosOrBosses() {
        val chips = ProfileSummaryModel.chips(
            profile = base(botMode = BotMode.FARM),
            farmSpot = spot(),
            labels = labels(),
        )
        assertEquals(listOf(ProfileSummaryChip.Kind.SPOT), chips.map { it.kind })
        assertNull(ProfileSummaryModel.ciclosFootnote(base(), "lap", "clock"))
    }

    @Test
    fun bossesOnly_showsBossesNotSpot() {
        val chips = ProfileSummaryModel.chips(
            profile = base(
                botMode = BotMode.FARM_BOSSES,
                killBossesConfig = KillBossesConfig(
                    maps = listOf("eversong_forest"),
                    pet = PetConfig(enablePet = true, petType = PetType.ANGEL),
                ),
            ),
            farmSpot = spot(),
            labels = labels(),
        )
        assertEquals(listOf(ProfileSummaryChip.Kind.BOSSES), chips.map { it.kind })
        assertTrue(chips.single().secondary!!.contains("Angel"))
    }

    @Test
    fun rotationMapLap_showsAllThreeAndFootnote() {
        val profile = base(
            botMode = BotMode.FARM,
            enablePet = true,
            petType = PetType.IMP,
            modeRotation = ModeRotationConfig(
                enabled = true,
                strategy = ModeRotationStrategy.MAP_LAP,
                restMinutes = 60,
            ),
            killBossesConfig = KillBossesConfig(
                maps = listOf("a", "b"),
                pet = PetConfig(enablePet = true, petType = PetType.ANGEL),
            ),
        )
        val chips = ProfileSummaryModel.chips(
            profile = profile,
            farmSpot = spot(map = "corrupted_lands", wire = 3),
            labels = labels(),
        )
        assertEquals(
            listOf(
                ProfileSummaryChip.Kind.CICLOS,
                ProfileSummaryChip.Kind.SPOT,
                ProfileSummaryChip.Kind.BOSSES,
            ),
            chips.map { it.kind },
        )
        assertEquals("Ciclos · 60m", chips[0].primary)
        assertTrue(chips[1].primary.contains("corrupted_lands w3") || chips[1].primary.contains("w3"))
        assertTrue(chips[1].secondary!!.contains("Imp"))
        assertEquals("Bosses: 2 mapa(s)", chips[2].primary)
        assertEquals(
            "lap-note",
            ProfileSummaryModel.ciclosFootnote(profile, "lap-note", "clock-note"),
        )
    }

    @Test
    fun shortMapWire_takesFirstWord() {
        assertEquals("Corrupted w3", ProfileSummaryModel.shortMapWire("Corrupted Lands", 3))
    }

    private data class Labels(
        val ciclosOffLabel: String = "",
        val ciclosMapLapLabel: (Int) -> String = { "Ciclos · ${it}m" },
        val ciclosClockLabel: String = "Horario",
        val spotUnset: String = "Spot: sin punto",
        val spotPrimary: (String) -> String = { "Spot: $it" },
        val petOff: String = "Pet off",
        val petLine: (String) -> String = { "Pet $it" },
        val bossesUnset: String = "Bosses: sin mapas",
        val bossesPrimary: (Int) -> String = { "Bosses: $it mapa(s)" },
        val petAngel: String = "Angel",
        val petImp: String = "Imp",
    )

    private fun ProfileSummaryModel.chips(
        profile: BotProfile?,
        farmSpot: FarmLocation?,
        labels: Labels,
    ) = chips(
        profile = profile,
        farmSpot = farmSpot,
        ciclosOffLabel = labels.ciclosOffLabel,
        ciclosMapLapLabel = labels.ciclosMapLapLabel,
        ciclosClockLabel = labels.ciclosClockLabel,
        spotUnset = labels.spotUnset,
        spotPrimary = labels.spotPrimary,
        petOff = labels.petOff,
        petLine = labels.petLine,
        bossesUnset = labels.bossesUnset,
        bossesPrimary = labels.bossesPrimary,
        petAngel = labels.petAngel,
        petImp = labels.petImp,
    )

    private fun labels() = Labels()

    private fun base(
        botMode: String = BotMode.FARM,
        enablePet: Boolean = false,
        petType: PetType = PetType.DEFAULT,
        modeRotation: ModeRotationConfig = ModeRotationConfig(),
        killBossesConfig: KillBossesConfig = KillBossesConfig(),
    ) = BotProfile(
        filename = "test.json",
        displayName = "Test",
        botMode = botMode,
        enablePet = enablePet,
        petType = petType,
        modeRotation = modeRotation,
        killBossesConfig = killBossesConfig,
    )

    private fun spot(map: String = "eversong_forest", wire: Int = 1) = FarmLocation(
        id = "spot1",
        profile = "test.json",
        name = "Spot",
        map = map,
        wire = wire,
        x = 100,
        y = 100,
    )
}
