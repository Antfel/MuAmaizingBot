package com.example.muamaizingbot.bot.maintenance

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.example.muamaizingbot.vision.roi.ScaledRoi
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Nearby players via Immortal nameplate **color structure** (not HP bars / random green):
 *
 * `[GUILD]` gold/yellow  →  `SERVER` white  →  `NAME` green
 *
 * Focus tap is **below** the nameplate (mid-torso), never on/above the text.
 */
object NearbyAllyDetector {

    private const val TAG = "ElfBuffCast"
    private const val SKIP_LOG_MS = 20_000L

    /** `[GUILD]` text — yellow / gold. */
    private val GOLD_LOW = Scalar(12.0, 55.0, 120.0)
    private val GOLD_HIGH = Scalar(38.0, 255.0, 255.0)

    /** `SERVER` (e.g. S323) — white / near-white. */
    private val WHITE_LOW = Scalar(0.0, 0.0, 175.0)
    private val WHITE_HIGH = Scalar(180.0, 60.0, 255.0)

    /** Player `NAME` — green. */
    private val GREEN_LOW = Scalar(40.0, 40.0, 80.0)
    private val GREEN_HIGH = Scalar(95.0, 255.0, 255.0)

    /**
     * Distance from nameplate **bottom** down to upper torso of *that* character.
     * Too large (e.g. 108px) overshoots into our own body when the ally stands
     * stacked above us on screen.
     */
    private const val FOCUS_BELOW_FRAC = 0.085f
    private const val FOCUS_BELOW_MIN = 55
    private const val FOCUS_BELOW_MAX = 75

    /** Screen-center body zone: reject focus taps that would hit ourselves. */
    private const val SELF_BODY_Y_FRAC = 0.52f
    private const val SELF_BODY_RADIUS_FRAC = 0.10f
    private const val SELF_PLATE_RADIUS_FRAC = 0.10f

    data class NameplateHit(
        /** Horizontal center of the full `[GUILD] SERVER.NAME` span. */
        val centerX: Int,
        /** Vertical center of the nameplate text row. */
        val centerY: Int,
        /** Bottom edge of the nameplate (tap must be below this). */
        val plateBottom: Int,
        val width: Int,
        val height: Int,
        val area: Int,
        val source: String = "struct",
    ) {
        fun focusTapOffset(frameHeight: Int): Int {
            return (frameHeight * FOCUS_BELOW_FRAC).toInt()
                .coerceIn(FOCUS_BELOW_MIN, FOCUS_BELOW_MAX)
        }

        fun focusTapX(frameWidth: Int): Int = centerX.coerceIn(0, frameWidth - 1)

        /** Mid-character: below the nameplate bottom, never above the name. */
        fun focusTapY(frameHeight: Int): Int {
            return (plateBottom + focusTapOffset(frameHeight)).coerceIn(0, frameHeight - 1)
        }
    }

    data class Status(
        val nearby: Boolean = false,
        val barCount: Int = 0,
        val detail: String = "Near: —",
        val lastNames: List<String> = emptyList(),
    )

    @Volatile
    private var lastSkipLogMs = 0L

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    suspend fun detectNearbyAllies(): List<NameplateHit> {
        val frame = NavigationVision.captureFrame() ?: run {
            publish(false, emptyList())
            return emptyList()
        }
        return try {
            val hits = findStructuredNameplates(frame)
            publish(hits.isNotEmpty(), hits)
            if (hits.isNotEmpty()) {
                Log.d(
                    TAG,
                    "[ELF_GIVER] nearby players=${hits.size} " +
                        hits.joinToString {
                            "plate=(${it.centerX},${it.centerY}) bottom=${it.plateBottom} " +
                                "focus=(${it.focusTapX(frame.width)},${it.focusTapY(frame.height)})"
                        },
                )
            } else {
                val now = System.currentTimeMillis()
                if (now - lastSkipLogMs >= SKIP_LOG_MS) {
                    Log.d(TAG, "[ELF_GIVER] nearby players=0 (no gold→white→green nameplate)")
                    lastSkipLogMs = now
                }
            }
            hits
        } finally {
            frame.recycle()
        }
    }

    /**
     * Gold / white / green masks painted for debug dumps.
     * Caller owns the returned bitmap.
     */
    fun buildMaskOverlay(frame: Bitmap): Bitmap {
        val world = worldRoi(frame)
        val crop = Bitmap.createBitmap(frame, world.left, world.top, world.width(), world.height())
        val bgr = OpenCvBitmapConverter.bitmapToBgrMat(crop)
        val hsv = Mat()
        val gold = Mat()
        val white = Mat()
        val green = Mat()
        val overlay = Mat()
        return try {
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
            Core.inRange(hsv, GOLD_LOW, GOLD_HIGH, gold)
            Core.inRange(hsv, WHITE_LOW, WHITE_HIGH, white)
            Core.inRange(hsv, GREEN_LOW, GREEN_HIGH, green)

            // Darken original crop as base.
            bgr.convertTo(overlay, -1, 0.35, 0.0)
            paintMaskOnto(overlay, gold, Scalar(0.0, 180.0, 255.0)) // BGR orange-gold
            paintMaskOnto(overlay, white, Scalar(255.0, 255.0, 255.0))
            paintMaskOnto(overlay, green, Scalar(80.0, 255.0, 0.0))

            val cropBmp = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(overlay, cropBmp)

            val out = frame.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(out)
            val dim = Paint().apply { alpha = 100 }
            canvas.drawBitmap(frame, 0f, 0f, dim)
            canvas.drawBitmap(cropBmp, world.left.toFloat(), world.top.toFloat(), null)
            cropBmp.recycle()

            val roiPaint = Paint().apply {
                color = android.graphics.Color.MAGENTA
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(
                world.left.toFloat(),
                world.top.toFloat(),
                world.right.toFloat(),
                world.bottom.toFloat(),
                roiPaint,
            )
            val legend = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 18f
                setShadowLayer(2f, 1f, 1f, android.graphics.Color.BLACK)
            }
            canvas.drawText("ORANGE=GUILD  WHITE=SERVER  LIME=NAME  magenta=ROI", 12f, 28f, legend)
            out
        } finally {
            crop.recycle()
            bgr.release()
            hsv.release()
            gold.release()
            white.release()
            green.release()
            overlay.release()
        }
    }

    private fun paintMaskOnto(bgr: Mat, mask: Mat, colorBgr: Scalar) {
        val colored = Mat(bgr.size(), bgr.type(), colorBgr)
        colored.copyTo(bgr, mask)
        colored.release()
    }

    suspend fun hasNearbyAllies(): Boolean = detectNearbyAllies().isNotEmpty()

    fun findStructuredNameplates(frame: Bitmap): List<NameplateHit> {
        val world = worldRoi(frame)
        val crop = Bitmap.createBitmap(frame, world.left, world.top, world.width(), world.height())
        val bgr = OpenCvBitmapConverter.bitmapToBgrMat(crop)
        val hsv = Mat()
        val gold = Mat()
        val white = Mat()
        val green = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        return try {
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
            Core.inRange(hsv, GOLD_LOW, GOLD_HIGH, gold)
            Core.inRange(hsv, WHITE_LOW, WHITE_HIGH, white)
            Core.inRange(hsv, GREEN_LOW, GREEN_HIGH, green)

            val close = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(5.0, 2.0))
            Imgproc.morphologyEx(gold, gold, Imgproc.MORPH_CLOSE, close)
            // Connect letters of green NAME so we get the full span (not just first glyph).
            Imgproc.morphologyEx(green, green, Imgproc.MORPH_CLOSE, close)
            close.release()

            Imgproc.findContours(gold, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val selfX = frame.width / 2
            val selfY = (frame.height * SELF_BODY_Y_FRAC).toInt()
            val selfPlateR = (frame.width * SELF_PLATE_RADIUS_FRAC).toInt().coerceAtLeast(100)
            val selfBodyR = (frame.width * SELF_BODY_RADIUS_FRAC).toInt().coerceAtLeast(140)
            val minGuildW = (frame.width * 0.02f).toInt().coerceAtLeast(24)
            val maxGuildW = (frame.width * 0.13f).toInt().coerceAtMost(160)
            val minGuildH = 7
            val maxGuildH = 28

            val hits = mutableListOf<NameplateHit>()
            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                val area = Imgproc.contourArea(contour).toInt()
                if (area < 30) continue
                if (rect.width < minGuildW || rect.width > maxGuildW) continue
                if (rect.height < minGuildH || rect.height > maxGuildH) continue
                val aspect = rect.width.toFloat() / rect.height.coerceAtLeast(1)
                if (aspect < 1.5f) continue

                // Skip minimap / top-right chrome.
                val absGuildX = world.left + rect.x
                val absGuildY = world.top + rect.y
                if (absGuildX > frame.width * 0.72f && absGuildY < frame.height * 0.22f) continue

                val structured = matchNameplateBounds(gold, white, green, rect) ?: continue
                val absLeft = world.left + structured.left
                val absRight = world.left + structured.right
                val absTop = world.top + structured.top
                val absBottom = world.top + structured.bottom
                // Character sits under the *middle* of the full [GUILD] SERVER.NAME span.
                val cx = (absLeft + absRight) / 2
                val cy = (absTop + absBottom) / 2
                if (absRight - absLeft < 80) continue

                if (ElfBuffExclusionZones.intersectsPlate(
                        absLeft, absTop, absRight, absBottom,
                        frame.width, frame.height,
                    )
                ) {
                    Log.d(
                        TAG,
                        "[ELF_GIVER] skip plate in exclusion zone " +
                            "plate=($cx,$cy) bounds=($absLeft,$absTop)-($absRight,$absBottom)",
                    )
                    continue
                }

                // Own nameplate sits above our body — drop plates too close to self center.
                if (hypot((cx - selfX).toDouble(), (cy - selfY).toDouble()) < selfPlateR) {
                    Log.d(TAG, "[ELF_GIVER] skip plate near self plate=($cx,$cy)")
                    continue
                }

                val hit = NameplateHit(
                    centerX = cx,
                    centerY = cy,
                    plateBottom = absBottom,
                    width = absRight - absLeft,
                    height = absBottom - absTop,
                    area = area,
                    source = "struct",
                )
                Log.d(
                    TAG,
                    "[ELF_GIVER] plate bounds left=$absLeft right=$absRight " +
                        "midX=$cx width=${absRight - absLeft}",
                )
                val fx = hit.focusTapX(frame.width)
                val fy = hit.focusTapY(frame.height)
                val excludedZone = ElfBuffExclusionZones.zoneIdAt(fx, fy, frame.width, frame.height)
                if (excludedZone != null) {
                    Log.d(
                        TAG,
                        "[ELF_GIVER] skip focus in exclusion zone=$excludedZone " +
                            "plate=($cx,$cy) focus=($fx,$fy)",
                    )
                    continue
                }
                // Critical: focus must not land on our own character model.
                if (hypot((fx - selfX).toDouble(), (fy - selfY).toDouble()) < selfBodyR) {
                    Log.d(
                        TAG,
                        "[ELF_GIVER] skip focus-on-self plate=($cx,$cy) " +
                            "focus=($fx,$fy) self=($selfX,$selfY) r=$selfBodyR",
                    )
                    continue
                }

                hits.add(hit)
            }

            mergeNearby(hits, mergeDist = (frame.width * 0.05f).toInt().coerceAtLeast(55))
                .sortedByDescending { it.width * it.height }
                .take(5)
        } finally {
            crop.recycle()
            bgr.release()
            hsv.release()
            gold.release()
            white.release()
            green.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private data class PlateBounds(
        val left: Int,
        val right: Int,
        val top: Int,
        val bottom: Int,
    )

    /**
     * Gold `[GUILD]` + white `SERVER` + green `NAME`.
     * Bounds span the full name string so focus X = horizontal midpoint (character center).
     *
     * Rejects combat clutter where "white" spans the whole plate (damage/loot glow).
     * Real Immortal plates have a **narrow** white SERVER token (e.g. S323 ≈ 15–50px).
     */
    private fun matchNameplateBounds(
        gold: Mat,
        white: Mat,
        green: Mat,
        guild: org.opencv.core.Rect,
    ): PlateBounds? {
        val row0 = (guild.y - 3).coerceAtLeast(0)
        val row1 = (guild.y + guild.height + 4).coerceAtMost(white.rows())
        val bandLeft = guild.x.coerceAtLeast(0)
        val searchLeft = (guild.x + guild.width - 4).coerceAtLeast(0)
        val searchRight = (searchLeft + 240).coerceAtMost(white.cols())
        if (row1 - row0 < 6 || searchRight - searchLeft < 40) return null

        val whiteBand = white.submat(row0, row1, searchLeft, searchRight)
        val greenBand = green.submat(row0, row1, searchLeft, searchRight)
        return try {
            val wProj = projectCols(whiteBand, minHits = 1)
            val gProj = projectCols(greenBand, minHits = 1)
            val whiteRuns = runs(wProj, minLen = 8)
            val greenRuns = runs(gProj, minLen = 10)
            if (whiteRuns.isEmpty() || greenRuns.isEmpty()) return null

            var best: PlateBounds? = null
            var bestScore = -1
            for (whiteRun in whiteRuns) {
                if (whiteRun.first > 55) continue
                val whiteW = whiteRun.second - whiteRun.first
                // SERVER is short (S5 / S323). Wide white ≈ combat FP.
                if (whiteW < 8 || whiteW > 52) continue

                for (greenRun in greenRuns) {
                    // NAME must start at/after SERVER (tiny overlap OK for antialias).
                    if (greenRun.first < whiteRun.second - 8) continue
                    if (greenRun.first > whiteRun.second + 40) break

                    val greenEndLocal = extendRunEnd(gProj, greenRun.second, maxGap = 10)
                    val greenW = greenEndLocal - greenRun.first
                    if (greenW < 16) continue

                    val plateRight = searchLeft + greenEndLocal
                    val plateLeft = bandLeft
                    val totalW = plateRight - plateLeft
                    if (totalW < 90 || totalW > 320) continue

                    // Prefer long green NAME + compact white SERVER.
                    val score = greenW * 3 + (52 - whiteW)
                    if (score > bestScore) {
                        bestScore = score
                        best = PlateBounds(
                            left = plateLeft,
                            right = plateRight,
                            top = row0,
                            bottom = row1,
                        )
                    }
                }
            }
            best
        } finally {
            whiteBand.release()
            greenBand.release()
        }
    }

    /** Grow a run end across small gaps (letter spacing in NAME). */
    private fun extendRunEnd(mask: BooleanArray, end: Int, maxGap: Int): Int {
        var i = end
        while (i < mask.size) {
            if (mask[i]) {
                i++
                continue
            }
            var gap = 0
            var j = i
            while (j < mask.size && !mask[j] && gap <= maxGap) {
                j++
                gap++
            }
            if (gap > maxGap || j >= mask.size || !mask[j]) break
            i = j
        }
        return i
    }

    /** Per-column hit count ≥ minHits → boolean presence. */
    private fun projectCols(band: Mat, minHits: Int): BooleanArray {
        val cols = band.cols()
        val out = BooleanArray(cols)
        for (x in 0 until cols) {
            out[x] = Core.countNonZero(band.col(x)) >= minHits
        }
        return out
    }

    private fun runs(mask: BooleanArray, minLen: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < mask.size) {
            if (mask[i]) {
                var j = i
                while (j < mask.size && mask[j]) j++
                if (j - i >= minLen) out.add(i to j)
                i = j
            } else {
                i++
            }
        }
        return out
    }

    private fun mergeNearby(hits: List<NameplateHit>, mergeDist: Int): List<NameplateHit> {
        if (hits.size <= 1) return hits
        val sorted = hits.sortedByDescending { it.width }
        val kept = mutableListOf<NameplateHit>()
        for (hit in sorted) {
            val near = kept.any {
                hypot((it.centerX - hit.centerX).toDouble(), (it.centerY - hit.centerY).toDouble()) < mergeDist
            }
            if (!near) kept.add(hit)
        }
        return kept
    }

    private fun worldRoi(frame: Bitmap): Rect {
        // Keep clear of minimap (top-right) and HUD.
        return ScaledRoi.fromRefRect(
            left = 240,
            top = 140,
            right = 2100,
            bottom = 960,
            frameWidth = frame.width,
            frameHeight = frame.height,
        )
    }

    private fun publish(nearby: Boolean, hits: List<NameplateHit>) {
        _status.value = Status(
            nearby = nearby,
            barCount = hits.size,
            detail = if (nearby) "Near: ${hits.size}" else "Near: 0",
            lastNames = hits.map { "${it.centerX},${it.centerY}" },
        )
    }
}
