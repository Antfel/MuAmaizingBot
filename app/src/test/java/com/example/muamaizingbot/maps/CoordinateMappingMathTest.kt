package com.example.muamaizingbot.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure math checks for forward/inverse affine using a fixed transform
 * (Plain of Four Winds 2 coefficients) without loading map assets.
 */
class CoordinateMappingMathTest {

    private fun mapping(): AffineTransform = AffineTransform(
        coordX = listOf(0.128144021, 0.128094355, -155.446674),
        coordY = listOf(0.12578925, -0.12754393, 27.906028),
        sourceWidth = 2560,
        sourceHeight = 1440,
    )

    private fun applyForward(px: Double, py: Double): Pair<Double, Double> {
        val m = mapping()
        val gx = m.coordX[0] * px + m.coordX[1] * py + m.coordX[2]
        val gy = m.coordY[0] * px + m.coordY[1] * py + m.coordY[2]
        return gx to gy
    }

    @Test
    fun inverseRecoversSourcePixels() {
        val m = mapping()
        val px = 1200.0
        val py = 700.0
        val (gx, gy) = applyForward(px, py)

        val a = m.coordX[0]
        val b = m.coordX[1]
        val c = m.coordX[2]
        val d = m.coordY[0]
        val e = m.coordY[1]
        val f = m.coordY[2]
        val det = a * e - b * d
        assertTrue(kotlin.math.abs(det) > 1e-9)
        val rx = gx - c
        val ry = gy - f
        val ipx = (e * rx - b * ry) / det
        val ipy = (-d * rx + a * ry) / det
        assertEquals(px, ipx, 1e-6)
        assertEquals(py, ipy, 1e-6)
    }

    @Test
    fun hasMappingRequiresThreeCoeffs() {
        val def = MapDefinition(
            id = "t",
            name = "t",
            group = "g",
            maintenance = null,
            coordinateMapping = mapping(),
            navigation = null,
            wireSwitch = null,
        )
        assertTrue(CoordinateMapping.hasMapping(def))
        assertNotNull(CoordinateMapping.pixelToMapCoord(def, 1200, 700))
        assertNotNull(CoordinateMapping.mapCoordToPixel(def, 100, 80))
    }
}
