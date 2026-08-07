package com.example.muamaizingbot.bot.combat

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoModeDetectorTest {

    @Test
    fun classifyLabel_autoManualPause() {
        assertEquals(AutoModeDetector.Label.AUTO, AutoModeDetector.classifyLabel("Auto"))
        assertEquals(AutoModeDetector.Label.AUTO, AutoModeDetector.classifyLabel("Äuto"))
        assertEquals(AutoModeDetector.Label.AUTO, AutoModeDetector.classifyLabel("VAuto"))
        assertEquals(AutoModeDetector.Label.MANUAL, AutoModeDetector.classifyLabel("Manual"))
        assertEquals(AutoModeDetector.Label.PAUSE, AutoModeDetector.classifyLabel("Pause"))
        assertEquals(AutoModeDetector.Label.PAUSE, AutoModeDetector.classifyLabel("PAUSE"))
        assertEquals(AutoModeDetector.Label.PAUSE, AutoModeDetector.classifyLabel("Paus"))
        assertEquals(AutoModeDetector.Label.NONE, AutoModeDetector.classifyLabel("AutoNav"))
        assertEquals(AutoModeDetector.Label.NONE, AutoModeDetector.classifyLabel(""))
    }

    @Test
    fun normalizeOcr_stripsAccents() {
        assertEquals("auto", AutoModeDetector.normalizeOcr("Äuto"))
        assertEquals("pause", AutoModeDetector.normalizeOcr("Pause"))
        assertEquals("manual", AutoModeDetector.normalizeOcr("Manual"))
    }
}
