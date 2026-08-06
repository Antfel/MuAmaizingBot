package com.example.muamaizingbot.vision.store

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * OCR for the two MU Coin balances on the Store currency bar
 * (unbound + bound / padlock). Used to gate Random Teleport Seal purchases.
 *
 * Amounts appear as `207k`, `12K`, or plain integers like `6735`.
 */
object StoreMuCoinOcr {

    private const val TAG = "StoreMuCoinOcr"
    private const val OCR_UPSCALE = 3.0

    /** One Random Teleport Seal pack costs 2000 MU Coin. */
    const val SEAL_PACK_COST = 2_000L

    /** Imp / Guardian Angel on MU Coin Store Best Seller. */
    const val PET_COST = 8_000L

    /**
     * Horizontal span of the two MU Coin amount fields on the Store currency
     * bar @ 1280×720 (unbound + bound/padlock). Vertical position shifts with
     * Store layout (Best Seller vs Common Items), so [readBalances] probes
     * several Y bands — never the item-price footers alone.
     */
    private const val ROI_LEFT_REF = 470
    private const val ROI_RIGHT_REF = 900
    private const val REF_W = 1280f
    private const val REF_H = 720f

    /**
     * Candidate (top, bottom) bands @ 1280×720, ordered by live frequency.
     * - 575–620: Best Seller / typical centered Store (e.g. `139k` / `1339`)
     * - 645–705: lower currency strip (Common Items / taller grid)
     * - 590–640: legacy mid band
     */
    private val ROI_Y_BANDS_REF = listOf(
        575 to 620,
        645 to 705,
        590 to 640,
    )

    /** Find amounts; prefer `139k` over absorbing the K in `1339 Ket`. */
    private val AMOUNT_FIND = Regex(
        pattern = """\d+(?:[.,]\d+)?[kmKM]|\d+(?:[.,]\d+)?""",
        option = RegexOption.IGNORE_CASE,
    )

    private val AMOUNT_PARSE = Regex(
        pattern = """^(\d+(?:[.,]\d+)?)([kmKM])?$""",
        option = RegexOption.IGNORE_CASE,
    )

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    data class MuCoinBalances(
        val primary: Long?,
        val secondary: Long?,
        val raw: String = "",
    ) {
        /** True when at least one readable balance can cover [cost]. */
        fun canAfford(cost: Long = SEAL_PACK_COST): Boolean =
            listOfNotNull(primary, secondary).any { it >= cost }

        fun maxOrNull(): Long? = listOfNotNull(primary, secondary).maxOrNull()
    }

    /**
     * Parse a compact store amount: `207k` → 207000, `12K` → 12000, `6735` → 6735.
     */
    fun parseCompactAmount(token: String): Long? {
        val cleaned = token.trim()
            .replace('，', ',')
            .replace('．', '.')
            .replace('О', '0') // Cyrillic O
            .replace('о', '0')
        val match = AMOUNT_PARSE.matchEntire(cleaned) ?: return null
        val numberRaw = match.groupValues[1].replace(',', '.')
        val number = numberRaw.toDoubleOrNull() ?: return null
        if (number < 0) return null
        val suffix = match.groupValues.getOrNull(2)?.lowercase().orEmpty()
        val scaled = when (suffix) {
            "k" -> number * 1_000.0
            "m" -> number * 1_000_000.0
            else -> number
        }
        if (scaled > Long.MAX_VALUE.toDouble()) return null
        return scaled.toLong()
    }

    /** Extract all compact amounts from an OCR blob (order left→right / top→bottom). */
    fun parseAllAmounts(raw: String): List<Long> {
        val normalized = raw
            .replace('\n', ' ')
            .replace('О', '0')
            .replace('о', '0')
        return AMOUNT_FIND.findAll(normalized)
            .mapNotNull { parseCompactAmount(it.value) }
            .filter { it >= 0 }
            .toList()
    }

    fun balancesFromAmounts(amounts: List<Long>, raw: String = ""): MuCoinBalances {
        return MuCoinBalances(
            primary = amounts.getOrNull(0),
            secondary = amounts.getOrNull(1),
            raw = raw,
        )
    }

    fun muCoinRoi(frameWidth: Int, frameHeight: Int): Rect =
        muCoinRoiForBand(frameWidth, frameHeight, ROI_Y_BANDS_REF.first())

    private fun muCoinRoiForBand(
        frameWidth: Int,
        frameHeight: Int,
        band: Pair<Int, Int>,
    ): Rect {
        val sx = frameWidth / REF_W
        val sy = frameHeight / REF_H
        return Rect(
            (ROI_LEFT_REF * sx).toInt().coerceIn(0, frameWidth),
            (band.first * sy).toInt().coerceIn(0, frameHeight),
            (ROI_RIGHT_REF * sx).toInt().coerceIn(0, frameWidth),
            (band.second * sy).toInt().coerceIn(0, frameHeight),
        )
    }

    suspend fun readBalances(frame: Bitmap): MuCoinBalances {
        var lastEmpty: MuCoinBalances? = null
        for (band in ROI_Y_BANDS_REF) {
            val roi = muCoinRoiForBand(frame.width, frame.height, band)
            val balances = readBalancesInRoi(frame, roi) ?: continue
            if (balances.primary != null || balances.secondary != null) {
                return balances
            }
            lastEmpty = balances
        }
        return lastEmpty ?: MuCoinBalances(null, null)
    }

    private suspend fun readBalancesInRoi(frame: Bitmap, roi: Rect): MuCoinBalances? {
        val crop = crop(frame, roi) ?: run {
            Log.w(TAG, "[MU_COIN] crop failed roi=$roi")
            return null
        }
        val processed = preprocess(crop)
        crop.recycle()
        if (processed == null) {
            Log.w(TAG, "[MU_COIN] preprocess failed")
            return null
        }
        return try {
            val raw = recognize(processed)?.text?.replace('\n', ' ')?.trim().orEmpty()
            val amounts = parseAllAmounts(raw)
            val balances = balancesFromAmounts(amounts, raw)
            Log.d(
                TAG,
                "[MU_COIN] raw=\"$raw\" primary=${balances.primary} " +
                    "secondary=${balances.secondary} roi=$roi",
            )
            balances
        } finally {
            processed.recycle()
        }
    }

    private fun crop(frame: Bitmap, roi: Rect): Bitmap? {
        val left = roi.left.coerceIn(0, frame.width)
        val top = roi.top.coerceIn(0, frame.height)
        val right = roi.right.coerceIn(left, frame.width)
        val bottom = roi.bottom.coerceIn(top, frame.height)
        val w = right - left
        val h = bottom - top
        if (w < 24 || h < 12) {
            return null
        }
        return Bitmap.createBitmap(frame, left, top, w, h)
    }

    private fun preprocess(crop: Bitmap): Bitmap? {
        val bgr = OpenCvBitmapConverter.bitmapToBgrMat(crop)
        val gray = Mat()
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
        bgr.release()

        val upscaled = Mat()
        Imgproc.resize(gray, upscaled, Size(), OCR_UPSCALE, OCR_UPSCALE, Imgproc.INTER_CUBIC)
        gray.release()

        val rgba = Mat()
        Imgproc.cvtColor(upscaled, rgba, Imgproc.COLOR_GRAY2RGBA)
        upscaled.release()
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }

    private suspend fun recognize(
        bitmap: Bitmap,
    ): com.google.mlkit.vision.text.Text? {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "[MU_COIN] ML Kit failed: ${error.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
    }
}
