package com.example.muamaizingbot.bot.navigation

import android.util.Log
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.WireSwitchConfig
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlinx.coroutines.delay

/**
 * Wire switch via HUD switch button (main UI) — same flow as mu-adb-bot navigation_state.switch_to_wire.
 * Does NOT require the zone map window to be open.
 */
object WireSwitchActions {

    private const val TAG = "WireSwitch"
    private const val WIRE_THRESHOLD = 0.8f
    private const val SCROLL_WAIT_MS = 1000L
    private const val POPUP_OPEN_WAIT_MS = 1000L
    private const val WIRE_SELECT_WAIT_MS = 1000L

    suspend fun switchToWire(mapDef: MapDefinition, wireId: Int): Boolean {
        val wire = wireId.coerceAtLeast(1)
        Log.d(TAG, "[WIRE] requested wire=$wire map=${mapDef.id}")

        if (wire == 1) {
            Log.d(TAG, "[WIRE] skip wire=1")
            return true
        }

        val config = mapDef.wireSwitch
        if (config == null || !config.enabled) {
            Log.w(TAG, "[WIRE] not configured map=${mapDef.id}")
            return false
        }

        Log.d(TAG, "[WIRE] available=${config.availableWires}")

        if (config.availableWires.size <= 1) {
            Log.d(TAG, "[WIRE] single wire map")
            return true
        }

        if (wire !in config.availableWires) {
            Log.w(TAG, "[WIRE] wire $wire not in ${config.availableWires}")
            return false
        }

        if (config.hudDetection) {
            hudTemplate(config, wire)?.let { hudPath ->
                if (NavigationVision.findTemplate(hudPath, WIRE_THRESHOLD) != null) {
                    Log.d(TAG, "[WIRE] already on wire $wire (HUD)")
                    return true
                }
            }
        }

        if (!openWirePopup(config)) {
            return false
        }

        val wireMatch = findWireOptionWithScroll(config, wire) ?: return false

        val confirmOk = if (isWireRowSelected(config, wireMatch)) {
            Log.d(TAG, "[WIRE] wire $wire already selected in popup")
            confirmWireSwitch(config)
        } else {
            Log.d(TAG, "[WIRE] selecting wire $wire")
            if (!NavigationVision.tapMatch(wireMatch)) {
                return false
            }
            delay(WIRE_SELECT_WAIT_MS)
            confirmWireSwitch(config)
        }

        if (!confirmOk) {
            return false
        }

        if (config.hudDetection) {
            hudTemplate(config, wire)?.let { hudPath ->
                if (NavigationVision.findTemplate(hudPath, WIRE_THRESHOLD) != null) {
                    Log.d(TAG, "[WIRE] confirmed via HUD")
                    return true
                }
                Log.w(TAG, "[WIRE] HUD not confirmed after switch")
            }
        }

        Log.d(TAG, "[WIRE] completed wire=$wire")
        return true
    }

    private suspend fun openWirePopup(config: WireSwitchConfig): Boolean {
        val templates = config.templates

        repeat(3) { attempt ->
            val switchButton = NavigationVision.findTemplate(templates.switchButton, WIRE_THRESHOLD)
                ?: run {
                    Log.w(TAG, "[WIRE] switch button not found attempt=${attempt + 1}")
                    return false
                }

            Log.d(TAG, "[WIRE] opening popup via HUD switch attempt=${attempt + 1}")
            NavigationVision.tapMatch(switchButton)
            delay(POPUP_OPEN_WAIT_MS)

            if (NavigationVision.findTemplate(templates.popupOpen, WIRE_THRESHOLD) != null) {
                return true
            }
            Log.w(TAG, "[WIRE] popup not visible attempt=${attempt + 1}")
        }

        Log.w(TAG, "[WIRE] popup did not open")
        return false
    }

    private suspend fun findWireOptionWithScroll(
        config: WireSwitchConfig,
        wireId: Int,
    ): com.example.muamaizingbot.vision.template.PcTemplateMatchResult? {
        val optionPath = config.templates.options[wireId]
        if (optionPath.isNullOrBlank()) {
            Log.w(TAG, "[WIRE] no option template for wire $wireId")
            return null
        }

        val scroll = config.popupScroll
        val maxAttempts = scroll.maxAttempts

        repeat(maxAttempts) { attempt ->
            val match = NavigationVision.findTemplate(optionPath, WIRE_THRESHOLD)
            if (match != null) {
                Log.d(TAG, "[WIRE] option found attempt=${attempt + 1}")
                return match
            }
            if (attempt < maxAttempts - 1) {
                Log.d(TAG, "[WIRE] scrolling popup attempt=${attempt + 1}")
                NavigationVision.swipe(scroll)
                delay(SCROLL_WAIT_MS)
            }
        }

        Log.w(TAG, "[WIRE] option not found wire=$wireId")
        return null
    }

    private suspend fun isWireRowSelected(
        config: WireSwitchConfig,
        wireMatch: com.example.muamaizingbot.vision.template.PcTemplateMatchResult,
    ): Boolean {
        val region = NavigationVision.wireRowRegion(wireMatch)
        return NavigationVision.findTemplate(config.templates.selected, WIRE_THRESHOLD, region) != null
    }

    private suspend fun confirmWireSwitch(config: WireSwitchConfig): Boolean {
        val enter = NavigationVision.findTemplate(config.templates.enterButton, WIRE_THRESHOLD)
            ?: run {
                Log.w(TAG, "[WIRE] enter button not found")
                return false
            }
        Log.d(TAG, "[WIRE] confirming switch wait=${config.switchWaitSeconds}s")
        NavigationVision.tapMatch(enter)
        delay(config.switchWaitSeconds * 1000L)
        return true
    }

    private fun hudTemplate(config: WireSwitchConfig, wireId: Int): String? {
        val path = config.templates.hud[wireId] ?: return null
        return path.takeIf { TemplateRepository.getByPath(it) != null }
    }
}
