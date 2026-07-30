package com.example.muamaizingbot.settings

import android.content.Context
import androidx.annotation.StringRes

/**
 * Locale-aware [getString] for non-Compose code (license, Telegram, overlay gates).
 * Always wraps with [LocaleHelper] so the UI language preference applies.
 */
object UiStrings {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes id: Int): String {
        val ctx = localizedContext() ?: return ""
        return ctx.getString(id)
    }

    fun get(@StringRes id: Int, vararg formatArgs: Any): String {
        val ctx = localizedContext() ?: return ""
        return ctx.getString(id, *formatArgs)
    }

    private fun localizedContext(): Context? {
        val base = appContext ?: return null
        return LocaleHelper.wrap(base)
    }
}
