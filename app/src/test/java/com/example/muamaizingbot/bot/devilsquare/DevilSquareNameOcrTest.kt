package com.example.muamaizingbot.bot.devilsquare

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fuzzy map-name match used to confirm the Devil Square HUD.
 *
 * The matcher accepts short tokens (`devi`, `squa`) so OCR letter drops still
 * resolve; these tests pin the accepted variants and, more importantly, keep
 * real map names from matching.
 */
class DevilSquareNameOcrTest {

    @Test
    fun matchesExactName() {
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("Devil Square"))
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("DEVIL SQUARE"))
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("DevilSquare"))
    }

    @Test
    fun matchesDespiteSpacingAndTrailingLevel() {
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("  Devil   Square  "))
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("Devil Square 1"))
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("[Devil Square]"))
    }

    @Test
    fun matchesOcrLetterDrops() {
        // Variants observed when the HUD text is small or partially occluded.
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("Devl Squore"))
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("Devi Squar"))
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("Dewl Sqare"))
        assertTrue(DevilSquareActions.looksLikeDevilSquareName("Devll Squa"))
    }

    @Test
    fun rejectsWhenOnlyOneHalfIsPresent() {
        assertFalse(DevilSquareActions.looksLikeDevilSquareName("Devil Plains"))
        assertFalse(DevilSquareActions.looksLikeDevilSquareName("Town Square"))
    }

    @Test
    fun rejectsStringsTooShortToTrust() {
        assertFalse(DevilSquareActions.looksLikeDevilSquareName(""))
        assertFalse(DevilSquareActions.looksLikeDevilSquareName("Devil"))
        assertFalse(DevilSquareActions.looksLikeDevilSquareName("DvSq"))
    }

    /**
     * `Devias` is a real MU map and contains the `devi` token — the closest
     * real-world false positive. No shipped map name may match.
     */
    @Test
    fun rejectsEveryRealMapName() {
        val realMapNames = listOf(
            "Devias",
            "Lorencia",
            "Noria",
            "Elbeland",
            "Land of Demons",
            "Divine Realm",
            "Eversong Forest",
            "Ferea",
            "Abyssal Ferea",
            "Foggy Forest",
            "Corrupted Lands",
            "Atlans",
            "Tarkan",
            "Icarus",
            "Lost Tower",
            "Aida",
            "Dungeon",
            "Kanturu Ruins",
            "Kanturu Relics",
            "Raklion",
            "Swamp of Peace",
            "Plain of Four Winds",
            "Temple of Kalima",
            "Endless Abyss",
            "High Heaven",
            "Purgatory of Misery",
            "Corridor of Agony",
        )
        for (name in realMapNames) {
            assertFalse(
                "map name must not read as Devil Square: $name",
                DevilSquareActions.looksLikeDevilSquareName(name),
            )
        }
    }
}
