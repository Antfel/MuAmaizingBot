package com.example.muamaizingbot.vision.map

import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.MapDefinitionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentMapOcrTest {

    @Test
    fun matchesExactSiblingNames() {
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Plain of Four Winds 2",
                "Plain of Four Winds 2",
            ),
        )
        assertFalse(
            CurrentMapOcr.matchesExpected(
                "Plain of Four Winds 2",
                "Plain of Four Winds 1",
            ),
        )
        assertFalse(
            CurrentMapOcr.matchesExpected(
                "Plain of Four Winds 1",
                "Plain of Four Winds 2",
            ),
        )
    }

    @Test
    fun matchesRaklionAndKalimaFloors() {
        assertTrue(CurrentMapOcr.matchesExpected("Raklion 3", "Raklion 3"))
        assertFalse(CurrentMapOcr.matchesExpected("Raklion 3", "Raklion 1"))
        assertTrue(CurrentMapOcr.matchesExpected("Temple of Kalima 9", "Temple of Kalima 9"))
        assertFalse(CurrentMapOcr.matchesExpected("Temple of Kalima 9", "Temple of Kalima 8"))
    }

    @Test
    fun matchesDivineRealmWithoutFloorNumber() {
        assertTrue(CurrentMapOcr.matchesExpected("Divine Realm", "Divine Realm"))
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Divine Realm( fWirel",
                "Divine Realm",
            ),
        )
    }

    @Test
    fun toleratesOneCharacterHudTypoForFloorlessMap() {
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Corrupled Lands Wirel",
                "Corrupted Lands",
            ),
        )
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Corrupled Lands NAire11",
                "Corrupted Lands",
            ),
        )
        assertFalse(CurrentMapOcr.matchesExpected("Corrupted Sand", "Corrupted Lands"))
    }

    @Test
    fun toleratesHudJunkButNotWrongDigit() {
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Plain of Four Winds 2)",
                "Plain of Four Winds 2",
            ),
        )
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Plain of Four Winds 2 f",
                "Plain of Four Winds 2",
            ),
        )
        assertFalse(
            CurrentMapOcr.matchesExpected(
                "Plain of Four Winds 2-6Switch",
                "Plain of Four Winds 1",
            ),
        )
        // Switch bleed stripped → still Plains 2
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Plain of Four Winds 2-6Switch",
                "Plain of Four Winds 2",
            ),
        )
    }

    @Test
    fun rejectsLongerDigitPrefix() {
        assertFalse(CurrentMapOcr.matchesExpected("Raklion 11", "Raklion 1"))
        assertTrue(CurrentMapOcr.matchesExpected("Raklion 1", "Raklion 1"))
    }

    @Test
    fun weakWhenNotMatched() {
        assertTrue(
            CurrentMapOcr.isWeak(
                CurrentMapOcr.ReadResult(rawText = "Ploin of Four Winds 2", matched = false),
            ),
        )
        assertFalse(
            CurrentMapOcr.isWeak(
                CurrentMapOcr.ReadResult(rawText = "Plain of Four Winds 2", matched = true),
            ),
        )
    }

    @Test
    fun matchesLandOfDemonsWithoutTreatingLineAsFloor() {
        // Zone name is floorless; [Line N] / [Wire N] are the channel, not a submap.
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Land of Demons (Line 1]",
                "Land of Demons",
            ),
        )
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Land of Demons [Wire2]",
                "Land of Demons",
            ),
        )
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Land of Demons Wirel",
                "Land of Demons",
            ),
        )
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Land of Demons Mire",
                "Land of Demons",
            ),
        )
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Land of Demons DAire1",
                "Land of Demons",
            ),
        )
        // Stale "Land of Demons 1" expected must not be required; open title alone is enough
        // for the floorless name, and must not confuse Raklion-style digit matching.
        assertFalse(
            CurrentMapOcr.matchesExpected(
                "Land of Demons",
                "Land of Demons 2",
            ),
        )
        assertFalse(
            CurrentMapOcr.matchesExpected(
                "Divine Realm",
                "Land of Demons",
            ),
        )
    }

    @Test
    fun staleLandOfDemons1ExpectedMatchesFloorlessHudWhenSanitized() {
        // Overlay pack may still ship name="Land of Demons 1"; OCR expects floorless.
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Land of Demons (Line 1]",
                MapDefinitionRepository.ocrExpectedName(
                    MapDefinition(
                        id = "land_of_demons_1",
                        name = "Land of Demons 1",
                        group = "Land of Demons",
                        maintenance = null,
                        coordinateMapping = null,
                    ),
                    groupSiblingCount = 1,
                ),
            ),
        )
        // Multi-floor groups must keep the digit (Aida 1 vs Aida 2).
        assertEquals(
            "Aida 1",
            MapDefinitionRepository.ocrExpectedName(
                MapDefinition(
                    id = "aida_1",
                    name = "Aida 1",
                    group = "Aida",
                    maintenance = null,
                    coordinateMapping = null,
                ),
                groupSiblingCount = 2,
            ),
        )
    }

    @Test
    fun sanitizeStripsWireAndLineChannelBleed() {
        assertEquals(
            "Land of Demons",
            CurrentMapOcr.sanitizeHudText("Land of Demons (Line 1]"),
        )
        assertEquals(
            "Land of Demons",
            CurrentMapOcr.sanitizeHudText("Land of Demons [Wire2]"),
        )
        assertEquals(
            "Raklion 3",
            CurrentMapOcr.sanitizeHudText("Raklion 3 [Wire2]"),
        )
        assertTrue(
            CurrentMapOcr.matchesExpected(
                "Raklion 3 Wire4]",
                "Raklion 3",
            ),
        )
    }

    @Test
    fun resolvesRecognizedOtherMapWithoutTreatingGarbageAsKnown() {
        val maps = listOf(
            "noria" to "Noria",
            "corrupted_lands" to "Corrupted Lands",
            "plains_1" to "Plain of Four Winds 1",
            "plains_2" to "Plain of Four Winds 2",
            "land_of_demons_1" to "Land of Demons",
        )

        assertEquals("noria", CurrentMapOcr.resolveKnownMapId("Noria", maps))
        assertEquals(
            "corrupted_lands",
            CurrentMapOcr.resolveKnownMapId("Corrupled Lands Wirel", maps),
        )
        assertEquals(
            "plains_2",
            CurrentMapOcr.resolveKnownMapId("Plain of Four Winds 2-6Switch", maps),
        )
        assertEquals(
            "land_of_demons_1",
            CurrentMapOcr.resolveKnownMapId("Land of Demons (Line 1]", maps),
        )
        assertNull(CurrentMapOcr.resolveKnownMapId("DUON,", maps))
        assertNull(CurrentMapOcr.resolveKnownMapId("", maps))
    }
}
