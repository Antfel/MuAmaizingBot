package com.example.muamaizingbot.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class CombatFocusPkModeTest {

    @Test
    fun parse_knownValues() {
        assertEquals(CombatFocusPkMode.PEACE, CombatFocusPkMode.parse("peace"))
        assertEquals(CombatFocusPkMode.TEAM, CombatFocusPkMode.parse("TEAM"))
        assertEquals(CombatFocusPkMode.UNION, CombatFocusPkMode.parse(" Union "))
        assertEquals(CombatFocusPkMode.ALL, CombatFocusPkMode.parse("all"))
    }

    @Test
    fun parse_unknownDefaultsToAll() {
        assertEquals(CombatFocusPkMode.ALL, CombatFocusPkMode.parse(null))
        assertEquals(CombatFocusPkMode.ALL, CombatFocusPkMode.parse(""))
        assertEquals(CombatFocusPkMode.ALL, CombatFocusPkMode.parse("nope"))
    }

    @Test
    fun toStorage_roundTrip() {
        for (mode in CombatFocusPkMode.entries) {
            assertEquals(mode, CombatFocusPkMode.parse(mode.toStorage()))
        }
    }
}
