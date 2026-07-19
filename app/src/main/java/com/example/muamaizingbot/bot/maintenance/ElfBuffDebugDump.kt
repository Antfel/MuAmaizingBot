package com.example.muamaizingbot.bot.maintenance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.example.muamaizingbot.vision.debug.DebugFrameExporter
import com.example.muamaizingbot.vision.navigation.NavigationVision
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Step-by-step visual debug for elf giver: detection masks, nameplates, planned taps.
 * Saved under `files/debug_capture/elf_buff_<timestamp>/` on device.
 * Pull: `ADB_DEVICE=emulator-5574 ./scripts/pull_debug_capture.sh`
 */
object ElfBuffDebugDump {

    private const val TAG = "ElfBuffDebug"
    @Volatile
    var enabled: Boolean = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sessionDir: File? = null

    private val step = AtomicInteger(0)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun beginSession(reason: String): File? {
        if (!enabled) return null
        val ctx = appContext ?: return null
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(DebugFrameExporter.exportDirectory(ctx), "elf_buff_$ts").apply { mkdirs() }
        sessionDir = dir
        step.set(0)
        Log.i(TAG, "[ELF_DEBUG] session start reason=$reason dir=${dir.absolutePath}")
        return dir
    }

    fun sessionPath(): String? = sessionDir?.absolutePath

    suspend fun saveRaw(label: String) {
        if (!enabled || sessionDir == null) return
        val frame = NavigationVision.captureFrame() ?: return
        try {
            saveBitmap(frame, label)
        } finally {
            frame.recycle()
        }
    }

    suspend fun saveRawFromBitmap(frame: Bitmap, label: String) {
        if (!enabled || sessionDir == null) return
        saveBitmap(frame, label)
    }

    suspend fun saveBitmapDirect(bitmap: Bitmap, label: String) {
        if (!enabled || sessionDir == null) return
        saveBitmap(bitmap, label)
    }

    suspend fun saveAnnotated(
        label: String,
        annotate: (Canvas, Bitmap) -> Unit,
    ) {
        if (!enabled || sessionDir == null) return
        val frame = NavigationVision.captureFrame() ?: return
        try {
            val mutable = frame.copy(Bitmap.Config.ARGB_8888, true)
            try {
                annotate(Canvas(mutable), mutable)
                saveBitmap(mutable, label)
            } finally {
                mutable.recycle()
            }
        } finally {
            frame.recycle()
        }
    }

    suspend fun saveDetection(
        frame: Bitmap,
        hits: List<NearbyAllyDetector.NameplateHit>,
        maskOverlay: Bitmap?,
        label: String = "03_detect",
    ) {
        if (!enabled || sessionDir == null) return
        withContext(Dispatchers.IO) {
            maskOverlay?.let { saveBitmap(it, "02_masks_gold_white_green") }
            val mutable = frame.copy(Bitmap.Config.ARGB_8888, true)
            try {
                val canvas = Canvas(mutable)
                drawHud(canvas, mutable, "DETECT hits=${hits.size}")
                // Exclusion zones (no nameplate / no focus).
                val zoneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(55, 255, 0, 0)
                    style = Paint.Style.FILL
                }
                val zoneStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                val zoneLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    textSize = 14f
                    setShadowLayer(2f, 1f, 1f, Color.BLACK)
                }
                for ((i, rect) in ElfBuffExclusionZones.scaled(mutable.width, mutable.height).withIndex()) {
                    canvas.drawRect(RectF(rect), zoneFill)
                    canvas.drawRect(RectF(rect), zoneStroke)
                    canvas.drawText(
                        ElfBuffExclusionZones.BASE_ZONES[i].id,
                        rect.left + 4f,
                        rect.top + 16f,
                        zoneLabel,
                    )
                }
                val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.CYAN
                    style = Paint.Style.STROKE
                    strokeWidth = 2.5f
                }
                val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.RED
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.YELLOW
                    strokeWidth = 2f
                }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 18f
                    setShadowLayer(2f, 1f, 1f, Color.BLACK)
                }
                for ((i, hit) in hits.withIndex()) {
                    val left = (hit.centerX - hit.width / 2).toFloat()
                    val top = (hit.centerY - hit.height / 2).toFloat()
                    canvas.drawRect(
                        RectF(left, top, left + hit.width, top + hit.height),
                        platePaint,
                    )
                    val fx = hit.focusTapX(mutable.width).toFloat()
                    val fy = hit.focusTapY(mutable.height).toFloat()
                    canvas.drawLine(hit.centerX.toFloat(), hit.plateBottom.toFloat(), fx, fy, linePaint)
                    canvas.drawCircle(fx, fy, 14f, focusPaint)
                    canvas.drawLine(fx - 18f, fy, fx + 18f, fy, focusPaint)
                    canvas.drawLine(fx, fy - 18f, fx, fy + 18f, focusPaint)
                    canvas.drawText(
                        "#${i + 1} plate=(${hit.centerX},${hit.centerY}) bottom=${hit.plateBottom}",
                        left,
                        (top - 6).coerceAtLeast(20f),
                        textPaint,
                    )
                    canvas.drawText(
                        "FOCUS tap=(${fx.toInt()},${fy.toInt()})",
                        fx + 16f,
                        fy - 10f,
                        textPaint,
                    )
                }
                saveBitmap(mutable, label)
            } finally {
                mutable.recycle()
            }
        }
    }

    suspend fun saveTapPlan(
        label: String,
        taps: List<TapMark>,
        title: String,
    ) {
        saveAnnotated(label) { canvas, bmp ->
            drawHud(canvas, bmp, title)
            for (tap in taps) {
                drawTap(canvas, tap)
            }
        }
    }

    data class TapMark(
        val x: Int,
        val y: Int,
        val label: String,
        val color: Int = Color.RED,
    )

    private fun drawTap(canvas: Canvas, tap: TapMark) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tap.color
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tap.color
            style = Paint.Style.FILL
            alpha = 80
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        val x = tap.x.toFloat()
        val y = tap.y.toFloat()
        canvas.drawCircle(x, y, 16f, fill)
        canvas.drawCircle(x, y, 16f, paint)
        canvas.drawLine(x - 22f, y, x + 22f, y, paint)
        canvas.drawLine(x, y - 22f, x, y + 22f, paint)
        canvas.drawText("${tap.label} (${tap.x},${tap.y})", x + 20f, y - 12f, text)
    }

    private fun drawHud(canvas: Canvas, bmp: Bitmap, title: String) {
        val bg = Paint().apply { color = Color.argb(180, 0, 0, 0) }
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), 36f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GREEN
            textSize = 22f
        }
        canvas.drawText(title, 10f, 26f, text)
    }

    private suspend fun saveBitmap(bitmap: Bitmap, label: String) {
        val dir = sessionDir ?: return
        withContext(Dispatchers.IO) {
            val n = step.incrementAndGet()
            val safe = label.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "%02d_%s.png".format(n, safe))
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.i(TAG, "[ELF_DEBUG] saved ${file.name} ${bitmap.width}x${bitmap.height}")
        }
    }
}
