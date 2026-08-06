package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.disconnect.DisconnectDetector
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Detects active Elf companion buffs (Greater Defense + Greater Damage).
 *
 * Icons used to be matched as one side-by-side template. When the buff bar
 * overflows, one icon can stay on the strip while the other moves into the
 * overflow popup behind the yellow up-arrow. We probe each icon separately
 * and, if either is missing, open the overflow panel and re-check.
 *
 * Buff strip + overflow arrow live on the bottom-right HUD (not the top bar).
 */
object ElfBuffCheckActions {

    private const val TAG = "ElfBuff"

    private const val BUFF_DEFENSE = "templates/mu/ui/common/elf_buff_greater_defense.png"
    private const val BUFF_DAMAGE = "templates/mu/ui/common/elf_buff_greater_damage.png"
    private const val OVERFLOW_ARROW = "templates/mu/ui/common/elf_buff_overflow_arrow.png"

    private const val BUFF_THRESHOLD = 0.55f
    private const val ARROW_THRESHOLD = 0.78f
    private const val OVERFLOW_SETTLE_MS = 450L

    /**
     * Authored on 1280×720. User pin around arrow: (1100,690)–(1120,710).
     * Slightly padded so the 24×22 template fits.
     */
    private val ARROW_ROI_1280 = Rect(1085, 670, 1155, 720)

    /** Bottom-right strip: buff icons + overflow popup above the arrow. */
    private val BUFF_ROI_1280 = Rect(700, 480, 1280, 720)

    data class BuffPresence(
        val defense: Boolean,
        val damage: Boolean,
    ) {
        val both: Boolean get() = defense && damage

        fun or(other: BuffPresence): BuffPresence = BuffPresence(
            defense = defense || other.defense,
            damage = damage || other.damage,
        )
    }

    /**
     * True when both Greater Defense and Greater Damage are active (bar and/or
     * overflow). When elf buff is disabled on the profile, returns true so
     * callers never navigate for buff.
     */
    suspend fun hasElfBuff(): Boolean {
        if (!ProfileRepository.shouldSeekElfBuff()) {
            Log.d(TAG, "[ELF] hasBuff skipped (disabled or not configured)")
            return true
        }

        DisconnectDetector.markBusy("elf-buff-check", holdMs = 15_000L)
        val first = probeBuffs()
        Log.d(
            TAG,
            "[ELF] hasBuff bar defense=${first.defense} damage=${first.damage}",
        )
        if (first.both) {
            ElfBuffSeekGate.noteBuffPresent()
            return true
        }

        val afterOverflow = checkViaOverflow(already = first)
        val ok = afterOverflow.both
        Log.d(
            TAG,
            "[ELF] hasBuff final defense=${afterOverflow.defense} " +
                "damage=${afterOverflow.damage} ok=$ok",
        )
        if (ok) {
            ElfBuffSeekGate.noteBuffPresent()
        }
        return ok
    }

    private suspend fun checkViaOverflow(already: BuffPresence): BuffPresence {
        val barRoi = arrowRoi()
        val arrow = NavigationVision.findTemplate(
            OVERFLOW_ARROW,
            ARROW_THRESHOLD,
            barRoi,
        )
        if (arrow == null) {
            Log.d(TAG, "[ELF] overflow arrow not present — skip popup check")
            NavigationVision.logBestScore(OVERFLOW_ARROW, barRoi)
            return already
        }

        return DisconnectDetector.withUiAction("elf-buff-overflow", holdMs = 15_000L) {
            Log.d(
                TAG,
                "[ELF] opening buff overflow score=${"%.3f".format(arrow.score)} " +
                    "at=(${arrow.centerX},${arrow.centerY})",
            )
            if (!NavigationVision.tapMatch(arrow)) {
                return@withUiAction already
            }
            delay(OVERFLOW_SETTLE_MS)
            try {
                val opened = probeBuffs()
                Log.d(
                    TAG,
                    "[ELF] hasBuff overflow defense=${opened.defense} damage=${opened.damage}",
                )
                already.or(opened)
            } finally {
                closeOverflow()
            }
        }
    }

    private suspend fun closeOverflow() {
        val arrow = NavigationVision.findTemplate(
            OVERFLOW_ARROW,
            ARROW_THRESHOLD,
            arrowRoi(),
        )
        if (arrow != null) {
            NavigationVision.tapMatch(arrow)
            delay(OVERFLOW_SETTLE_MS)
            Log.d(TAG, "[ELF] overflow closed")
        } else {
            Log.d(TAG, "[ELF] overflow close miss (arrow gone)")
        }
    }

    private suspend fun probeBuffs(): BuffPresence {
        val roi = buffSearchRoi()
        val defense = NavigationVision.findTemplate(BUFF_DEFENSE, BUFF_THRESHOLD, roi) != null
        val damage = NavigationVision.findTemplate(BUFF_DAMAGE, BUFF_THRESHOLD, roi) != null
        if (!defense) {
            NavigationVision.logBestScore(BUFF_DEFENSE, roi)
        }
        if (!damage) {
            NavigationVision.logBestScore(BUFF_DAMAGE, roi)
        }
        return BuffPresence(defense = defense, damage = damage)
    }

    private suspend fun arrowRoi(): Rect? = scaleRoi1280(ARROW_ROI_1280)

    private suspend fun buffSearchRoi(): Rect? = scaleRoi1280(BUFF_ROI_1280)

    private suspend fun scaleRoi1280(ref: Rect): Rect? {
        val frame = NavigationVision.captureFrame() ?: return null
        return try {
            val w = frame.width
            val h = frame.height
            Rect(
                ref.left * w / RefCoords.TARGET_WIDTH,
                ref.top * h / RefCoords.TARGET_HEIGHT,
                ref.right * w / RefCoords.TARGET_WIDTH,
                ref.bottom * h / RefCoords.TARGET_HEIGHT,
            )
        } finally {
            frame.recycle()
        }
    }
}
