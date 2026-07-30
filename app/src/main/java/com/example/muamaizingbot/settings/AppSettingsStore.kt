package com.example.muamaizingbot.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val tag: String, val nativeLabel: String) {
    SPANISH("es", "Español"),
    PORTUGUESE("pt", "Português"),
    ENGLISH("en", "English");

    companion object {
        fun fromTag(tag: String): AppLanguage {
            val normalized = tag.trim().lowercase().substringBefore('-')
            return entries.firstOrNull { it.tag == normalized } ?: SPANISH
        }
    }
}

/**
 * App-scoped UI settings (not per BotProfile).
 */
object AppSettingsStore {

    private const val PREFS = "app_settings"
    private const val KEY_LANGUAGE = "ui_language"

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    private val _language = MutableStateFlow(AppLanguage.SPANISH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _language.value = AppLanguage.fromTag(p.getString(KEY_LANGUAGE, AppLanguage.SPANISH.tag).orEmpty())
    }

    /** Safe for [android.app.Activity.attachBaseContext] / [Application.attachBaseContext] before [init]. */
    fun languageTag(context: Context): String {
        prefs?.let { return AppLanguage.fromTag(it.getString(KEY_LANGUAGE, AppLanguage.SPANISH.tag).orEmpty()).tag }
        // During Application.attachBaseContext, applicationContext may still be null.
        val storage = context.applicationContext ?: context
        val p = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppLanguage.fromTag(p.getString(KEY_LANGUAGE, AppLanguage.SPANISH.tag).orEmpty()).tag
    }

    fun current(): AppLanguage = _language.value

    fun setLanguage(language: AppLanguage) {
        requirePrefs().edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    private fun requirePrefs(): android.content.SharedPreferences {
        return prefs ?: error("AppSettingsStore.init() required")
    }
}
