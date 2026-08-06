package com.example.muamaizingbot.vision.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreMuCoinOcrTest {

    @Test
    fun parseAllAmounts_toleratesNoisyStoreBarOcr() {
        // Lower currency strip (host OCR): "207k a] ik | OE"
        assertEquals(
            listOf(207_000L),
            StoreMuCoinOcr.parseAllAmounts("207k a] ik | OE"),
        )
        // Best Seller strip: "+] 139k Kay] 1339 Ket"
        assertEquals(
            listOf(139_000L, 1_339L),
            StoreMuCoinOcr.parseAllAmounts("+] 139k Kay] 1339 Ket"),
        )
        assertTrue(
            StoreMuCoinOcr.balancesFromAmounts(
                StoreMuCoinOcr.parseAllAmounts("+] 139k Kay] 1339 Ket"),
            ).canAfford(StoreMuCoinOcr.PET_COST),
        )
    }

    @Test
    fun parseCompactAmount_handlesKSuffixAndPlainInts() {
        assertEquals(207_000L, StoreMuCoinOcr.parseCompactAmount("207k"))
        assertEquals(12_000L, StoreMuCoinOcr.parseCompactAmount("12K"))
        assertEquals(6_735L, StoreMuCoinOcr.parseCompactAmount("6735"))
        assertEquals(1_500L, StoreMuCoinOcr.parseCompactAmount("1.5k"))
        assertEquals(187_000_000L, StoreMuCoinOcr.parseCompactAmount("187m"))
        assertNull(StoreMuCoinOcr.parseCompactAmount(""))
        assertNull(StoreMuCoinOcr.parseCompactAmount("abc"))
    }

    @Test
    fun parseAllAmounts_readsBothMuCoinFields() {
        assertEquals(
            listOf(207_000L, 12_000L),
            StoreMuCoinOcr.parseAllAmounts("207K 12K"),
        )
        assertEquals(
            listOf(207_000L, 6_735L),
            StoreMuCoinOcr.parseAllAmounts("207k6735"),
        )
    }

    @Test
    fun canAfford_requiresAtLeastOneBalanceAtOrAboveCost() {
        val ok = StoreMuCoinOcr.MuCoinBalances(207_000L, 1_500L)
        assertTrue(ok.canAfford())

        val okSecondary = StoreMuCoinOcr.MuCoinBalances(500L, 6_735L)
        assertTrue(okSecondary.canAfford())

        val bothShort = StoreMuCoinOcr.MuCoinBalances(1_999L, 100L)
        assertFalse(bothShort.canAfford())

        val unread = StoreMuCoinOcr.MuCoinBalances(null, null)
        assertFalse(unread.canAfford())

        val oneUnreadButEnough = StoreMuCoinOcr.MuCoinBalances(null, 2_000L)
        assertTrue(oneUnreadButEnough.canAfford())
    }
}
