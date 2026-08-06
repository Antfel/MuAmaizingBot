package com.example.muamaizingbot.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class PetTypeTest {

    @Test
    fun parse_acceptsAngelAndImp() {
        assertEquals(PetType.ANGEL, PetType.parse("angel"))
        assertEquals(PetType.ANGEL, PetType.parse("ANGEL"))
        assertEquals(PetType.IMP, PetType.parse("imp"))
        assertEquals(PetType.IMP, PetType.parse("Imp"))
    }

    @Test
    fun parse_unknownFallsBackToDefault() {
        assertEquals(PetType.DEFAULT, PetType.parse(null))
        assertEquals(PetType.DEFAULT, PetType.parse(""))
        assertEquals(PetType.DEFAULT, PetType.parse("dragon"))
    }

    @Test
    fun toStorage_isLowercase() {
        assertEquals("angel", PetType.ANGEL.toStorage())
        assertEquals("imp", PetType.IMP.toStorage())
    }
}
