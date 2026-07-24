package com.example.muamaizingbot.vision.map

import org.junit.Assert.assertFalse
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
}
