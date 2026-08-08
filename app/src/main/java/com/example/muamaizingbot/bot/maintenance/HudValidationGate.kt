package com.example.muamaizingbot.bot.maintenance

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.navigation.MapWindowActions
import com.example.muamaizingbot.bot.navigation.NavigationTemplateThresholds
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.MuCombatRois
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
        // Real map chrome only — inventory/gear/store also have close_x and must
        // not be classified as MAP or we skip per-panel close ROIs.
        if (MapWindowActions.hasMapTabChrome()) {
            return BlockingPanel.MAP
        }
        val frame = NavigationVision.captureFrame() ?: return null
        return try {
            val storeTabRoi = MuCombatRois.storeTabRoi(frame)
            val storeTitleRoi = MuCombatRois.storeTitleRoi(frame)
            if (NavigationVision.findOnFrame(frame, STORE_TAB, PANEL_THRESHOLD, storeTabRoi) != null ||
                NavigationVision.findOnFrame(frame, STORE_TITLE, PANEL_THRESHOLD, storeTitleRoi) != null
            ) {
                Log.d(TAG, "[HUD] store hit tabRoi=$storeTabRoi titleRoi=$storeTitleRoi")
                return BlockingPanel.STORE
            }
            val invRoi = MuCombatRois.inventoryOpenRoi(frame)
            if (NavigationVision.findOnFrame(frame, INVENTORY_OPEN, PANEL_THRESHOLD, invRoi) != null) {
                Log.d(TAG, "[HUD] inventory_open hit roi=$invRoi")
                return BlockingPanel.INVENTORY
            }
            val gearRoi = MuCombatRois.gearOpenRoi(frame)
            if (NavigationVision.findOnFrame(frame, GEAR_OPEN, PANEL_THRESHOLD, gearRoi) != null) {
                Log.d(TAG, "[HUD] gear_open hit roi=$gearRoi")
                return BlockingPanel.GEAR
            }
            null
        } finally {
            frame.recycle()
        }
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
            val closed = tapCloseForPanel(panel!!)
            if (!closed) {
                Log.d(TAG, "[HUD] close_x miss attempt=${attempt + 1} panel=$panel")
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

    /** Panel-specific close_x search (positions differ per chrome). */
    private suspend fun tapCloseForPanel(panel: BlockingPanel): Boolean {
        val threshold = NavigationTemplateThresholds.closeX()
        return when (panel) {
            BlockingPanel.MAP -> {
                NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, threshold)
            }
            BlockingPanel.STORE -> {
                val roi = storeCloseXRoi()
                Log.d(TAG, "[HUD] close_x store roi=$roi")
                NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, threshold, roi)
            }
            BlockingPanel.INVENTORY -> {
                val roi = inventoryCloseXRoi()
                Log.d(TAG, "[HUD] close_x inventory roi=$roi")
                NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, threshold, roi)
            }
            BlockingPanel.GEAR -> {
                // Dual layout often shows Gear+Inventory; try Gear X then Inventory X.
                val gearRoi = gearCloseXRoi()
                Log.d(TAG, "[HUD] close_x gear roi=$gearRoi")
                if (NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, threshold, gearRoi)) {
                    return true
                }
                val invRoi = inventoryCloseXRoi()
                Log.d(TAG, "[HUD] close_x gear miss → inventory roi=$invRoi")
                NavigationVision.tapTemplate(MapWindowActions.CLOSE_X, threshold, invRoi)
            }
        }
    }

    private fun gearCloseXRoi(): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return MuCombatRois.gearCloseXRoi(w, h)
    }

    private fun inventoryCloseXRoi(): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return MuCombatRois.inventoryCloseXRoi(w, h)
    }

    private fun storeCloseXRoi(): Rect {
        val (w, h) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        return MuCombatRois.storeCloseXRoi(w, h)
    }
}
