package com.example.muamaizingbot.bot.recovery

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.maintenance.MapCheckActions
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator
import com.example.muamaizingbot.bot.navigation.NavigationWaitActions
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository

/**
 * Control point to resume navigation after interrupted sequences (elf buff, potions, combat).
 *
 * Skip full re-navigation only when on the configured farm map **and** within farm-spot
 * coordinates. Same map but off-spot (typical after revive) still re-routes to the spot.
 */
object BotRecoveryActions {

    private const val TAG = "Recovery"
    private const val MAX_NAV_ATTEMPTS = 2
    private const val NAV_RETRY_COOLDOWN_MS = 15_000L

    private var lastFailedNavigateMs = 0L

    fun isNavCooldownActive(): Boolean {
        if (lastFailedNavigateMs == 0L) return false
        return System.currentTimeMillis() - lastFailedNavigateMs < NAV_RETRY_COOLDOWN_MS
    }

    fun navCooldownRemainingMs(): Long {
        if (!isNavCooldownActive()) return 0L
        return (NAV_RETRY_COOLDOWN_MS - (System.currentTimeMillis() - lastFailedNavigateMs))
            .coerceAtLeast(0L)
    }

    suspend fun navigateToFarmWithRetry(reason: String, ensureAuto: Boolean = true): Boolean {
        if (isAlreadyAtFarmPost(reason)) {
            return true
        }

        if (isNavCooldownActive()) {
            val waitSec = (navCooldownRemainingMs() + 999L) / 1000L
            Log.w(TAG, "[RECOVERY] nav cooldown ${waitSec}s reason=$reason")
            return false
        }

        repeat(MAX_NAV_ATTEMPTS) { attempt ->
            Log.d(TAG, "[RECOVERY] navigate attempt=${attempt + 1}/$MAX_NAV_ATTEMPTS reason=$reason")
            if (DeathActions.isDead()) {
                Log.d(TAG, "[RECOVERY] dead before navigate; reviving")
                if (!DeathActions.recoverIfDead()) {
                    return false
                }
            }
            if (NavigationOrchestrator.goToActiveFarmSpot(ensureAuto = ensureAuto)) {
                Log.d(TAG, "[RECOVERY] navigate ok reason=$reason")
                lastFailedNavigateMs = 0L
                return true
            }
            if (attempt < MAX_NAV_ATTEMPTS - 1) {
                Log.w(TAG, "[RECOVERY] navigate failed; cleaning UI before retry")
                NavigationOrchestrator.cleanGameUi()
            }
        }
        lastFailedNavigateMs = System.currentTimeMillis()
        Log.w(TAG, "[RECOVERY] navigate failed after retries reason=$reason")
        return false
    }

    /** Light touch: clean UI + auto. After death, full farm-spot navigate. */
    suspend fun recoverOnSpot(reason: String): Boolean {
        Log.d(TAG, "[RECOVERY] on-spot reason=$reason")
        if (DeathActions.isDead()) {
            if (!DeathActions.recoverIfDead()) {
                return false
            }
            return navigateToFarmWithRetry(reason)
        }
        NavigationOrchestrator.cleanGameUi()
        if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[RECOVERY] ensureAutoMode failed on-spot; farm loop will retry")
        }
        return true
    }

    suspend fun recoverFromLostState(reason: String): Boolean {
        Log.w(TAG, "[RECOVERY] checkpoint reason=$reason")

        if (DeathActions.isDead()) {
            Log.d(TAG, "[RECOVERY] dead during recovery")
            if (!DeathActions.recoverIfDead()) {
                return false
            }
        }

        NavigationOrchestrator.cleanGameUi()

        if (isAlreadyAtFarmPost(reason)) {
            Log.d(TAG, "[RECOVERY] on farm map+spot; light recovery only reason=$reason")
            if (!GameActions.ensureAutoMode()) {
                Log.w(TAG, "[RECOVERY] ensureAutoMode failed; farm loop will retry")
            }
            Log.d(TAG, "[RECOVERY] checkpoint completed (on-spot) reason=$reason")
            return true
        }

        if (!navigateToFarmWithRetry(reason)) {
            if (isNavCooldownActive()) {
                // Don't escalate to hard ERROR while waiting; next loops will retry.
                Log.w(TAG, "[RECOVERY] checkpoint deferred (nav cooldown) reason=$reason")
                return true
            }
            return false
        }

        if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[RECOVERY] ensureAutoMode failed; farm loop will retry")
        }

        Log.d(TAG, "[RECOVERY] checkpoint completed reason=$reason")
        return true
    }

    /**
     * Skip navigate only when map matches **and** HUD coords are within [FarmLocation.arrivalRadius]
     * (same tight radius used for spot arrival; farmRadius default is also 5).
     * No saved coords / OCR miss → do not skip (force return to spot; needed after revive).
     */
    private suspend fun isAlreadyAtFarmPost(reason: String): Boolean {
        if (!MapCheckActions.isInConfiguredMap()) {
            return false
        }

        val farmSpot = LocationRepository.farmSpot.value
        if (farmSpot == null) {
            Log.d(TAG, "[RECOVERY] on map but no farm spot saved; navigate reason=$reason")
            return false
        }
        if (farmSpot.coordX == null || farmSpot.coordY == null) {
            Log.d(TAG, "[RECOVERY] on map but no spot coords; navigate reason=$reason")
            return false
        }

        val mapDef = MapDefinitionRepository.getById(farmSpot.map)
        val onSpot = NavigationWaitActions.isAtFarmSpot(farmSpot, mapDef)
        if (onSpot) {
            Log.d(
                TAG,
                "[RECOVERY] already on farm map+spot (r=${farmSpot.arrivalRadius}); " +
                    "skip navigate reason=$reason",
            )
            return true
        }

        Log.d(
            TAG,
            "[RECOVERY] on map but off spot (need r<=${farmSpot.arrivalRadius}); " +
                "navigate reason=$reason",
        )
        return false
    }
}
