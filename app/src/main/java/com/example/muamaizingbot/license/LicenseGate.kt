package com.example.muamaizingbot.license

import android.content.Context
import android.util.Log
import com.example.muamaizingbot.BuildConfig
import com.example.muamaizingbot.R
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.content.MapContentSync
import com.example.muamaizingbot.settings.UiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Session lease against license-admin: acquire / heartbeat / release.
 */
object LicenseGate {

    private const val TAG = "LicenseGate"
    /** How often we ask the server if the lease is still valid (admin kill reacts within this). */
    private const val HEARTBEAT_INTERVAL_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _hasSession = MutableStateFlow(false)
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    @Volatile
    private var heartbeatJob: Job? = null

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        // Store is initialized separately; gate is ready after that.
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    /**
     * Acquire a lease (or renew same device). Starts heartbeat on success.
     * Call from a background dispatcher / coroutine.
     */
    suspend fun acquire(): Boolean = withContext(Dispatchers.IO) {
        val key = LicenseStore.licenseKey()
        if (key.isBlank()) {
            setFail("MISSING_KEY", LicenseApiClient.userMessageForCode("MISSING_KEY"))
            return@withContext false
        }
        val base = LicenseStore.serverUrl()
        val deviceId = LicenseStore.deviceId()
        val version = BuildConfig.VERSION_NAME

        Log.i(TAG, "acquire device=${deviceId.take(8)}… base=$base")
        when (val result = LicenseApiClient.acquire(base, key, deviceId, version)) {
            is LicenseApiResult.Acquired -> {
                _sessionId.value = result.sessionId
                _hasSession.value = true
                _userMessage.value = null
                startHeartbeat(result.sessionId, key, base)
                Log.i(
                    TAG,
                    "acquire ok session=${result.sessionId.take(8)}… " +
                        "active=${result.activeSessions}/${result.maxSessions}",
                )
                // Non-blocking map pack sync; baseline APK assets remain usable on failure.
                scope.launch(Dispatchers.IO) {
                    MapContentSync.sync(base, key, result.sessionId)
                }
                true
            }
            is LicenseApiResult.Failed -> {
                setFail(result.code, result.message)
                false
            }
            is LicenseApiResult.Ok -> {
                setFail("INVALID_RESPONSE", UiStrings.get(R.string.license_err_unexpected))
                false
            }
        }
    }

    /** Fire-and-forget release; stops heartbeat. */
    fun releaseAsync(reason: String = "stop") {
        val sid = _sessionId.value
        val key = LicenseStore.licenseKey()
        stopHeartbeat()
        _sessionId.value = null
        _hasSession.value = false
        if (sid.isNullOrBlank() || key.isBlank()) {
            Log.d(TAG, "release skipped (no session) reason=$reason")
            return
        }
        val base = LicenseStore.serverUrl()
        scope.launch(Dispatchers.IO) {
            Log.i(TAG, "release session=${sid.take(8)}… reason=$reason")
            when (val result = LicenseApiClient.release(base, sid, key)) {
                is LicenseApiResult.Ok -> Log.d(TAG, "release ok")
                is LicenseApiResult.Failed ->
                    Log.w(TAG, "release failed code=${result.code} msg=${result.message}")
                else -> Unit
            }
        }
    }

    private fun setFail(code: String, message: String) {
        Log.w(TAG, "fail code=$code msg=$message")
        _userMessage.value = message.ifBlank { LicenseApiClient.userMessageForCode(code) }
        _hasSession.value = false
        _sessionId.value = null
        stopHeartbeat()
    }

    private fun startHeartbeat(sessionId: String, licenseKey: String, baseUrl: String) {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val sid = _sessionId.value ?: break
                if (sid != sessionId) break
                when (
                    val result = withContext(Dispatchers.IO) {
                        LicenseApiClient.heartbeat(baseUrl, sid, licenseKey)
                    }
                ) {
                    is LicenseApiResult.Ok -> Log.d(TAG, "heartbeat ok")
                    is LicenseApiResult.Failed -> {
                        Log.w(TAG, "heartbeat failed code=${result.code}")
                        if (isFatalLeaseError(result.code)) {
                            handleLeaseRevoked(result.code, result.message)
                            break
                        }
                        // Transient / network: keep trying.
                        _userMessage.value = result.message
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun isFatalLeaseError(code: String): Boolean {
        return code == "SESSION_EXPIRED" ||
            code == "SESSION_NOT_FOUND" ||
            code == "INVALID_LICENSE" ||
            code == "REVOKED" ||
            code == "EXPIRED"
    }

    /**
     * Admin kill / expired lease: clear local session and force bot Stop (no auto-restart).
     */
    private fun handleLeaseRevoked(code: String, message: String) {
        val userMsg = when (code) {
            "SESSION_NOT_FOUND", "SESSION_EXPIRED" ->
                UiStrings.get(R.string.license_err_session_released)
            else -> message.ifBlank { LicenseApiClient.userMessageForCode(code) }
        }
        Log.w(TAG, "lease revoked code=$code → stop bot")
        stopHeartbeat()
        _sessionId.value = null
        _hasSession.value = false
        _userMessage.value = userMsg
        BotController.stopFromLicenseRevoke(userMsg)
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
