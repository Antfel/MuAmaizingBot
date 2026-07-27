package com.example.muamaizingbot.telegram

import android.content.Context

/**
 * App-scoped Telegram alert settings (not per BotProfile).
 * Bot token is embedded — user only configures chat ID.
 */
object TelegramStore {

    private const val PREFS = "telegram_settings"
    private const val KEY_CHAT_ID = "chat_id"
    private const val KEY_ALERTS_ENABLED = "alerts_enabled"

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun requirePrefs(): android.content.SharedPreferences {
        return prefs ?: error("TelegramStore.init() required")
    }

    fun chatId(): String {
        return requirePrefs().getString(KEY_CHAT_ID, "")?.trim().orEmpty()
    }

    fun setChatId(chatId: String) {
        requirePrefs().edit().putString(KEY_CHAT_ID, chatId.trim()).apply()
    }

    fun alertsEnabled(): Boolean {
        return requirePrefs().getBoolean(KEY_ALERTS_ENABLED, true)
    }

    fun setAlertsEnabled(enabled: Boolean) {
        requirePrefs().edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
    }

    fun isReadyForSend(): Boolean {
        return alertsEnabled() && chatId().isNotBlank() && TelegramEndpoint.isConfigured()
    }
}
