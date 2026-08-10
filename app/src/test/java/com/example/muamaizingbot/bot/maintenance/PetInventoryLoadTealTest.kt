package com.example.muamaizingbot.bot.maintenance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetInventoryLoadTealTest {

    @Test
    fun darkTealLoadingPixelsMatch() {
        // Approximate loading marble (cyan cast, dark) from bag mid-load samples.
        assertTrue(PetActions.isLoadingTealPixel(r = 13, g = 51, b = 51))
        assertTrue(PetActions.isLoadingTealPixel(r = 18, g = 54, b = 54))
        assertTrue(PetActions.isLoadingTealPixel(r = 16, g = 52, b = 52))
    }

    @Test
    fun charcoalEmptyAndBrightIconsDoNotMatch() {
        // Empty charcoal (near-neutral dark).
        assertFalse(PetActions.isLoadingTealPixel(r = 22, g = 22, b = 22))
        assertFalse(PetActions.isLoadingTealPixel(r = 28, g = 28, b = 28))
        // Item / non-cyan art.
        assertFalse(PetActions.isLoadingTealPixel(r = 200, g = 80, b = 40))
        assertFalse(PetActions.isLoadingTealPixel(r = 76, g = 83, b = 74))
        assertFalse(PetActions.isLoadingTealPixel(r = 40, g = 180, b = 60))
    }
}
