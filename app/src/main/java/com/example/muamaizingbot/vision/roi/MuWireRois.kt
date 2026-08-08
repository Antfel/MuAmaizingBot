package com.example.muamaizingbot.vision.roi

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Wire-switch HUD / popup ROIs authored on BlueStacks 5584 @ 1280×720.
 * Logical refs are 2560×1440 via [ScaledRoi].
 */
object MuWireRois {

    /**
     * Top-right `[Wire N]` HUD + Switch channel button (excludes Efficiency / minimap).
     * Closed HUD: wire label ~(1106,33); Switch ~(1222,53).
     * Native (1060,0)–(1280,90).
     */
    private const val WIRE_HUD_LEFT = 2120
    private const val WIRE_HUD_TOP = 0
    private const val WIRE_HUD_RIGHT = 2560
    private const val WIRE_HUD_BOTTOM = 180

    fun wireHudRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = WIRE_HUD_LEFT,
            top = WIRE_HUD_TOP,
            right = WIRE_HUD_RIGHT,
            bottom = WIRE_HUD_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun wireHudRoi(frame: Bitmap): Rect = wireHudRoi(frame.width, frame.height)

    /**
     * "Switch Channel" popup title band.
     * Open popup: wire_popup_open tl~(542,114) center~(639,125).
     * Native (480,90)–(800,170).
     */
    private const val POPUP_TITLE_LEFT = 960
    private const val POPUP_TITLE_TOP = 180
    private const val POPUP_TITLE_RIGHT = 1600
    private const val POPUP_TITLE_BOTTOM = 340

    fun popupTitleRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = POPUP_TITLE_LEFT,
            top = POPUP_TITLE_TOP,
            right = POPUP_TITLE_RIGHT,
            bottom = POPUP_TITLE_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun popupTitleRoi(frame: Bitmap): Rect = popupTitleRoi(frame.width, frame.height)

    /**
     * Channel-list OCR band (orange on ANN_2_popup). Rows only — not Switch Line / chrome.
     * Native (488,172)–(794,476).
     */
    private const val LIST_OCR_LEFT = 976
    private const val LIST_OCR_TOP = 344
    private const val LIST_OCR_RIGHT = 1588
    private const val LIST_OCR_BOTTOM = 952

    fun listOcrRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = LIST_OCR_LEFT,
            top = LIST_OCR_TOP,
            right = LIST_OCR_RIGHT,
            bottom = LIST_OCR_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun listOcrRoi(frame: Bitmap): Rect = listOcrRoi(frame.width, frame.height)

    /**
     * Orange close X on the Switch Channel chrome.
     * Open popup: close_x ~(816,121). Native (760,80)–(870,160).
     */
    private const val POPUP_CLOSE_LEFT = 1520
    private const val POPUP_CLOSE_TOP = 160
    private const val POPUP_CLOSE_RIGHT = 1740
    private const val POPUP_CLOSE_BOTTOM = 320

    fun popupCloseXRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = POPUP_CLOSE_LEFT,
            top = POPUP_CLOSE_TOP,
            right = POPUP_CLOSE_RIGHT,
            bottom = POPUP_CLOSE_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun popupCloseXRoi(frame: Bitmap): Rect = popupCloseXRoi(frame.width, frame.height)

    /**
     * Selected-row underline / highlight inside the channel list.
     * Open popup: wire_selected ~(634,198). Native (480,170)–(800,280).
     */
    private const val SELECTED_LEFT = 960
    private const val SELECTED_TOP = 340
    private const val SELECTED_RIGHT = 1600
    private const val SELECTED_BOTTOM = 560

    fun selectedRowRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = SELECTED_LEFT,
            top = SELECTED_TOP,
            right = SELECTED_RIGHT,
            bottom = SELECTED_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun selectedRowRoi(frame: Bitmap): Rect = selectedRowRoi(frame.width, frame.height)

    /**
     * "Switch Line" button center — static while the Switch Channel popup is open.
     * Authored on 5584 @ 1280×720: enter template center ~(640,551) → REF (1280,1102).
     * Prefer this tap over template search; chrome does not move.
     */
    const val ENTER_TAP_REF_X = 1280
    const val ENTER_TAP_REF_Y = 1102

    /**
     * Optional tight ROI around [ENTER_TAP_REF_X]/[ENTER_TAP_REF_Y] for debug / validate.
     * Native (520,500)–(760,600).
     */
    private const val ENTER_LEFT = 1040
    private const val ENTER_TOP = 1000
    private const val ENTER_RIGHT = 1520
    private const val ENTER_BOTTOM = 1200

    fun enterButtonRoi(frameWidth: Int, frameHeight: Int): Rect {
        return ScaledRoi.fromRefRect(
            left = ENTER_LEFT,
            top = ENTER_TOP,
            right = ENTER_RIGHT,
            bottom = ENTER_BOTTOM,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
        )
    }

    fun enterButtonRoi(frame: Bitmap): Rect = enterButtonRoi(frame.width, frame.height)
}
