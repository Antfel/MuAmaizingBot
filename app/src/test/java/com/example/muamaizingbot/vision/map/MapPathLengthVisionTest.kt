package com.example.muamaizingbot.vision.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapPathLengthVisionTest {

    @Test
    fun classifyUsesConfigurableFarThreshold() {
        assertEquals(
            MapPathLengthVision.PathClass.UNKNOWN,
            MapPathLengthVision.classify(0, farMinDots = 10),
        )
        assertEquals(
            MapPathLengthVision.PathClass.NEAR,
            MapPathLengthVision.classify(9, farMinDots = 10),
        )
        assertEquals(
            MapPathLengthVision.PathClass.FAR,
            MapPathLengthVision.classify(10, farMinDots = 10),
        )
        assertEquals(
            MapPathLengthVision.PathClass.NEAR,
            MapPathLengthVision.classify(14, farMinDots = 15),
        )
        assertEquals(
            MapPathLengthVision.PathClass.FAR,
            MapPathLengthVision.classify(15, farMinDots = 15),
        )
    }
}
