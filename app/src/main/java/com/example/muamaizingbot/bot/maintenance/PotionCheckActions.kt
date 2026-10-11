package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois

object PotionCheckActions {

    private const val TAG = "PotionCheck"
    const val HP_OUT = "templates/mu/ui/hp_potion_out.png"
    const val MP_OUT = "templates/mu/ui/mana_potion_out.png"
    /** A real empty slot clears this. The other bottle can too; [resolveEmptySlots] breaks that tie. */
    const val POTION_THRESHOLD = 0.92f
    /** Same bottle if centers are at most this far apart at 1280×720. Adjacent slots are ~65px. */
    const val SAME_SLOT_PX_1280 = 40

    data class PotionEmptySlots(
        val hpEmpty: Boolean,
        val mpEmpty: Boolean,
    )

    /**
     * Both templates are searched on the whole quick-slot bar. A double buy needs both scores
     * at or above [threshold] on two different points. Hits on the same point buy only the winner.
     */
    fun resolveEmptySlots(
        hpScore: Float,
        hpCenterX: Int,
        hpCenterY: Int,
        mpScore: Float,
        mpCenterX: Int,
        mpCenterY: Int,
        threshold: Float = POTION_THRESHOLD,
        sameSlotPx: Int = SAME_SLOT_PX_1280,
    ): PotionEmptySlots {
        val hpHit = hpScore >= threshold
        val mpHit = mpScore >= threshold
        if (!hpHit || !mpHit) {
            return PotionEmptySlots(hpEmpty = hpHit, mpEmpty = mpHit)
        }
        val dx = hpCenterX - mpCenterX
        val dy = hpCenterY - mpCenterY
        val samePoint = dx * dx + dy * dy <= sameSlotPx * sameSlotPx
        if (!samePoint) {
            return PotionEmptySlots(hpEmpty = true, mpEmpty = true)
        }
        return if (hpScore >= mpScore) {
            PotionEmptySlots(hpEmpty = true, mpEmpty = false)
        } else {
            PotionEmptySlots(hpEmpty = false, mpEmpty = true)
        }
    }

    suspend fun isHpPotionEmpty(): Boolean = readEmptySlots().hpEmpty

    suspend fun isManaPotionEmpty(): Boolean = readEmptySlots().mpEmpty

    suspend fun isAnyPotionEmpty(): Boolean {
        val slots = readEmptySlots()
        return slots.hpEmpty || slots.mpEmpty
    }

    suspend fun readEmptySlots(): PotionEmptySlots {
        val frame = NavigationVision.captureFrame() ?: return PotionEmptySlots(false, false)
        return try {
            val roi = MuCombatRois.potionSlotsRoi(frame)
            val hpProbe = NavigationVision.probeOnFrame(frame, HP_OUT, roi)
            val mpProbe = NavigationVision.probeOnFrame(frame, MP_OUT, roi)
            val sameSlotPx = SAME_SLOT_PX_1280 * frame.width / 1280
            val slots = resolveEmptySlots(
                hpScore = hpProbe.score,
                hpCenterX = hpProbe.centerX,
                hpCenterY = hpProbe.centerY,
                mpScore = mpProbe.score,
                mpCenterX = mpProbe.centerX,
                mpCenterY = mpProbe.centerY,
                sameSlotPx = sameSlotPx.coerceAtLeast(1),
            )
            Log.d(
                TAG,
                "[POTION] hpEmpty=${slots.hpEmpty}(${"%.3f".format(hpProbe.score)}) " +
                    "at=(${hpProbe.centerX},${hpProbe.centerY}) " +
                    "mpEmpty=${slots.mpEmpty}(${"%.3f".format(mpProbe.score)}) " +
                    "at=(${mpProbe.centerX},${mpProbe.centerY}) " +
                    "sameSlotPx=$sameSlotPx roi=$roi",
            )
            slots
        } finally {
            frame.recycle()
        }
    }

    /** Shared ROI for potion-slot template taps / probes. */
    fun potionSlotsRoi(): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return MuCombatRois.potionSlotsRoi(w, h)
    }

}
