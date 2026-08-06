package com.example.muamaizingbot.vision.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireHudOcrTest {

    @Test
    fun parsesWireAndLineChannelLabels() {
        assertEquals(1, WireHudOcr.parseHudWireId("[Wire1]"))
        assertEquals(2, WireHudOcr.parseHudWireId("Wire 2"))
        assertEquals(4, WireHudOcr.parseHudWireId("IWire4]"))
        // Open-map title uses Line for the same channel id.
        assertEquals(1, WireHudOcr.parseHudWireId("Land of Demons (Line 1]"))
        assertEquals(3, WireHudOcr.parseHudWireId("[Line 3]"))
        assertNull(WireHudOcr.parseHudWireId("Land of Demons"))
        assertNull(WireHudOcr.parseHudWireId("Raklion 3"))
    }
}
