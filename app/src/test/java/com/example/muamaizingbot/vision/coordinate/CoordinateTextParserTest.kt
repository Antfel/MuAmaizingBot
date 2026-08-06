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
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(6, 161))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(17, 171))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(161, 161))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(190, 171))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedAxis(200, 161))
    }

    @Test
    fun looksLikeTruncatedHudRead_matchesIncidentSamples() {
        val target = 161 to 171
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(61 to 171, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(16 to 171, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(6 to 171, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(161 to 17, target, 5))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(16 to 17, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(1 to 190, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(16 to 200, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(161 to 171, target, 5))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(200 to 200, target, 5))
    }

    @Test
    fun looksLikeTruncatedHudRead_plains2Wire2Boss179() {
        // Incident: target (179,153), OCR sticky (7,154) — drops leading 1.
        val target = 179 to 153
        val radius = 10
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedHudRead(7 to 154, target, radius))
        assertEquals(true, CoordinateTextParser.looksLikeTruncatedAxis(7, 179))
        // Y far from boss must not count as arrival-truncation.
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(7 to 50, target, radius))
        assertEquals(false, CoordinateTextParser.looksLikeTruncatedHudRead(200 to 200, target, radius))
    }
}
