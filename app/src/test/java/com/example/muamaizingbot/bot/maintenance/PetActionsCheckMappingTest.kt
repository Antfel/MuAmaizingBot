package com.example.muamaizingbot.bot.maintenance

import com.example.muamaizingbot.profile.PetType
import org.junit.Assert.assertEquals
import org.junit.Test

class PetActionsCheckMappingTest {

    @Test
    fun mapsSlotAgainstWantedPet() {
        assertEquals(
            PetActions.CheckResult.MATCH,
            resolve(PetActions.PetSlotState.ANGEL, PetType.ANGEL),
        )
        assertEquals(
            PetActions.CheckResult.MATCH,
            resolve(PetActions.PetSlotState.IMP, PetType.IMP),
        )
        assertEquals(
            PetActions.CheckResult.NEED_PURCHASE,
            resolve(PetActions.PetSlotState.EMPTY, PetType.ANGEL),
        )
        assertEquals(
            PetActions.CheckResult.NEED_PURCHASE,
            resolve(PetActions.PetSlotState.IMP, PetType.ANGEL),
        )
        assertEquals(
            PetActions.CheckResult.NEED_PURCHASE,
            resolve(PetActions.PetSlotState.ANGEL, PetType.IMP),
        )
        assertEquals(
            PetActions.CheckResult.READ_FAILED,
            resolve(PetActions.PetSlotState.UNKNOWN, PetType.ANGEL),
        )
    }

    private fun resolve(
        slot: PetActions.PetSlotState,
        want: PetType,
    ): PetActions.CheckResult {
        return when (slot) {
            PetActions.PetSlotState.UNKNOWN -> PetActions.CheckResult.READ_FAILED
            PetActions.PetSlotState.EMPTY -> PetActions.CheckResult.NEED_PURCHASE
            PetActions.PetSlotState.ANGEL -> if (want == PetType.ANGEL) {
                PetActions.CheckResult.MATCH
            } else {
                PetActions.CheckResult.NEED_PURCHASE
            }
            PetActions.PetSlotState.IMP -> if (want == PetType.IMP) {
                PetActions.CheckResult.MATCH
            } else {
                PetActions.CheckResult.NEED_PURCHASE
            }
        }
    }
}
