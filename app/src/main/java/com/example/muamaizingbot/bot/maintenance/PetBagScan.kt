package com.example.muamaizingbot.bot.maintenance

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.profile.PetType
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.opencv.OpenCvBitmapConverter
import com.example.muamaizingbot.vision.roi.ScaledRoi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Slot-by-slot sweep of the first bag rows, used as the confirmation gate before
 * spending MU Coin on a pet.
 *
 * The icon-template search in [PetActions] cannot tell "the pet is not in the bag"
 * apart from "the bag could not be read" — a frozen emulator, unpainted icons or a
 * shifted ROI all score low and used to end in a purchase. This sweep taps each
 * slot and reads the item name from the detail popup instead, so every slot lands
 * in one of three buckets: it is the pet, it is some other item, or it could not
 * be read. Only a clean sweep with zero unreadable slots authorises a purchase.
 *
 * Geometry measured on Ridax @ 1280×720 and stored as logical 2560×1440 refs:
 * the bag is an 8×12 grid of 42 px cells with its first cell centred at (933,137).
 * Tapping Manage sorts equippables to the front, so the pet — when present — sits
 * in the first rows; [SCAN_SLOTS] covers two of them.
 */
object PetBagScan {

    private const val TAG = "PetBagScan"

    /**
     * Top-left of slot 0, ref 2560×1440 (×2 of the measured 1280×720 values).
     *
     * Confirmed on Ridax two ways that agree within 3 px of the resulting centre:
     * cell separators repeat every 42 px from x=915/y=119, and the inventory
     * template matcher reports the Imp icon at (932,143) @720p.
     */
    private const val GRID_ORIGIN_X_REF = 1824
    private const val GRID_ORIGIN_Y_REF = 232
    private const val GRID_PITCH_REF = 84
    const val GRID_COLS = 8

    /** Two rows: Manage sorts equippables to the front, pets land in row 0. */
    const val SCAN_SLOTS = GRID_COLS * 2

    /** Item-name band of the detail popup (centred ~x=687 @ 720p). */
    private const val NAME_LEFT_REF = 1160
    private const val NAME_TOP_REF = 420
    private const val NAME_RIGHT_REF = 1600
    private const val NAME_BOTTOM_REF = 500

    /** Row holding the orange popup actions (Recycle on the left, Equip right). */
    private const val ACTIONS_LEFT_REF = 1000
    private const val ACTIONS_TOP_REF = 990
    private const val ACTIONS_RIGHT_REF = 1800
    private const val ACTIONS_BOTTOM_REF = 1100

    /** A real button is ~102 px wide @ 720p; ignore stray orange glyphs. */
    private const val MIN_BUTTON_WIDTH_PX = 50

    /**
     * Measured on Ridax: the detail popup is absent 0.4 s after the tap and present
     * by 1.4 s. Poll well past that — reading too early makes an occupied slot look
     * empty, which is exactly the false "bag is empty" this class exists to prevent.
     */
    private const val POPUP_TIMEOUT_MS = 3_500L
    private const val POPUP_POLL_MS = 250L
    /** Sorted bags are compacted, so a run of empty slots means the rest is empty. */
    private const val EMPTY_STREAK_STOP = 3
    private const val OCR_UPSCALE = 3.0

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    sealed interface Result {
        /** Wanted pet sits in [slot]; [equipAt] is the popup's Equip button. */
        data class Found(val slot: Int, val equipAt: Point) : Result

        /** Every occupied slot was read and none held the wanted pet. */
        data object ConfirmedAbsent : Result

        /** The bag could not be read well enough to justify spending. */
        data class Inconclusive(val reason: String) : Result
    }

    /**
     * Sweeps the first bag rows. Caller must already have Gear + Inventory open
     * and Manage applied.
     */
    suspend fun sweepForPet(want: PetType): Result = sweep(want, stopOnFound = true)

    /**
     * Reads every slot and only logs what it saw — no Equip, no Store, nothing
     * spent. Used to check the name OCR against a real bag before trusting the
     * sweep to authorise purchases.
     */
    suspend fun dryRunSweep(want: PetType): Result {
        Log.w(TAG, "[BAG] DRY RUN start want=${want.toStorage()} — reads only, nothing is bought")
        val result = sweep(want, stopOnFound = false)
        Log.w(TAG, "[BAG] DRY RUN end result=$result")
        return result
    }

    private suspend fun sweep(want: PetType, stopOnFound: Boolean): Result {
        val (frameWidth, frameHeight) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: return inconclusive("no_frame_size")

        var emptyStreak = 0
        var itemsRead = 0
        var found: Result.Found? = null

        for (slot in 0 until SCAN_SLOTS) {
            val (slotX, slotY) = slotCenter(slot, frameWidth, frameHeight)
            if (!NavigationVision.tapScreen(slotX, slotY)) {
                return inconclusive("tap_failed slot=$slot")
            }
            val buttons = awaitActionButtons(frameWidth, frameHeight)
                ?: return inconclusive("no_frame slot=$slot")

            if (buttons.isEmpty()) {
                // No Equip/Recycle does not mean no item: a consumable opens a popup
                // with its own actions. Counting those as empty would end the sweep
                // early and let an unscanned pet be declared missing.
                val other = readItemName(frameWidth, frameHeight)
                if (!other.isNullOrBlank()) {
                    emptyStreak = 0
                    itemsRead++
                    Log.d(TAG, "[BAG] slot=$slot raw=\"$other\" not equippable")
                    continue
                }
                emptyStreak++
                Log.d(TAG, "[BAG] slot=$slot empty streak=$emptyStreak")
                if (emptyStreak >= EMPTY_STREAK_STOP) {
                    Log.d(TAG, "[BAG] slot=$slot — $EMPTY_STREAK_STOP empties, bag tail reached")
                    break
                }
                continue
            }

            emptyStreak = 0
            when (val outcome = sweepSlot(slot, want, buttons, frameWidth, frameHeight)) {
                is Result.Found -> {
                    if (stopOnFound) return outcome
                    // Dry run: keep reading so the whole bag shows up in the log.
                    found = found ?: outcome
                    itemsRead++
                }
                is Result.Inconclusive -> {
                    if (stopOnFound) return outcome
                    Log.w(TAG, "[BAG] slot=$slot unreadable (${outcome.reason})")
                }
                Result.ConfirmedAbsent -> itemsRead++
            }
        }

        found?.let { return it }
        if (itemsRead == 0) {
            return inconclusive("no_items_read")
        }
        Log.d(TAG, "[BAG] sweep clean items=$itemsRead want=${want.toStorage()} — pet not in bag")
        return Result.ConfirmedAbsent
    }

    /**
     * Reads one open popup. [Result.ConfirmedAbsent] here means "read fine, not the
     * pet"; the sweep keeps going.
     */
    private suspend fun sweepSlot(
        slot: Int,
        want: PetType,
        buttons: List<Point>,
        frameWidth: Int,
        frameHeight: Int,
    ): Result {
        val raw = readItemName(frameWidth, frameHeight)
        if (raw.isNullOrBlank()) {
            return inconclusive("name_unreadable slot=$slot")
        }
        val pet = classifyPetName(raw)
        Log.d(TAG, "[BAG] slot=$slot raw=\"$raw\" pet=${pet?.toStorage() ?: "-"}")
        if (pet == want) {
            // Equip is the rightmost action; Recycle sits to its left and would
            // destroy the pet, so pick by position rather than by template score.
            val equipAt = buttons.last()
            Log.d(TAG, "[BAG] slot=$slot holds ${want.toStorage()} equipAt=(${equipAt.x},${equipAt.y})")
            return Result.Found(slot, equipAt)
        }
        return Result.ConfirmedAbsent
    }

    private fun inconclusive(reason: String): Result.Inconclusive {
        Log.w(TAG, "[BAG] sweep inconclusive reason=$reason — purchase NOT authorised")
        return Result.Inconclusive(reason)
    }

    /** Centre of bag [slot] in screen pixels, row-major over [GRID_COLS]. */
    fun slotCenter(slot: Int, frameWidth: Int, frameHeight: Int): Pair<Int, Int> {
        val (refX, refY) = slotCenterRef(slot)
        return RefCoords.scalePoint(refX, refY, frameWidth, frameHeight)
    }

    /** Pure grid math, in 2560×1440 reference space. */
    fun slotCenterRef(slot: Int): Pair<Int, Int> {
        val row = slot / GRID_COLS
        val col = slot % GRID_COLS
        val half = GRID_PITCH_REF / 2
        return (GRID_ORIGIN_X_REF + col * GRID_PITCH_REF + half) to
            (GRID_ORIGIN_Y_REF + row * GRID_PITCH_REF + half)
    }

    /**
     * Maps a popup item name to a pet, tolerating OCR slips (`Imp`→`lmp`).
     *
     * Deliberately generous: an unrecognised pet would let the sweep report
     * [Result.ConfirmedAbsent] and authorise a purchase, so near-misses must
     * still resolve. Length is compared first, which keeps long unrelated names
     * such as `Blessed MP Potion` or the `Archangel` gear title from matching.
     */
    fun classifyPetName(raw: String): PetType? {
        val n = raw.lowercase().filter { it.isLetter() }
        if (n.isEmpty()) return null
        if (nearly(n, "imp", maxDistance = 1)) return PetType.IMP
        if (nearly(n, "angel", maxDistance = 1)) return PetType.ANGEL
        if (nearly(n, "guardianangel", maxDistance = 2)) return PetType.ANGEL
        return null
    }

    private fun nearly(candidate: String, target: String, maxDistance: Int): Boolean {
        if (kotlin.math.abs(candidate.length - target.length) > maxDistance) return false
        return levenshtein(candidate, target) <= maxDistance
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val sub = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /**
     * Polls until the popup actions appear.
     *
     * Returns the button centres once visible, an empty list when the whole window
     * elapsed with a readable screen and no popup (a genuinely empty slot), or null
     * when no frame could be read at all — the caller must not treat that as empty.
     */
    private suspend fun awaitActionButtons(frameWidth: Int, frameHeight: Int): List<Point>? {
        val deadline = System.currentTimeMillis() + POPUP_TIMEOUT_MS
        var sawFrame = false
        while (System.currentTimeMillis() < deadline) {
            val buttons = readActionButtons(frameWidth, frameHeight)
            if (buttons != null) {
                sawFrame = true
                if (buttons.isNotEmpty()) return buttons
            }
            delay(POPUP_POLL_MS)
        }
        return if (sawFrame) emptyList() else null
    }

    /**
     * Centres of the orange popup actions, left to right. Empty when no popup is
     * open, which is how an empty slot is detected.
     */
    private suspend fun readActionButtons(frameWidth: Int, frameHeight: Int): List<Point>? {
        val roi = ScaledRoi.fromRefRect(
            ACTIONS_LEFT_REF,
            ACTIONS_TOP_REF,
            ACTIONS_RIGHT_REF,
            ACTIONS_BOTTOM_REF,
            frameWidth,
            frameHeight,
        )
        val frame = NavigationVision.captureFrame() ?: return null
        return try {
            orangeButtonCentres(frame, roi)
        } finally {
            frame.recycle()
        }
    }

    private fun orangeButtonCentres(frame: Bitmap, roi: Rect): List<Point> {
        val left = roi.left.coerceIn(0, frame.width - 1)
        val top = roi.top.coerceIn(0, frame.height - 1)
        val width = roi.width().coerceAtMost(frame.width - left)
        val height = roi.height().coerceAtMost(frame.height - top)
        if (width <= 0 || height <= 0) return emptyList()

        val pixels = IntArray(width * height)
        frame.getPixels(pixels, 0, width, left, top, width, height)

        val hitsPerColumn = IntArray(width)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            if (isButtonOrange(r, g, b)) {
                hitsPerColumn[i % width]++
            }
        }

        val minRun = MIN_BUTTON_WIDTH_PX * frame.width / 1280
        val centres = mutableListOf<Point>()
        var runStart = -1
        for (x in 0..width) {
            val filled = x < width && hitsPerColumn[x] > 2
            if (filled && runStart < 0) {
                runStart = x
            } else if (!filled && runStart >= 0) {
                if (x - runStart >= minRun) {
                    centres += Point(left + (runStart + x) / 2, top + height / 2)
                }
                runStart = -1
            }
        }
        return centres
    }

    /** Warm orange of the Recycle / Equip plates. */
    internal fun isButtonOrange(r: Int, g: Int, b: Int): Boolean =
        r > 170 && g in 100..200 && b < 110

    private suspend fun readItemName(frameWidth: Int, frameHeight: Int): String? {
        val roi = ScaledRoi.fromRefRect(
            NAME_LEFT_REF,
            NAME_TOP_REF,
            NAME_RIGHT_REF,
            NAME_BOTTOM_REF,
            frameWidth,
            frameHeight,
        )
        val frame = NavigationVision.captureFrame() ?: return null
        val crop = try {
            cropOrNull(frame, roi)
        } finally {
            frame.recycle()
        } ?: return null

        return try {
            // The popup is translucent, so plain greyscale keeps the Gear panel
            // behind it. Isolating the coloured glyphs first reads far better.
            readText(isolateGlyphs(crop))?.takeIf { it.isNotBlank() }
                ?: readText(upscaleGrey(crop))
        } finally {
            crop.recycle()
        }
    }

    private fun cropOrNull(frame: Bitmap, roi: Rect): Bitmap? {
        val left = roi.left.coerceIn(0, frame.width)
        val top = roi.top.coerceIn(0, frame.height)
        val right = roi.right.coerceIn(left, frame.width)
        val bottom = roi.bottom.coerceIn(top, frame.height)
        val w = right - left
        val h = bottom - top
        if (w < 24 || h < 10) return null
        return Bitmap.createBitmap(frame, left, top, w, h)
    }

    /** Keep saturated/bright glyphs, drop the translucent panel behind them. */
    private fun isolateGlyphs(crop: Bitmap): Bitmap {
        val w = crop.width
        val h = crop.height
        val pixels = IntArray(w * h)
        crop.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            val glyph = mx > 120 && (mx - mn > 45 || mx > 200)
            pixels[i] = if (glyph) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.setPixels(pixels, 0, w, 0, 0, w, h)
        return upscale(mask).also { if (it !== mask) mask.recycle() }
    }

    private fun upscaleGrey(crop: Bitmap): Bitmap {
        val bgr = OpenCvBitmapConverter.bitmapToBgrMat(crop)
        val grey = Mat()
        Imgproc.cvtColor(bgr, grey, Imgproc.COLOR_BGR2GRAY)
        bgr.release()
        val rgba = Mat()
        Imgproc.cvtColor(grey, rgba, Imgproc.COLOR_GRAY2RGBA)
        grey.release()
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return upscale(bitmap).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun upscale(bitmap: Bitmap): Bitmap {
        val src = OpenCvBitmapConverter.bitmapToBgrMat(bitmap)
        val big = Mat()
        Imgproc.resize(src, big, Size(), OCR_UPSCALE, OCR_UPSCALE, Imgproc.INTER_CUBIC)
        src.release()
        val rgba = Mat()
        Imgproc.cvtColor(big, rgba, Imgproc.COLOR_BGR2RGBA)
        big.release()
        val out = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(rgba, out)
        rgba.release()
        return out
    }

    private suspend fun readText(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (continuation.isActive) {
                            continuation.resume(result.text.replace('\n', ' ').trim())
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "[BAG] ML Kit failed: ${error.message}")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
