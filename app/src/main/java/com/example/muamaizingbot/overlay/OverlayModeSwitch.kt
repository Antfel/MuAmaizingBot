package com.example.muamaizingbot.overlay

import android.util.Log
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.bot.bosses.BossHuntState
import com.example.muamaizingbot.bot.combat.CombatFocusActions
import com.example.muamaizingbot.profile.BotMode
import com.example.muamaizingbot.profile.BotProfile
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.profile.isElfBuffPostMode
import com.example.muamaizingbot.profile.isElfBuffWarMode
import com.example.muamaizingbot.profile.isFarmBossesMode
import com.example.muamaizingbot.profile.isFarmMode
import com.example.muamaizingbot.profile.normalizedBotMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Quick mode slots for the overlay HUD.
 * Elf giver/war share one slot; the last elf subtype is restored when switching back.
 */
enum class OverlayModeSlot {
    FARM,
    ELF,
    BOSSES,
    ;

    fun isSelected(profile: BotProfile): Boolean = when (this) {
        FARM -> profile.isFarmMode()
        ELF -> profile.isElfBuffPostMode()
        BOSSES -> profile.isFarmBossesMode()
    }

    fun isConfigured(profile: BotProfile): Boolean = when (this) {
        FARM, ELF -> LocationRepository.getFarmSpot(profile.filename) != null
        BOSSES -> profile.killBossesConfig.maps.isNotEmpty()
    }
}

object OverlayModeSwitch {
    private const val TAG = "OverlayMode"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val switchInFlight = AtomicBoolean(false)

    private val _switching = MutableStateFlow(false)
    /** True while a switch is stopping the old worker; overlay disables the chips. */
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    @Volatile
    private var lastElfPostMode: String = BotMode.ELF_BUFF_GIVER

    fun rememberElfSubtype(profile: BotProfile?) {
        if (profile?.isElfBuffPostMode() == true) {
            lastElfPostMode = profile.normalizedBotMode()
        }
    }

    /**
     * Switch profile mode from the overlay.
     * If the bot was RUNNING, waits for the old worker to unwind before cold-starting
     * the new mode, so both loops never tap the game at the same time.
     */
    fun apply(slot: OverlayModeSlot) {
        val profile = ProfileRepository.currentProfile.value ?: return
        if (slot.isSelected(profile)) {
            Log.d(TAG, "[OVERLAY_MODE] already on slot=$slot")
            return
        }
        if (!slot.isConfigured(profile)) {
            Log.w(TAG, "[OVERLAY_MODE] slot=$slot not configured — ignore")
            return
        }
        if (!switchInFlight.compareAndSet(false, true)) {
            Log.w(TAG, "[OVERLAY_MODE] switch already in flight — ignore slot=$slot")
            return
        }

        rememberElfSubtype(profile)

        val targetMode = when (slot) {
            OverlayModeSlot.FARM -> BotMode.FARM
            OverlayModeSlot.BOSSES -> BotMode.FARM_BOSSES
            OverlayModeSlot.ELF -> when {
                profile.isElfBuffWarMode() -> BotMode.ELF_BUFF_WAR
                profile.isElfBuffPostMode() -> profile.normalizedBotMode()
                else -> lastElfPostMode
            }
        }

        val previous = profile.normalizedBotMode()
        val botState = BotController.state.value
        val wasRunning = botState == BotRuntimeState.RUNNING
        Log.d(
            TAG,
            "[OVERLAY_MODE] switch $previous → $targetMode slot=$slot botState=$botState restart=$wasRunning",
        )

        _switching.value = true
        scope.launch {
            try {
                if (botState != BotRuntimeState.IDLE) {
                    BotController.stopAndAwait()
                }
                ProfileRepository.setBotMode(profile.filename, targetMode)
                BossHuntState.reset()
                CombatFocusActions.reset()
                if (wasRunning) {
                    BotController.start()
                }
            } finally {
                _switching.value = false
                switchInFlight.set(false)
            }
        }
    }
}
