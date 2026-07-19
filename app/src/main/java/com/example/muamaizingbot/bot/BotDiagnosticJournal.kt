package com.example.muamaizingbot.bot

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Ring buffer of recent bot actions / loop branches for post-mortem dumps on ERROR.
 *
 * Pull dumps from device: `…/files/debug_capture/bot_error_*.txt`
 * Logcat tag: [BOT_DIAG]
 */
object BotDiagnosticJournal {

    private const val TAG = "BotDiag"
    private const val MAX_ENTRIES = 80
    private const val DUMP_TAG = "BOT_DIAG"

    data class Entry(
        val elapsedMs: Long,
        val tag: String,
        val message: String,
    )

    private val lock = Any()
    private val entries = ArrayDeque<Entry>(MAX_ENTRIES)
    private var appContext: Context? = null
    private val startedAtMs = System.currentTimeMillis()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun record(tag: String, message: String) {
        val entry = Entry(
            elapsedMs = System.currentTimeMillis() - startedAtMs,
            tag = tag,
            message = message,
        )
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    /** Dump recent trail + error reason to logcat and a file under debug_capture. */
    fun dumpError(reason: String) {
        val trail = snapshot()
        val header = buildString {
            appendLine("===== BOT_ERROR_DUMP =====")
            appendLine("time=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("error=$reason")
            appendLine("state=${BotController.state.value}")
            appendLine("trail=${trail.size} entries")
            appendLine("----- recent actions -----")
            for (e in trail) {
                appendLine("${formatElapsed(e.elapsedMs)} [${e.tag}] ${e.message}")
            }
            appendLine("===== END BOT_ERROR_DUMP =====")
        }

        header.lineSequence().forEach { line ->
            if (line.isNotEmpty()) {
                Log.e(DUMP_TAG, line)
            }
        }

        val ctx = appContext
        if (ctx == null) {
            Log.w(TAG, "[DIAG] no context; file dump skipped")
            return
        }
        try {
            val dir = File(ctx.getExternalFilesDir(null), "debug_capture").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "bot_error_$stamp.txt")
            file.writeText(header)
            Log.e(DUMP_TAG, "saved ${file.absolutePath}")
            record(TAG, "error dump saved ${file.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "[DIAG] file dump failed: ${t.message}")
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val m = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
        val s = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
        val ms = elapsedMs % 1000
        return "%02d:%02d.%03d".format(m, s, ms)
    }
}
