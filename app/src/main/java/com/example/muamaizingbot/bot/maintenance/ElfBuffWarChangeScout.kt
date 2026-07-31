package com.example.muamaizingbot.bot.maintenance

import android.graphics.Bitmap
import android.util.Log
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc

/**
 * War empty-zone scout: keep a grayscale reference of the playfield and score
 * candidate tap cells by mean absolute difference. High-change cells are
 * preferred for the next probe (likely an ally entered that footprint).
 */
object ElfBuffWarChangeScout {

    private const val TAG = "ElfBuffWar"
    private const val PATCH_HALF_BASE = 32
    private const val BASE_W = 1280
    /** Mean absdiff (0–255) required to override round-robin. */
    private const val MIN_PRIORITY_SCORE = 14.0
    /** Refresh empty reference if older than this and scores stay cold. */
    private const val REF_MAX_AGE_MS = 12_000L

    data class RankedCell(
        val cell: ElfBuffWarTapGrid.Cell,
        val score: Double,
    )

    private var refGray: Mat? = null
    private var refW: Int = -1
    private var refH: Int = -1
    private var refCapturedAtMs: Long = 0L
    private var consecutiveColdRanks: Int = 0

    fun reset(reason: String) {
        releaseRef()
        consecutiveColdRanks = 0
        Log.d(TAG, "[WAR_SCOUT] reset reason=$reason")
    }

    /**
     * Rank free cells by change vs empty reference (highest first).
     * Returns empty when OpenCV/frame unavailable or no cell beats [MIN_PRIORITY_SCORE]
     * — caller should fall back to round-robin.
     */
    suspend fun rankFreeCells(
        free: List<ElfBuffWarTapGrid.Cell>,
        screenW: Int,
        screenH: Int,
    ): List<RankedCell> {
        if (free.isEmpty()) return emptyList()
        if (!OpenCVInitializer.isInitialized && !OpenCVInitializer.init()) {
            Log.w(TAG, "[WAR_SCOUT] OpenCV unavailable — skip prioritization")
            return emptyList()
        }

        val frame = NavigationVision.captureFrame() ?: return emptyList()
        return try {
            ensureReference(frame, screenW, screenH)
            val ref = refGray ?: return emptyList()
            val gray = toGray(frame)
            try {
                val half = patchHalf(screenW)
                val ranked = free.map { cell ->
                    RankedCell(cell, patchAbsDiffMean(ref, gray, cell.screenX, cell.screenY, half))
                }.sortedByDescending { it.score }

                val best = ranked.firstOrNull()
                val above = ranked.filter { it.score >= MIN_PRIORITY_SCORE }
                Log.d(
                    TAG,
                    "[WAR_SCOUT] scores=" +
                        ranked.joinToString { "${it.cell.label}=${"%.1f".format(it.score)}" } +
                        " priority=${above.firstOrNull()?.cell?.label ?: "none"}",
                )

                if (above.isEmpty()) {
                    consecutiveColdRanks++
                    if (consecutiveColdRanks >= 4 &&
                        System.currentTimeMillis() - refCapturedAtMs >= REF_MAX_AGE_MS
                    ) {
                        Log.d(TAG, "[WAR_SCOUT] cold ranks — refresh empty reference")
                        captureReference(frame, screenW, screenH)
                        consecutiveColdRanks = 0
                    }
                    emptyList()
                } else {
                    consecutiveColdRanks = 0
                    above
                }
            } finally {
                gray.release()
            }
        } finally {
            frame.recycle()
        }
    }

    private fun ensureReference(frame: Bitmap, screenW: Int, screenH: Int) {
        val age = System.currentTimeMillis() - refCapturedAtMs
        val staleSize = refGray == null || refW != screenW || refH != screenH
        val staleAge = age >= REF_MAX_AGE_MS * 2
        if (staleSize || staleAge) {
            captureReference(frame, screenW, screenH)
        }
    }

    private fun captureReference(frame: Bitmap, screenW: Int, screenH: Int) {
        releaseRef()
        refGray = toGray(frame)
        refW = screenW
        refH = screenH
        refCapturedAtMs = System.currentTimeMillis()
        Log.i(TAG, "[WAR_SCOUT] empty reference captured ${screenW}x${screenH}")
    }

    private fun patchAbsDiffMean(
        ref: Mat,
        cur: Mat,
        cx: Int,
        cy: Int,
        half: Int,
    ): Double {
        val rect = clampPatch(cx, cy, half, ref.cols(), ref.rows()) ?: return 0.0
        val refRoi = Mat(ref, rect)
        val curRoi = Mat(cur, rect)
        val diff = Mat()
        return try {
            Core.absdiff(refRoi, curRoi, diff)
            val mean = Core.mean(diff)
            mean.`val`[0]
        } finally {
            diff.release()
            curRoi.release()
            refRoi.release()
        }
    }

    private fun clampPatch(cx: Int, cy: Int, half: Int, w: Int, h: Int): Rect? {
        val left = (cx - half).coerceAtLeast(0)
        val top = (cy - half).coerceAtLeast(0)
        val right = (cx + half).coerceAtMost(w)
        val bottom = (cy + half).coerceAtMost(h)
        if (right - left < 8 || bottom - top < 8) return null
        return Rect(left, top, right - left, bottom - top)
    }

    private fun toGray(frame: Bitmap): Mat {
        val bgr = OpenCvBitmapConverter.bitmapToBgrMat(frame)
        val gray = Mat()
        return try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            // Materialize continuous buffer (submat-safe later).
            val copy = Mat()
            gray.copyTo(copy)
            copy
        } finally {
            gray.release()
            bgr.release()
        }
    }

    private fun patchHalf(screenW: Int): Int =
        (PATCH_HALF_BASE * screenW / BASE_W).coerceAtLeast(16)

    private fun releaseRef() {
        refGray?.release()
        refGray = null
        refW = -1
        refH = -1
        refCapturedAtMs = 0L
    }
}
