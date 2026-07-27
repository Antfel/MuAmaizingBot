package com.example.muamaizingbot.license

import android.content.Context
import java.util.UUID

/**
 * App-scoped license settings (not per BotProfile).
 * Server URL is embedded encrypted — see [LicenseEndpoint].
 */
object LicenseStore {

    private const val PREFS = "license_settings"
    private const val KEY_SERVER_URL_LEGACY = "server_url"
    private const val KEY_LICENSE_KEY = "license_key"
    private const val KEY_DEVICE_ID = "device_id"

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        if (p.getString(KEY_DEVICE_ID, null).isNullOrBlank()) {
            p.edit().putString(KEY_DEVICE_ID, UUID.randomUUID().toString()).apply()
        }
        // Drop any previously user-editable server URL from prefs.
        if (p.contains(KEY_SERVER_URL_LEGACY)) {
            p.edit().remove(KEY_SERVER_URL_LEGACY).apply()
        }
    }

    private fun requirePrefs(): android.content.SharedPreferences {
        return prefs ?: error("LicenseStore.init() required")
    }

    fun serverUrl(): String = LicenseEndpoint.serverUrl()

    fun licenseKey(): String {
        return requirePrefs().getString(KEY_LICENSE_KEY, "")?.trim().orEmpty()
    }

    fun setLicenseKey(key: String) {
        requirePrefs().edit().putString(KEY_LICENSE_KEY, key.trim()).apply()
    }

    fun deviceId(): String {
        return requirePrefs().getString(KEY_DEVICE_ID, null)
            ?: error("device_id missing — call LicenseStore.init()")
    }
}
