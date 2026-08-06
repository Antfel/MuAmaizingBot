package com.example.muamaizingbot.bot.disconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DisconnectUiActionTest {

    @Before
    fun reset() {
        DisconnectDetector.reset()
    }

    @Test
    fun beginEnd_tracksActiveAction() {
        assertFalse(DisconnectDetector.isUiActionActive())
        DisconnectDetector.beginUiAction("pet-validate", holdMs = 5_000L)
        assertTrue(DisconnectDetector.isUiActionActive())
        assertEquals("pet-validate", DisconnectDetector.uiActionReason())
        DisconnectDetector.endUiAction("pet-validate")
        assertFalse(DisconnectDetector.isUiActionActive())
        assertEquals("", DisconnectDetector.uiActionReason())
    }

    @Test
    fun nestedActions_requireMatchingEnds() {
        DisconnectDetector.beginUiAction("outer", holdMs = 5_000L)
        DisconnectDetector.beginUiAction("inner", holdMs = 5_000L)
        assertTrue(DisconnectDetector.isUiActionActive())
        DisconnectDetector.endUiAction("inner")
        assertTrue(DisconnectDetector.isUiActionActive())
        DisconnectDetector.endUiAction("outer")
        assertFalse(DisconnectDetector.isUiActionActive())
    }
}
