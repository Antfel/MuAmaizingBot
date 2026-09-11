package com.example.muamaizingbot.vision.focus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tiny 1-NN portrait classifier for the top-center focus HUD face slot.
 *
 * Labels: [Kind.PJ] (player portrait), [Kind.BOSS] (boss/golden emblem — same art),
 * [Kind.EMPTY] (no focus). Replaces `boss_focus` / `focus_clear_x` template probes
 * for fight validation; skull acquire / focus_player world taps stay template-based.
 *
 * Bank: `assets/vision/focus_portrait_bank.json` (export via
 * `scripts/export_focus_portrait_bank.py`).
 */
object FocusPortraitClassifier {

    private const val TAG = "FocusPortrait"
    private const val ASSET = "vision/focus_portrait_bank.json"
    private const val FACE_SIZE = 32
    private const val PROBE_EVERY = 25

    enum class Kind {
        PJ,
        BOSS,
        EMPTY,
        UNKNOWN,
    }

    private data class Sample(val kind: Kind, val feat: FloatArray)

    @Volatile
    private var ready = false
    private var refW = 1280
    private var refH = 720
    private var faceL = 532
    private var faceT = 26
    private var faceR = 564
    private var faceB = 58
    private var scale = floatArrayOf(1f, 1f, 1f, 1f)
    private var bank: List<Sample> = emptyList()

    private val probeCount = AtomicInteger(0)
    private val probeNanos = AtomicLong(0)

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            try {
                val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                refW = root.getInt("ref_w")
                refH = root.getInt("ref_h")
                val face = root.getJSONArray("face")
                faceL = face.getInt(0)
                faceT = face.getInt(1)
                faceR = face.getInt(2)
                faceB = face.getInt(3)
                val sc = root.getJSONArray("scale")
                scale = FloatArray(4) { i -> sc.getDouble(i).toFloat() }
                val samples = root.getJSONArray("samples")
                val loaded = ArrayList<Sample>(samples.length())
                var pj = 0
                var boss = 0
                var empty = 0
                for (i in 0 until samples.length()) {
                    val row = samples.getJSONObject(i)
                    val kind = when (row.getString("label")) {
                        "pj" -> Kind.PJ.also { pj++ }
                        "boss" -> Kind.BOSS.also { boss++ }
                        "empty" -> Kind.EMPTY.also { empty++ }
                        else -> continue
                    }
                    val featArr = row.getJSONArray("feat")
                    val feat = FloatArray(4) { j -> featArr.getDouble(j).toFloat() }
                    loaded.add(Sample(kind, feat))
                }
                bank = loaded
                ready = bank.isNotEmpty()
                Log.i(
                    TAG,
                    "bank loaded n=${bank.size} pj=$pj boss=$boss empty=$empty ready=$ready",
                )
            } catch (t: Throwable) {
                Log.e(TAG, "failed to load $ASSET: ${t.message}", t)
                ready = false
            }
        }
    }

    fun isReady(): Boolean = ready

    /** Classify a full-screen frame. Recycles the internal 32×32 crop, not [frame]. */
    fun classify(frame: Bitmap): Kind {
        if (!ready || bank.isEmpty()) return Kind.UNKNOWN
        val face = faceCrop32(frame) ?: return Kind.UNKNOWN
        return try {
            nearest(features(face))
        } finally {
            if (face !== frame) face.recycle()
        }
    }

    /**
     * Classify the live HUD face slot.
     * Copies only the ~32×32 ROI from the capture buffer — not a full 1280×720 frame.
     */
    suspend fun classifyLatest(): Kind {
        if (!ready || bank.isEmpty()) return Kind.UNKNOWN
        val size = ScreenCaptureManager.peekLatestBitmapSize() ?: return Kind.UNKNOWN
        val rect = faceRectPx(size.first, size.second) ?: return Kind.UNKNOWN
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val crop = ScreenCaptureManager.copyRegion(rect[0], rect[1], rect[2], rect[3])
            ?: return Kind.UNKNOWN
        val face = scaleToFace(crop)
        return try {
            val kind = nearest(features(face))
            noteProbe(kind, startedAt, size.first, size.second, rect[2], rect[3])
            kind
        } finally {
            face.recycle()
        }
    }

    suspend fun isPjFocus(): Boolean = classifyLatest() == Kind.PJ

    suspend fun isBossFocus(): Boolean = classifyLatest() == Kind.BOSS

    private fun nearest(feat: FloatArray): Kind {
        var best: Kind = Kind.UNKNOWN
        var bestD = Float.POSITIVE_INFINITY
        for (sample in bank) {
            var sum = 0f
            for (i in 0 until 4) {
                val d = (feat[i] - sample.feat[i]) * scale[i]
                sum += d * d
            }
            val dist = sqrt(sum)
            if (dist < bestD) {
                bestD = dist
                best = sample.kind
            }
        }
        return best
    }

    /**
     * Face slot on a capture of [frameW]×[frameH]: `[left, top, width, height]`.
     * At 1280×720 this is a 32×32 crop (no scale).
     */
    internal fun faceRectPx(frameW: Int, frameH: Int): IntArray? {
        if (frameW <= 0 || frameH <= 0 || refW <= 0 || refH <= 0) return null
        val sx = frameW.toFloat() / refW.toFloat()
        val sy = frameH.toFloat() / refH.toFloat()
        var l = (faceL * sx).toInt()
        var t = (faceT * sy).toInt()
        var r = (faceR * sx).toInt()
        var b = (faceB * sy).toInt()
        l = l.coerceIn(0, frameW - 1)
        t = t.coerceIn(0, frameH - 1)
        r = r.coerceIn(l + 1, frameW)
        b = b.coerceIn(t + 1, frameH)
        return intArrayOf(l, t, r - l, b - t)
    }

    private fun faceCrop32(frame: Bitmap): Bitmap? {
        val rect = faceRectPx(frame.width, frame.height) ?: return null
        val crop = Bitmap.createBitmap(frame, rect[0], rect[1], rect[2], rect[3])
        return scaleToFace(crop)
    }

    private fun scaleToFace(crop: Bitmap): Bitmap {
        if (crop.width == FACE_SIZE && crop.height == FACE_SIZE) {
            return crop
        }
        val scaled = Bitmap.createScaledBitmap(crop, FACE_SIZE, FACE_SIZE, true)
        if (scaled !== crop) {
            crop.recycle()
        }
        return scaled
    }

    private fun noteProbe(
        kind: Kind,
        startedAtNanos: Long,
        frameW: Int,
        frameH: Int,
        cropW: Int,
        cropH: Int,
    ) {
        val elapsedNs = (SystemClock.elapsedRealtimeNanos() - startedAtNanos).coerceAtLeast(0L)
        val n = probeCount.incrementAndGet()
        val totalNs = probeNanos.addAndGet(elapsedNs)
        if (n == 1 || n % PROBE_EVERY == 0) {
            val lastMs = elapsedNs / 1_000_000.0
            val avgMs = totalNs / n / 1_000_000.0
            val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
            val javaUsedMb =
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) /
                    (1024.0 * 1024.0)
            Log.i(
                TAG,
                "[FOCUS] probe n=$n kind=$kind lastMs=${"%.2f".format(lastMs)} " +
                    "avgMs=${"%.2f".format(avgMs)} crop=${cropW}x${cropH} " +
                    "frame=${frameW}x${frameH} nativeHeapMb=${"%.1f".format(nativeMb)} " +
                    "javaUsedMb=${"%.1f".format(javaUsedMb)}",
            )
        }
    }

    /**
     * Match Python [scripts/watch_focus_portrait.py] features:
     * lum_var, edge, skin_frac, gold_frac (OpenCV HSV units).
     */
    private fun features(bgr32: Bitmap): FloatArray {
        val n = FACE_SIZE * FACE_SIZE
        val pixels = IntArray(n)
        bgr32.getPixels(pixels, 0, FACE_SIZE, 0, 0, FACE_SIZE, FACE_SIZE)

        val gray = FloatArray(n)
        var sum = 0.0
        for (i in 0 until n) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            // OpenCV BGR2GRAY
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            gray[i] = y
            sum += y
        }
        val mean = (sum / n).toFloat()
        var varAcc = 0.0
        for (i in 0 until n) {
            val d = gray[i] - mean
            varAcc += d * d
        }
        val lumVar = (varAcc / n).toFloat()

        // Python: mean(|diff axis1|) + mean(|diff axis0|)
        var gxAcc = 0.0
        var gxN = 0
        for (y in 0 until FACE_SIZE) {
            val row = y * FACE_SIZE
            for (x in 0 until FACE_SIZE - 1) {
                gxAcc += abs(gray[row + x + 1] - gray[row + x])
                gxN++
            }
        }
        var gyAcc = 0.0
        var gyN = 0
        for (y in 0 until FACE_SIZE - 1) {
            val row = y * FACE_SIZE
            val row2 = (y + 1) * FACE_SIZE
            for (x in 0 until FACE_SIZE) {
                gyAcc += abs(gray[row2 + x] - gray[row + x])
                gyN++
            }
        }
        val edgeExact = (gxAcc / gxN + gyAcc / gyN).toFloat()

        var skin = 0
        var gold = 0
        val hsv = FloatArray(3)
        for (i in 0 until n) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            if (r > g && r >= b - 5 && r in 55..235 && (r - b) > 12) {
                skin++
            }
            Color.RGBToHSV(r, g, b, hsv)
            // Android H 0–360, S/V 0–1 → OpenCV H 0–179, S/V 0–255
            val h = hsv[0] / 2f
            val s = hsv[1] * 255f
            val v = hsv[2] * 255f
            if (h in 12f..35f && s >= 70f && v >= 90f) {
                gold++
            }
        }
        val skinFrac = skin.toFloat() / n.toFloat()
        val goldFrac = gold.toFloat() / n.toFloat()
        return floatArrayOf(lumVar, edgeExact, skinFrac, goldFrac)
    }
}
