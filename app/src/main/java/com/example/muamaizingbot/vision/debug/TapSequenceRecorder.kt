package com.example.muamaizingbot.vision.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.muamaizingbot.capture.ScreenCaptureManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Records user (and bot) taps with an annotated frame + JSONL timeline.
 *
 * While recording, a fullscreen capture layer intercepts your finger, saves the
 * tap, then re-dispatches it into the game. Use the overlay Rec/Stop controls
 * (HUD stays above the capture layer).
 *
 * Output: `files/debug_capture/record_<timestamp>/`
 *   0001_userTap_user.png
 *   timeline.jsonl
 *   README.txt
 *
 * Pull: `ADB_DEVICE=emulator-5574 ./scripts/pull_debug_capture.sh`
 * (pull wipes remote by default so the next pull stays small)
 */
object TapSequenceRecorder {

    private const val TAG = "TapRecorder"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val step = AtomicInteger(0)
    private val recording = AtomicBoolean(false)

    @Volatile
    private var lastToggleAtMs = 0L

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sessionDir: File? = null

    @Volatile
    private var sessionStartElapsed = 0L

    /** Optional label for the next input (e.g. "focus", "skill_defense"). */
    @Volatile
    var pendingLabel: String? = null

    data class Status(
        val recording: Boolean = false,
        val eventCount: Int = 0,
        val detail: String = "Rec: off",
        val sessionPath: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isRecording(): Boolean = recording.get()

    fun toggle(): Boolean {
        val now = System.currentTimeMillis()
        // HUD restack / Compose recomposition can redeliver the same finger → Rec↔Stop flap.
        if (now - lastToggleAtMs < TOGGLE_DEBOUNCE_MS) {
            Log.d(TAG, "[REC] toggle ignored (debounce)")
            return recording.get()
        }
        lastToggleAtMs = now
        return if (recording.get()) {
            stop()
            false
        } else {
            start()
            true
        }
    }

    fun start(): File? {
        val ctx = appContext ?: return null
        if (recording.get()) return sessionDir
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(DebugFrameExporter.exportDirectory(ctx), "record_$ts").apply { mkdirs() }
        sessionDir = dir
        step.set(0)
        sessionStartElapsed = System.currentTimeMillis()
        recording.set(true)
        dir.resolve("README.txt").writeText(
            """
            Tap sequence recording (YOUR taps while Rec is ON)
            Started: $ts
            Overlay layer captures finger → saves frame → replays into game.
            Pull: ADB_DEVICE=emulator-5574 ./scripts/pull_debug_capture.sh
            Files: NNNN_<kind>_<label>.png + timeline.jsonl
            """.trimIndent() + "\n",
        )
        publish()
        Log.i(TAG, "[REC] start dir=${dir.absolutePath}")
        noteMarker("session_start")
        return dir
    }

    fun stop() {
        if (!recording.get()) return
        noteMarker("session_stop")
        recording.set(false)
        val path = sessionDir?.absolutePath
        publish()
        Log.i(TAG, "[REC] stop events=${step.get()} dir=$path")
    }

    fun noteMarker(label: String) {
        if (!recording.get()) return
        appendEvent(
            kind = "marker",
            label = label,
            x = null,
            y = null,
            durationMs = null,
            frameFile = null,
        )
    }

    /**
     * Capture current frame, draw tap marker, enqueue save.
     * Call **before** dispatching the gesture when possible.
     */
    fun noteTap(
        x: Int,
        y: Int,
        kind: String,
        durationMs: Long = 0L,
        label: String? = null,
    ) {
        if (!recording.get()) return
        val resolved = label ?: pendingLabel ?: kind
        pendingLabel = null
        val frame = ScreenCaptureManager.getLatestBitmap()
        scope.launch {
            try {
                val n = step.incrementAndGet()
                val safeKind = kind.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val safeLabel = resolved.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
                val fileName = "%04d_%s_%s.png".format(n, safeKind, safeLabel)
                var savedName: String? = null
                if (frame != null) {
                    try {
                        val annotated = annotate(frame, x, y, "$kind:$resolved")
                        try {
                            val out = File(sessionDir ?: return@launch, fileName)
                            FileOutputStream(out).use { fos ->
                                annotated.compress(Bitmap.CompressFormat.PNG, 90, fos)
                            }
                            savedName = fileName
                        } finally {
                            annotated.recycle()
                        }
                    } finally {
                        frame.recycle()
                    }
                }
                appendEvent(
                    kind = kind,
                    label = resolved,
                    x = x,
                    y = y,
                    durationMs = durationMs.takeIf { it > 0L },
                    frameFile = savedName,
                )
                publish()
                Log.d(TAG, "[REC] #$n $kind label=$resolved tap=($x,$y) frame=$savedName")
            } catch (t: Throwable) {
                Log.w(TAG, "[REC] noteTap failed: ${t.message}")
                frame?.recycle()
            }
        }
    }

    fun noteSwipe(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Long,
        label: String? = null,
    ) {
        if (!recording.get()) return
        val resolved = label ?: pendingLabel ?: "swipe"
        pendingLabel = null
        // Record as tap at start point with swipe metadata in JSON.
        noteTap(x1, y1, kind = "swipe", durationMs = durationMs, label = "$resolved->$x2,$y2")
    }

    private fun annotate(source: Bitmap, x: Int, y: Int, title: String): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val bg = Paint().apply { color = Color.argb(160, 0, 0, 0) }
        canvas.drawRect(0f, 0f, out.width.toFloat(), 32f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GREEN
            textSize = 20f
        }
        canvas.drawText(title, 8f, 22f, text)
        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        val fx = x.toFloat()
        val fy = y.toFloat()
        canvas.drawCircle(fx, fy, 16f, cross)
        canvas.drawLine(fx - 22f, fy, fx + 22f, fy, cross)
        canvas.drawLine(fx, fy - 22f, fx, fy + 22f, cross)
        val coord = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        canvas.drawText("($x,$y)", fx + 20f, fy - 12f, coord)
        return out
    }

    private fun appendEvent(
        kind: String,
        label: String,
        x: Int?,
        y: Int?,
        durationMs: Long?,
        frameFile: String?,
    ) {
        val dir = sessionDir ?: return
        val elapsed = System.currentTimeMillis() - sessionStartElapsed
        val obj = JSONObject()
            .put("t_ms", elapsed)
            .put("kind", kind)
            .put("label", label)
        if (x != null) obj.put("x", x)
        if (y != null) obj.put("y", y)
        if (durationMs != null) obj.put("duration_ms", durationMs)
        if (frameFile != null) obj.put("frame", frameFile)
        scope.launch {
            writeMutex.withLock {
                dir.resolve("timeline.jsonl").appendText(obj.toString() + "\n")
            }
        }
    }

    private fun publish() {
        _status.value = Status(
            recording = recording.get(),
            eventCount = step.get(),
            detail = if (recording.get()) {
                "Rec taps: ON (${step.get()})"
            } else {
                "Rec taps: off"
            },
            sessionPath = sessionDir?.absolutePath,
        )
    }

    private const val TOGGLE_DEBOUNCE_MS = 700L
}
