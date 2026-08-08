package com.example.muamaizingbot.vision.roi

import android.graphics.Bitmap
import android.graphics.Rect

object MuCombatRois {

    /**
     * Solo el label Auto / Manual / Pause. Medido en 1280×720:
     * (1209,340)-(1275,370) → ref 2560×1440 ×2.
     */
    private const val AUTO_HUD_LEFT = 2418
    private const val AUTO_HUD_TOP = 680
    private const val AUTO_HUD_RIGHT = 2550
    private const val AUTO_HUD_BOTTOM = 740

    fun autoHudRoi(frame: Bitmap): Rect {
        return ScaledRoi.fromRefRect(
            left = AUTO_HUD_LEFT,
            top = AUTO_HUD_TOP,
            right = AUTO_HUD_RIGHT,
            bottom = AUTO_HUD_BOTTOM,
            frameWidth = frame.width,
            frameHeight = frame.height
        )
    }

    /** Death / revive dialog (logical ref 2560×1440). Revive button ~(1120,865). */
    fun deathDialogRoi(frame: Bitmap): Rect {
        return ScaledRoi.fromRefRect(
            left = 500,
            top = 550,
            right = 1900,
            bottom = 1100,
            frameWidth = frame.width,
            frameHeight = frame.height
        )
    }

    /** Bottom-right skill cluster (Greater Defense / Greater Damage, etc.). */
    fun skillBarRoi(frame: Bitmap): Rect {
        return ScaledRoi.fromRefRect(
            left = 1600,
            top = 550,
            right = 2560,
            bottom = 1440,
            frameWidth = frame.width,
            frameHeight = frame.height,
        )
    }

    /**
     * Bottom combat cluster: closed PK label (All) + Focus player button.
     * Logical ref 2560×1440.
     */
    fun targetingHudRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = 1600,
            top = 900,
            right = 2560,
            bottom = 1440,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun targetingHudRoi(frame: Bitmap): Rect = targetingHudRoi(frame.width, frame.height)

    /**
     * PK mode popup: option boxes that open **above** the All button (~10s visible).
     * Covers UnionKuaFu / All rows in that stack.
     */
    fun pkModePopupRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = 1400,
            top = 500,
            right = 2300,
            bottom = 1400,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    /**
     * Bottom-center HP/MP potion quick slots (empty-icon probes).
     * Authored from 5584 @ 1280×720: HP empty ~(542,685); covers all 4 quick slots.
     * Native ROI (500,635)-(785,720). Logical ref 2560×1440.
     */
    private const val POTION_SLOTS_LEFT = 1000
    private const val POTION_SLOTS_TOP = 1270
    private const val POTION_SLOTS_RIGHT = 1570
    private const val POTION_SLOTS_BOTTOM = 1440

    fun potionSlotsRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = POTION_SLOTS_LEFT,
            top = POTION_SLOTS_TOP,
            right = POTION_SLOTS_RIGHT,
            bottom = POTION_SLOTS_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun potionSlotsRoi(frame: Bitmap): Rect = potionSlotsRoi(frame.width, frame.height)

    /**
     * Right-edge Inventory bag button (full-badge probe + tap).
     * Authored from 5584 @ 1280×720: inventory.png ~(1246,261).
     * Native ROI (1190,200)-(1280,330). Logical ref 2560×1440.
     */
    private const val INVENTORY_FULL_LEFT = 2380
    private const val INVENTORY_FULL_TOP = 400
    private const val INVENTORY_FULL_RIGHT = 2560
    private const val INVENTORY_FULL_BOTTOM = 660

    fun inventoryFullRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = INVENTORY_FULL_LEFT,
            top = INVENTORY_FULL_TOP,
            right = INVENTORY_FULL_RIGHT,
            bottom = INVENTORY_FULL_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun inventoryFullRoi(frame: Bitmap): Rect = inventoryFullRoi(frame.width, frame.height)

    /**
     * Open Inventory panel title ("Inventory" header on right bag).
     * Authored from 5584 @ 1280×720: inventory_open tl~(1023,17).
     * Native ROI (960,0)-(1210,70). Logical ref 2560×1440.
     */
    private const val INVENTORY_OPEN_LEFT = 1920
    private const val INVENTORY_OPEN_TOP = 0
    private const val INVENTORY_OPEN_RIGHT = 2420
    private const val INVENTORY_OPEN_BOTTOM = 140

    fun inventoryOpenRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = INVENTORY_OPEN_LEFT,
            top = INVENTORY_OPEN_TOP,
            right = INVENTORY_OPEN_RIGHT,
            bottom = INVENTORY_OPEN_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun inventoryOpenRoi(frame: Bitmap): Rect = inventoryOpenRoi(frame.width, frame.height)

    /**
     * Open Gear panel title (left/center dual-layout with Inventory).
     * Authored from 5584 @ 1280×720: gear_open tl~(648,18).
     * Native ROI (615,0)-(750,70). Logical ref 2560×1440.
     */
    private const val GEAR_OPEN_LEFT = 1230
    private const val GEAR_OPEN_TOP = 0
    private const val GEAR_OPEN_RIGHT = 1500
    private const val GEAR_OPEN_BOTTOM = 140

    fun gearOpenRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = GEAR_OPEN_LEFT,
            top = GEAR_OPEN_TOP,
            right = GEAR_OPEN_RIGHT,
            bottom = GEAR_OPEN_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun gearOpenRoi(frame: Bitmap): Rect = gearOpenRoi(frame.width, frame.height)

    /**
     * MU Coin Store left tab ("MU Coin Store").
     * Authored from 5584 @ 1280×720: store_open_tab tl~(168,148).
     * Native ROI (105,130)-(360,205). Logical ref 2560×1440.
     */
    private const val STORE_TAB_LEFT = 210
    private const val STORE_TAB_TOP = 260
    private const val STORE_TAB_RIGHT = 720
    private const val STORE_TAB_BOTTOM = 410

    fun storeTabRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = STORE_TAB_LEFT,
            top = STORE_TAB_TOP,
            right = STORE_TAB_RIGHT,
            bottom = STORE_TAB_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun storeTabRoi(frame: Bitmap): Rect = storeTabRoi(frame.width, frame.height)

    /**
     * Store window title ("Store").
     * Authored from 5584 @ 1280×720: store_title tl~(596,100).
     * Native ROI (550,85)-(735,145). Logical ref 2560×1440.
     */
    private const val STORE_TITLE_LEFT = 1100
    private const val STORE_TITLE_TOP = 170
    private const val STORE_TITLE_RIGHT = 1470
    private const val STORE_TITLE_BOTTOM = 290

    fun storeTitleRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = STORE_TITLE_LEFT,
            top = STORE_TITLE_TOP,
            right = STORE_TITLE_RIGHT,
            bottom = STORE_TITLE_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun storeTitleRoi(frame: Bitmap): Rect = storeTitleRoi(frame.width, frame.height)

    /**
     * close_x on Gear panel (mid-top, right edge of Gear chrome).
     * Authored from 5584 dual layout: center~(859,19).
     * Native ROI (800,0)-(940,70). Logical ref 2560×1440.
     */
    private const val GEAR_CLOSE_X_LEFT = 1600
    private const val GEAR_CLOSE_X_TOP = 0
    private const val GEAR_CLOSE_X_RIGHT = 1880
    private const val GEAR_CLOSE_X_BOTTOM = 140

    fun gearCloseXRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = GEAR_CLOSE_X_LEFT,
            top = GEAR_CLOSE_X_TOP,
            right = GEAR_CLOSE_X_RIGHT,
            bottom = GEAR_CLOSE_X_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun gearCloseXRoi(frame: Bitmap): Rect = gearCloseXRoi(frame.width, frame.height)

    /**
     * close_x on Inventory panel (top-right).
     * Authored from 5584 dual layout: center~(1258,19).
     * Native ROI (1180,0)-(1280,70). Logical ref 2560×1440.
     */
    private const val INVENTORY_CLOSE_X_LEFT = 2360
    private const val INVENTORY_CLOSE_X_TOP = 0
    private const val INVENTORY_CLOSE_X_RIGHT = 2560
    private const val INVENTORY_CLOSE_X_BOTTOM = 140

    fun inventoryCloseXRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = INVENTORY_CLOSE_X_LEFT,
            top = INVENTORY_CLOSE_X_TOP,
            right = INVENTORY_CLOSE_X_RIGHT,
            bottom = INVENTORY_CLOSE_X_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun inventoryCloseXRoi(frame: Bitmap): Rect = inventoryCloseXRoi(frame.width, frame.height)

    /**
     * close_x on Store window (upper mid-right).
     * Authored from 5584: center~(1120,110).
     * Native ROI (1060,70)-(1180,150). Logical ref 2560×1440.
     */
    private const val STORE_CLOSE_X_LEFT = 2120
    private const val STORE_CLOSE_X_TOP = 140
    private const val STORE_CLOSE_X_RIGHT = 2360
    private const val STORE_CLOSE_X_BOTTOM = 300

    fun storeCloseXRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = STORE_CLOSE_X_LEFT,
            top = STORE_CLOSE_X_TOP,
            right = STORE_CLOSE_X_RIGHT,
            bottom = STORE_CLOSE_X_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun storeCloseXRoi(frame: Bitmap): Rect = storeCloseXRoi(frame.width, frame.height)
}
