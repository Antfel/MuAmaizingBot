package com.example.muamaizingbot.bot.maintenance

import android.util.Log
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationTemplateThresholds
import com.example.muamaizingbot.vision.navigation.NavigationVision
import kotlinx.coroutines.delay

/**
 * Detects full-screen / overlay panels that hide world HUD templates
 * (elf buff icon, potion-out, inventory-full, …).
 *
 * Call [ensureClearForHudProbe] before those validations so a leftover Gear /
 * Store / map / inventory window does not look like a missing buff/potion.
 */
object HudValidationGate {

    private const val TAG = "HudValidation"
    private const val INVENTORY_OPEN = "templates/mu/ui/inventory_open.png"
    private const val GEAR_OPEN = "templates/mu/ui/pet/gear_open.png"
    private const val STORE_TAB = "templates/mu/ui/store/store_open_tab.png"
    private const val STORE_TITLE = "templates/mu/ui/store/store_title.png"
    private const val PANEL_THRESHOLD = 0.75f
    private const val CLOSE_SETTLE_MS = 500L
    private const val CLOSE_ATTEMPTS = 2

    enum class BlockingPanel {
        MAP,
        STORE,
        INVENTORY,
        GEAR,
    }

    suspend fun detectBlockingPanel(): BlockingPanel? {
        if (MapWindowActions.isMapWindowOpen()) {
            return BlockingPanel.MAP
        }
        if (NavigationVision.findTemplate(STORE_TAB, PANEL_THRESHOLD) != null ||
            NavigationVision.findTemplate(STORE_TITLE, PANEL_THRESHOLD) != null
        ) {
            return BlockingPanel.STORE
        }
        if (NavigationVision.findTemplate(INVENTORY_OPEN, PANEL_THRESHOLD) != null) {
            return BlockingPanel.INVENTORY
        }
        if (NavigationVision.findTemplate(GEAR_OPEN, PANEL_THRESHOLD) != null) {
            return BlockingPanel.GEAR
        }
        return null
    }

    /**
     * @return true when the HUD should be safe to probe for world templates.
     */
    suspend fun ensureClearForHudProbe(): Boolean {
        var panel: BlockingPanel? = detectBlockingPanel()
        if (panel == null) {
            return true
        }
        Log.w(TAG, "[HUD] blocking panel=$panel — trying close_x")
        repeat(CLOSE_ATTEMPTS) { attempt ->
            val closed = NavigationVision.tapTemplate(
                MapWindowActions.CLOSE_X,
                NavigationTemplateThresholds.closeX(),
            )
            if (!closed) {
                Log.d(TAG, "[HUD] close_x miss attempt=${attempt + 1}")
            }
            delay(CLOSE_SETTLE_MS)
            panel = detectBlockingPanel()
            if (panel == null) {
                Log.d(TAG, "[HUD] panels clear after close attempt=${attempt + 1}")
                return true
            }
            Log.d(TAG, "[HUD] still blocked panel=$panel attempt=${attempt + 1}")
        }
        Log.w(TAG, "[HUD] still blocked panel=$panel — skip HUD validations this tick")
        return false
    }
}
