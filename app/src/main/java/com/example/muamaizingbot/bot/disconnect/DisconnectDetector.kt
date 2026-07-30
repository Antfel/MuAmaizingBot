package com.example.muamaizingbot.bot.disconnect

import android.util.Log
import com.example.muamaizingbot.R
import com.example.muamaizingbot.bot.BotDiagnosticJournal
import com.example.muamaizingbot.settings.UiStrings
import com.example.muamaizingbot.telegram.TelegramEndpoint
import com.example.muamaizingbot.telegram.TelegramNotifier
import com.example.muamaizingbot.telegram.TelegramSendResult
import com.example.muamaizingbot.telegram.TelegramStore
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects game disconnection / maintenance by checking for absence of in-world
 * combat HUD over consecutive checks.
 *
 * While the bot is navigating / opening map / buying potions / switching wire,
 * call [markBusy] so blank HUD does not count as disconnect.
 */
object DisconnectDetector {

    private const val TAG = "Disconnect"

    const val CHECK_INTERVAL_MS = 15_000L

    /** Consecutive blank checks before alert: 6 × 15s ≈ 90s (only when not busy). */
    private const val CONSECUTIVE_BLANK_THRESHOLD = 6

    private const val ALERT_COOLDOWN_MS = 10 * 60 * 1000L

    /** Default busy window after map/nav/shop/wire work. */
    const val DEFAULT_BUSY_HOLD_MS = 180_000L

    /**
     * Combat / character HUD only. Map/shop/close_x are NOT used here —
     * they false-positive on BlueStacks home or disappear during normal UI.
     */
    private val HUD_PROBES = listOf(
        "templates/mu/ui/auto_mode.png" to 0.55f,
        "templates/mu/ui/manual_mode.png" to 0.55f,
        "templates/mu/ui/dead_state.png" to 0.60f,
        "templates/mu/ui/common/elf_buff_icon.png" to 0.55f,
    )

    private var consecutiveBlanks = 0
    private var lastAlertMs = 0L
    private var alertSentThisSession = false
    private var loggedNotReady = false

    @Volatile
    private var busyUntilMs = 0L

    @Volatile
    private var busyReason: String = ""

    fun reset() {
        consecutiveBlanks = 0
        alertSentThisSession = false
        loggedNotReady = false
        busyUntilMs = 0L
        busyReason = ""
    }

    /**
     * Call when the bot starts (or continues) map/nav/wire/shop/inventory work.
     * Extends the suppress window; blanks are ignored and the counter resets.
     */
    fun markBusy(reason: String, holdMs: Long = DEFAULT_BUSY_HOLD_MS) {
        val until = System.currentTimeMillis() + holdMs.coerceAtLeast(5_000L)
        if (until > busyUntilMs) {
            busyUntilMs = until
        }
        busyReason = reason
        if (consecutiveBlanks > 0) {
            Log.d(TAG, "busy reset blanks=$consecutiveBlanks reason=$reason holdMs=$holdMs")
            consecutiveBlanks = 0
        } else {
            Log.d(TAG, "busy reason=$reason holdMs=$holdMs")
        }
    }

    fun isBusy(): Boolean = System.currentTimeMillis() < busyUntilMs

    suspend fun check(): Boolean {
        if (!TelegramStore.isReadyForSend()) {
            if (!loggedNotReady) {
                loggedNotReady = true
                Log.w(
                    TAG,
                    "alerts disabled or Telegram not configured " +
                        "(alerts=${TelegramStore.alertsEnabled()} " +
                        "chatIdSet=${TelegramStore.chatId().isNotBlank()} " +
                        "token=${TelegramEndpoint.isConfigured()})",
                )
            }
            return false
        }
        loggedNotReady = false

        if (isBusy()) {
            val leftSec = ((busyUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            Log.d(TAG, "check skip busy reason=$busyReason left=${leftSec}s")
            consecutiveBlanks = 0
            return false
        }

        val frame = NavigationVision.captureFrame()
        if (frame == null) {
            noteBlank("no_frame")
            return maybeAlert(UiStrings.get(R.string.telegram_alert_no_capture))
        }

        val hit = HUD_PROBES.firstOrNull { (path, thresh) ->
            NavigationVision.findOnFrame(frame, path, thresh) != null
        }

        if (hit != null) {
            Log.d(TAG, "check alive=true probe=${hit.first} blanks=$consecutiveBlanks")
            if (consecutiveBlanks > 0) {
                Log.d(TAG, "HUD ok — recovered after $consecutiveBlanks blank checks")
            }
            if (alertSentThisSession) {
                sendRecoveryNotice()
            }
            consecutiveBlanks = 0
            alertSentThisSession = false
            return false
        }

        noteBlank("no_hud")
        return maybeAlert(
            UiStrings.get(
                R.string.telegram_alert_no_hud,
                consecutiveBlanks,
                CHECK_INTERVAL_MS / 1000,
            ),
        )
    }

    suspend fun notifyRepeatedErrors(reason: String) {
        if (!TelegramStore.isReadyForSend()) return
        val now = System.currentTimeMillis()
        if (now - lastAlertMs < ALERT_COOLDOWN_MS) return

        withContext(Dispatchers.IO) {
            val result = TelegramNotifier.sendDisconnectAlert(
                UiStrings.get(R.string.telegram_alert_repeated_errors, reason),
            )
            if (result is TelegramSendResult.Ok) {
                lastAlertMs = now
                alertSentThisSession = true
                Log.d(TAG, "Telegram alert sent: repeated errors")
                BotDiagnosticJournal.record(TAG, "alert sent: repeated errors — $reason")
            }
        }
    }

    private fun noteBlank(source: String) {
        consecutiveBlanks++
        Log.d(TAG, "blank #$consecutiveBlanks/$CONSECUTIVE_BLANK_THRESHOLD source=$source")
    }

    private suspend fun maybeAlert(message: String): Boolean {
        if (consecutiveBlanks < CONSECUTIVE_BLANK_THRESHOLD) return false

        val now = System.currentTimeMillis()
        if (now - lastAlertMs < ALERT_COOLDOWN_MS) {
            Log.d(TAG, "alert suppressed (cooldown)")
            return false
        }

        return withContext(Dispatchers.IO) {
            val result = TelegramNotifier.sendDisconnectAlert(message)
            if (result is TelegramSendResult.Ok) {
                lastAlertMs = now
                alertSentThisSession = true
                Log.i(TAG, "Telegram alert sent: $message")
                BotDiagnosticJournal.record(TAG, "alert sent: $message")
                true
            } else {
                Log.w(TAG, "Telegram alert failed: $result")
                false
            }
        }
    }

    private suspend fun sendRecoveryNotice() {
        withContext(Dispatchers.IO) {
            TelegramNotifier.sendDisconnectAlert(UiStrings.get(R.string.telegram_alert_recovered))
        }
        alertSentThisSession = false
        Log.d(TAG, "recovery notice sent")
        BotDiagnosticJournal.record(TAG, "recovery notice sent")
    }
}
