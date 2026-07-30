package com.example.muamaizingbot.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context): Context {
        val tag = AppSettingsStore.languageTag(context)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
