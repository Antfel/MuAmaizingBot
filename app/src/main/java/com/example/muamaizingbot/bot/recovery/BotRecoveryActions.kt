package com.example.muamaizingbot.bot.recovery

import android.util.Log
import com.example.muamaizingbot.bot.combat.DeathActions
import com.example.muamaizingbot.bot.combat.GameActions
import com.example.muamaizingbot.bot.maintenance.MapCheckActions
import com.example.muamaizingbot.bot.navigation.NavigationOrchestrator

/**
 * Control point to resume navigation after interrupted sequences (elf buff, potions, combat).
 * Full re-navigation only when not on the configured farm map.
 */
object BotRecoveryActions {

    private const val TAG = "Recovery"
    private const val MAX_NAV_ATTEMPTS = 2

    suspend fun navigateToFarmWithRetry(reason: String): Boolean {
        if (MapCheckActions.isInConfiguredMap()) {
            Log.d(TAG, "[RECOVERY] already on farm map; skip navigate reason=$reason")
            return true
        }

        repeat(MAX_NAV_ATTEMPTS) { attempt ->
            Log.d(TAG, "[RECOVERY] navigate attempt=${attempt + 1}/$MAX_NAV_ATTEMPTS reason=$reason")
            if (DeathActions.isDead()) {
                Log.d(TAG, "[RECOVERY] dead before navigate; reviving")
                if (!DeathActions.recoverIfDead()) {
                    return false
                }
            }
            if (NavigationOrchestrator.goToActiveFarmSpot()) {
                Log.d(TAG, "[RECOVERY] navigate ok reason=$reason")
                return true
            }
            if (attempt < MAX_NAV_ATTEMPTS - 1) {
                Log.w(TAG, "[RECOVERY] navigate failed; cleaning UI before retry")
                NavigationOrchestrator.cleanGameUi()
            }
        }
        Log.w(TAG, "[RECOVERY] navigate failed after retries reason=$reason")
        return false
    }

    /** Light touch: clean UI + auto. No teleport unless wrong map. */
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

        if (MapCheckActions.isInConfiguredMap()) {
            Log.d(TAG, "[RECOVERY] on farm map; light recovery only reason=$reason")
            if (!GameActions.ensureAutoMode()) {
                Log.w(TAG, "[RECOVERY] ensureAutoMode failed; farm loop will retry")
            }
            Log.d(TAG, "[RECOVERY] checkpoint completed (on-spot) reason=$reason")
            return true
        }

        if (!navigateToFarmWithRetry(reason)) {
            return false
        }

        if (!GameActions.ensureAutoMode()) {
            Log.w(TAG, "[RECOVERY] ensureAutoMode failed; farm loop will retry")
        }

        Log.d(TAG, "[RECOVERY] checkpoint completed reason=$reason")
        return true
    }
}
