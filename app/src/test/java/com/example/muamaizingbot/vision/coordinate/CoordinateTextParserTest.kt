package com.example.muamaizingbot.vision.coordinate

import com.example.muamaizingbot.maps.CoordinateBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateTextParserTest {

    @Test
    fun parseCoordinates_parenFormat() {
        assertEquals(182 to 115, CoordinateTextParser.parseCoordinates("(182, 115)"))
    }

    @Test
    fun parseCoordinates_commaFormat() {
        assertEquals(42 to 99, CoordinateTextParser.parseCoordinates("42,99"))
    }

    @Test
    fun parseCoordinates_twoNumbers() {
        assertEquals(12 to 34, CoordinateTextParser.parseCoordinates("coord 12 noise 34"))
    }

    @Test
    fun parseCoordinates_invalid() {
        assertNull(CoordinateTextParser.parseCoordinates("no digits"))
    }

    @Test
    fun parseCoordinates_leadingOneMisreadAsN() {
        assertEquals(152 to 95, CoordinateTextParser.parseCoordinates("n52,95)"))
        assertEquals(128 to 122, CoordinateTextParser.parseCoordinates("n28,122)"))
        assertEquals(143 to 95, CoordinateTextParser.parseCoordinates("n43,95)"))
    }

    @Test
    fun applyCoordinateBounds_correctsLeadingOne() {
        val bounds = CoordinateBounds(xMin = 0, xMax = 300, yMin = 0, yMax = 300)
        assertEquals(182 to 115, CoordinateTextParser.applyCoordinateBounds(1182 to 115, bounds))
    }

    @Test
    fun applyCoordinateBounds_rejectsOutOfRange() {
        val bounds = CoordinateBounds(xMin = 0, xMax = 300, yMin = 0, yMax = 300)
        assertNull(CoordinateTextParser.applyCoordinateBounds(999 to 50, bounds))
    }

    @Test
    fun looksLikeTruncatedAxis_commonHudDrops() {
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(61, 161))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(16, 161))
        // Single-digit sticky only for true prefix/suffix ("1"), not middle "6".
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(1, 161))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(6, 161))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(17, 171))
        // Ridax elf: target 114, OCR 13 / 14 (drop leading 1 + optional digit noise).
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(14, 114))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(13, 114))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(161, 161))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(190, 171))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(200, 161))
    }

    @Test
    fun looksLikeTruncatedHudRead_ridaxElf114() {
        val target = 114 to 135
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(14 to 134, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(13 to 134, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(13 to 50, target, 5))
    }

    @Test
    fun looksLikeTruncatedHudRead_matchesIncidentSamples() {
        val target = 161 to 171
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(61 to 171, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(16 to 171, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(1 to 171, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(6 to 171, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(161 to 17, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(16 to 17, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(1 to 190, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(16 to 200, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(161 to 171, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(200 to 200, target, 5))
    }

    @Test
    fun looksLikeTruncatedHudRead_plains2Wire2Boss179() {
        // Drop leading 1 → 79 (prefix/suffix). Lone "7" is the middle digit — rejected
        // (same failure class as RiddB 4∈247). Path-ended + stable covers garbage OCR at spot.
        val target = 179 to 153
        val radius = 10
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(79 to 154, target, radius))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(79, 179))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(17, 179))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(7, 179))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(7 to 154, target, radius))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(7 to 50, target, radius))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(200 to 200, target, radius))
    }

    @Test
    fun looksLikeTruncatedAxis_rejectsMidDigitContains() {
        // RiddB: mid-walk OCR (4,170) vs target (247,169) — "4" is inside "247", not prefix/suffix.
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(4, 247))
        assertEquals(
            false,
            CoordinateTextParser.looksLikeTruncatedHudRead(4 to 170, 247 to 169, 10),
        )
        // Still accept real prefix/suffix truncations (247 → 24 / 47 / 2 / 7).
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(24, 247))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(47, 247))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(7, 247))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(2, 247))
    }
}
