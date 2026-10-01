package com.example.muamaizingbot.bot.maintenance

import com.example.muamaizingbot.profile.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pure parts of the bag sweep: the grid math that decides where the
 * bot taps, and the name matcher that decides whether MU Coin gets spent.
 */
class PetBagScanTest {

    // Grid measured on Ridax: 8×12 cells of 42 px, first centre (933,137) @720p.
    private fun centreAt720p(slot: Int): Pair<Int, Int> {
        val (refX, refY) = PetBagScan.slotCenterRef(slot)
        return refX / 2 to refY / 2
    }

    @Test
    fun firstSlotMatchesMeasuredCentre() {
        assertEquals(933 to 137, centreAt720p(0))
    }

    @Test
    fun columnsAdvanceByCellPitch() {
        assertEquals(933 + 42 to 137, centreAt720p(1))
        assertEquals(933 + 42 * 7 to 137, centreAt720p(7))
    }

    @Test
    fun slotEightWrapsToSecondRow() {
        assertEquals(933 to 137 + 42, centreAt720p(8))
    }

    /**
     * Regression: dividing by the 1280-wide target instead of the 2560-wide
     * reference let ref coordinates through unscaled, so taps landed off-screen at
     * x=1866. The gesture still reported success, every slot looked empty, and the
     * sweep reported the bag as unreadable.
     */
    @Test
    fun slotCentresScaleOntoTheFrame() {
        for (slot in 0 until PetBagScan.SCAN_SLOTS) {
            val (refX, refY) = PetBagScan.slotCenterRef(slot)
            val (x, y) = PetBagScan.slotCenter(slot, 1280, 720)
            assertEquals("slot $slot x", refX / 2, x)
            assertEquals("slot $slot y", refY / 2, y)
            assertTrue("slot $slot x=$x off-screen", x in 0 until 1280)
            assertTrue("slot $slot y=$y off-screen", y in 0 until 720)
        }
    }

    @Test
    fun sweepCoversTwoRows() {
        assertEquals(16, PetBagScan.SCAN_SLOTS)
        // Every swept slot must stay inside the bag drawn at 912..1248 / 116..620.
        for (slot in 0 until PetBagScan.SCAN_SLOTS) {
            val (x, y) = centreAt720p(slot)
            assertTrue("slot $slot x=$x", x in 912..1248)
            assertTrue("slot $slot y=$y", y in 116..620)
        }
    }

    @Test
    fun readsTheObservedPetNames() {
        assertEquals(PetType.IMP, PetBagScan.classifyPetName("Imp"))
        assertEquals(PetType.ANGEL, PetBagScan.classifyPetName("Angel"))
        assertEquals(PetType.ANGEL, PetBagScan.classifyPetName("Guardian Angel"))
    }

    /**
     * An unrecognised pet would make the sweep report absence and authorise a
     * purchase, so common OCR slips must still resolve.
     */
    @Test
    fun toleratesOcrSlipsOnPetNames() {
        assertEquals(PetType.IMP, PetBagScan.classifyPetName("lmp"))
        assertEquals(PetType.IMP, PetBagScan.classifyPetName("1mp"))
        assertEquals(PetType.IMP, PetBagScan.classifyPetName("mp"))
        assertEquals(PetType.ANGEL, PetBagScan.classifyPetName("Ange1"))
        assertEquals(PetType.ANGEL, PetBagScan.classifyPetName("Angei"))
        assertEquals(PetType.ANGEL, PetBagScan.classifyPetName("Guardlan Angel"))
    }

    @Test
    fun ignoresCaseSpacingAndPunctuation() {
        assertEquals(PetType.IMP, PetBagScan.classifyPetName("  IMP  "))
        assertEquals(PetType.ANGEL, PetBagScan.classifyPetName("[Angel]"))
    }

    /**
     * `Archangel` is the gear title showing through the translucent popup, and the
     * consumables around the pets must never read as one.
     */
    @Test
    fun rejectsNonPetItemNames() {
        val notPets = listOf(
            "Archangel",
            "Blessed MP Potion",
            "Blessed HP Potion",
            "Jewel of Bless",
            "Jewel of Soul",
            "Jewel of Chaos",
            "Random Teleport Seal",
            "Outfit Tempering",
            "Non tradable",
            "Base Stats",
            "Recycle Price: 100Bound MU Coin",
        )
        for (name in notPets) {
            assertNull("must not read as a pet: $name", PetBagScan.classifyPetName(name))
        }
    }

    @Test
    fun emptyReadIsNotAPet() {
        assertNull(PetBagScan.classifyPetName(""))
        assertNull(PetBagScan.classifyPetName("   "))
    }

    @Test
    fun onlyTheWarmOrangeOfTheActionPlatesCounts() {
        // Sampled from the Recycle / Equip plates on Ridax.
        assertTrue(PetBagScan.isButtonOrange(232, 154, 74))
        assertTrue(PetBagScan.isButtonOrange(198, 128, 60))
        // Popup chrome and the cyan item name must not register as a button.
        assertFalse(PetBagScan.isButtonOrange(37, 160, 216))
        assertFalse(PetBagScan.isButtonOrange(40, 40, 40))
        assertFalse(PetBagScan.isButtonOrange(255, 255, 255))
    }
}
