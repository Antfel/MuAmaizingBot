package com.example.muamaizingbot.bot.maintenance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.muamaizingbot.BuildConfig
import com.example.muamaizingbot.profile.PetType

/**
 * Debug-build hook to run the bag sweep on demand, read-only:
 *
 * ```
 * adb -s 127.0.0.1:5585 shell am broadcast \
 *   -a com.example.muamaizingbot.DEBUG_PET_SWEEP -p com.example.muamaizingbot --es pet imp
 * ```
 *
 * The screen capture must already be running. Results land in logcat under the
 * `PetBagScan` tag; no item is equipped and no MU Coin is spent.
 */
object PetBagScanDebugTrigger {

    private const val TAG = "PetBagScanDebug"
    private const val ACTION = "com.example.muamaizingbot.DEBUG_PET_SWEEP"
    private const val EXTRA_PET = "pet"

    @Volatile
    private var pending: PetType? = null

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION) return
                onTrigger(intent.getStringExtra(EXTRA_PET))
            }
        }
        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        Log.d(TAG, "[BAG] debug sweep trigger registered action=$ACTION")
    }

    private fun onTrigger(petArg: String?) {
        val want = PetType.parse(petArg ?: "imp")
        pending = want
        Log.w(TAG, "[BAG] debug sweep queued want=${want.toStorage()} — runs on next loop turn")
    }

    /**
     * Runs a queued sweep, if any, and reports whether the caller should skip the
     * rest of its turn.
     *
     * Driving the panels from the broadcast thread raced the bot loop and lost —
     * `openGearPanel` kept failing because the loop was tapping underneath it. The
     * sweep therefore runs inside the loop's own turn, where it holds the UI alone.
     */
    suspend fun runPendingSweep(): Boolean {
        val want = pending ?: return false
        pending = null
        try {
            Log.w(TAG, "[BAG] debug sweep start want=${want.toStorage()}")
            val result = PetActions.dryRunBagSweep(want)
            Log.w(TAG, "[BAG] debug sweep finished result=$result")
        } catch (error: Throwable) {
            Log.e(TAG, "[BAG] debug sweep failed: ${error.message}", error)
        }
        return true
    }
}
