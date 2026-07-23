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
}
