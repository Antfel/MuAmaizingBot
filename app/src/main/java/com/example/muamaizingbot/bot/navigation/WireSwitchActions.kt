package com.example.muamaizingbot.bot.navigation

import android.graphics.Rect
import android.util.Log
import com.example.muamaizingbot.bot.BotController
import com.example.muamaizingbot.bot.BotRuntimeState
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.maps.MapDefinition
import com.example.muamaizingbot.maps.SwipeCoords
import com.example.muamaizingbot.maps.WireSwitchConfig
import com.example.muamaizingbot.vision.coord.RefCoords
import com.example.muamaizingbot.vision.navigation.NavigationVision
import com.example.muamaizingbot.vision.roi.ScaledRoi
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import com.example.muamaizingbot.vision.template.TemplateRepository
import com.example.muamaizingbot.vision.wire.WireChannelOcr
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Wire switch via HUD switch button (main UI).
 * Does NOT require the zone map window to be open.
 *
 * After teleport the HUD settles slowly; tapping Switch too early / without a ROI around
 * [Wire N] often hits the top-right chat icon instead.
 */
object WireSwitchActions {

    private const val TAG = "WireSwitch"
    private const val WIRE_THRESHOLD = 0.75f
    private const val WIRE_HUD_THRESHOLD = 0.82f
    private const val WIRE_SAME_ROW_MARGIN = 0.03f
    private const val WIRE_ENTER_THRESHOLD = 0.52f
    private const val CHAT_THRESHOLD = 0.70f
    /** PC bot tap on Switch Line @ 2560×1440 (log: tap 1279 1102). */
    private const val WIRE_ENTER_REF_X = 1279
    private const val WIRE_ENTER_REF_Y = 1102
    /** Same cadence as map-list scroll ([MapEntryActions] / [NavigationVision]). */
    private const val SCROLL_WAIT_MS = 1000L
    private const val POPUP_OPEN_WAIT_MS = 1500L
    private const val WIRE_SELECT_WAIT_MS = 1500L
    private const val WIRE_ENTER_WAIT_MS = 4000L
    private const val HUD_WAIT_MS = 12_000L
    /** Longer drag than a flick; short travel keeps it map-list soft. */
    private const val SWIPE_DURATION_MS = 550L
    /** ~80px @ 720p — enough to change visible wire rows without flinging. */
    private const val SWIPE_TRAVEL_REF_Y = 160

    private const val CHAT_OPEN = "templates/mu/ui/common/chat_open.png"
    private const val CHAT_CLOSE = "templates/mu/ui/common/chat_button_close.png"

    private data class WireProbe(val wireId: Int, val match: PcTemplateMatchResult)

    private data class PopupLayout(
        val title: PcTemplateMatchResult,
        val listRoi: Rect,
        val forwardSwipe: ScreenSwipe,
        val reverseSwipe: ScreenSwipe,
    )

    private data class ScreenSwipe(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val durationMs: Long = SWIPE_DURATION_MS,
    )

    suspend fun switchToWire(mapDef: MapDefinition, wireId: Int): Boolean {
        val wire = wireId.coerceAtLeast(1)
        Log.d(TAG, "[WIRE] requested wire=$wire map=${mapDef.id}")

        if (!requireCapture("switchToWire")) {
            return false
        }

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

        closeChatIfOpen()

        if (config.hudDetection && isAlreadyOnTargetWire(config, wire)) {
            Log.d(TAG, "[WIRE] already on wire $wire (HUD unique)")
            return true
        }

        val layout = openWirePopup(config) ?: return false

        val wireHit = findWireOptionWithScroll(config, wire, layout)
        if (wireHit == null) {
            dismissWirePopup(config)
            return false
        }

        // Always tap the OCR row + Switch Line. Skip only via HUD (already on target wire).
        Log.d(
            TAG,
            "[WIRE] selecting wire $wire via OCR raw=${wireHit.rawText} " +
                "at=(${wireHit.centerX},${wireHit.centerY})",
        )
        if (!NavigationVision.tapScreen(wireHit.centerX, wireHit.centerY)) {
            dismissWirePopup(config)
            return false
        }
        delay(WIRE_SELECT_WAIT_MS)

        if (!confirmWireSwitch(config, layout)) {
            dismissWirePopup(config)
            return false
        }

        if (config.hudDetection) {
            if (isAlreadyOnTargetWire(config, wire)) {
                Log.d(TAG, "[WIRE] confirmed via HUD unique")
                return true
            }
            Log.w(TAG, "[WIRE] HUD not confirmed after switch — continuing (enter tapped)")
        }

        Log.d(TAG, "[WIRE] completed wire=$wire")
        return true
    }

    /**
     * HUD labels share "[Wire …]"; only skip the popup when [wireId] is the unique best HUD match.
     */
    private suspend fun isAlreadyOnTargetWire(
        config: WireSwitchConfig,
        wireId: Int,
    ): Boolean {
        val frame = NavigationVision.captureFrame() ?: return false
        return try {
            val probes = config.availableWires.mapNotNull { id ->
                val path = hudTemplate(config, id) ?: return@mapNotNull null
                WireProbe(id, NavigationVision.probeOnFrame(frame, path))
            }
            if (probes.isEmpty()) {
                return false
            }
            val scores = probes.joinToString { "${it.wireId}=${"%.3f".format(it.match.score)}" }
            Log.d(TAG, "[WIRE] HUD probe scores=[$scores] want=$wireId")

            val qualified = probes.filter { it.match.score >= WIRE_HUD_THRESHOLD }
            if (qualified.isEmpty()) {
                return false
            }
            val best = qualified.maxBy { it.match.score }
            if (best.wireId != wireId) {
                Log.d(
                    TAG,
                    "[WIRE] HUD best is wire=${best.wireId} score=${best.match.score} " +
                        "(want $wireId) — will open switch",
                )
                return false
            }
            val second = qualified
                .filter { it.wireId != wireId }
                .maxOfOrNull { it.match.score }
                ?: 0f
            if (best.match.score < second + WIRE_SAME_ROW_MARGIN) {
                Log.d(
                    TAG,
                    "[WIRE] HUD ambiguous target=${best.match.score} second=$second — will open switch",
                )
                return false
            }
            true
        } finally {
            frame.recycle()
        }
    }

    private suspend fun openWirePopup(config: WireSwitchConfig): PopupLayout? {
        val templates = config.templates

        val wireHud = waitForAnyWireHud(config)
        if (wireHud == null) {
            Log.w(TAG, "[WIRE] no wire HUD after teleport — HUD not ready / wrong screen")
            closeChatIfOpen()
            return null
        }
        Log.d(
            TAG,
            "[WIRE] HUD ready score=${wireHud.score} at=(${wireHud.centerX},${wireHud.centerY}) " +
                "tpl=${wireHud.templateName}",
        )

        for (attempt in 1..3) {
            if (!requireCapture("openWirePopup attempt=$attempt")) {
                return null
            }

            closeChatIfOpen()

            val alreadyOpen = NavigationVision.findTemplate(templates.popupOpen, WIRE_THRESHOLD)
            if (alreadyOpen != null) {
                Log.d(TAG, "[WIRE] popup already open score=${alreadyOpen.score}")
                return layoutFromTitle(alreadyOpen, config.popupScroll)
            }

            val switchRoi = switchButtonRoi(wireHud)
            val switchButton = NavigationVision.findTemplate(
                templates.switchButton,
                WIRE_THRESHOLD,
                switchRoi,
            )

            if (switchButton == null) {
                Log.w(
                    TAG,
                    "[WIRE] switch button not found in HUD ROI " +
                        "roi=[${switchRoi.left},${switchRoi.top}-${switchRoi.right},${switchRoi.bottom}] " +
                        "attempt=$attempt",
                )
                NavigationVision.logBestScore(templates.switchButton, switchRoi)
                delay(500L)
                continue
            }

            if (!isPlausibleSwitchTap(switchButton, wireHud)) {
                Log.w(
                    TAG,
                    "[WIRE] rejecting switch match (likely chat/top bar) " +
                        "at=(${switchButton.centerX},${switchButton.centerY}) " +
                        "hudY=${wireHud.centerY} attempt=$attempt",
                )
                delay(500L)
                continue
            }

            Log.d(
                TAG,
                "[WIRE] opening popup via HUD switch attempt=$attempt " +
                    "at=(${switchButton.centerX},${switchButton.centerY}) score=${switchButton.score}",
            )
            NavigationVision.tapMatch(switchButton)
            delay(POPUP_OPEN_WAIT_MS)

            if (closeChatIfOpen()) {
                Log.w(TAG, "[WIRE] tap opened chat instead of wire popup — retry")
                delay(600L)
                continue
            }

            val popup = NavigationVision.findTemplate(templates.popupOpen, WIRE_THRESHOLD)
            if (popup != null) {
                Log.d(
                    TAG,
                    "[WIRE] popup open score=${popup.score} at=(${popup.centerX},${popup.centerY})",
                )
                return layoutFromTitle(popup, config.popupScroll)
            }
            Log.w(TAG, "[WIRE] popup not visible attempt=$attempt")
        }

        Log.w(TAG, "[WIRE] popup did not open")
        return null
    }

    /** Wait until [Wire N] HUD is on screen after map load — avoids tapping chat too early. */
    private suspend fun waitForAnyWireHud(config: WireSwitchConfig): PcTemplateMatchResult? {
        val paths = config.availableWires.mapNotNull { hudTemplate(config, it) }
        if (paths.isEmpty()) {
            return null
        }
        val deadline = System.currentTimeMillis() + HUD_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!requireCapture("waitWireHud")) {
                return null
            }
            closeChatIfOpen()
            for (path in paths) {
                val match = NavigationVision.findTemplate(path, WIRE_HUD_THRESHOLD) ?: continue
                return match
            }
            delay(350L)
        }
        paths.firstOrNull()?.let { NavigationVision.logBestScore(it) }
        return null
    }

    /**
     * Switch sits immediately right of `[Wire N]`, flush with the top edge (~y=0–25 @ 720p).
     * Prior ROI (padR≈110, padT≈15) clipped the live button at (1227,3) while HUD was at x≈1081.
     */
    private fun switchButtonRoi(wireHud: PcTemplateMatchResult): Rect {
        val (screenW, screenH) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        val padL = RefCoords.scaleX(40, screenW)
        val padB = RefCoords.scaleY(100, screenH)
        val aroundHud = Rect(
            max(0, wireHud.bestX - padL),
            0,
            screenW,
            min(screenH, wireHud.bestY + wireHud.templateHeight + padB),
        )
        if (aroundHud.width() > 20 && aroundHud.height() > 20) {
            return aroundHud
        }
        return ScaledRoi.fromRefRect(1800, 0, 2560, 280, screenW, screenH)
    }

    /** Accept Switch next to wire HUD (often y≈10–20). Reject chat / far left / far below HUD. */
    private fun isPlausibleSwitchTap(
        switchMatch: PcTemplateMatchResult,
        wireHud: PcTemplateMatchResult,
    ): Boolean {
        val (screenW, screenH) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()
        val maxDy = RefCoords.scaleY(80, screenH)
        if (abs(switchMatch.centerY - wireHud.centerY) > maxDy) {
            return false
        }
        // Switch is to the right of `[Wire N]` text.
        if (switchMatch.centerX < wireHud.bestX + wireHud.templateWidth / 2) {
            return false
        }
        val minX = (screenW * 0.55f).toInt()
        return switchMatch.centerX >= minX
    }

    private suspend fun closeChatIfOpen(): Boolean {
        val open = NavigationVision.findTemplate(CHAT_OPEN, CHAT_THRESHOLD) ?: return false
        Log.w(TAG, "[WIRE] chat open detected at=(${open.centerX},${open.centerY}) — closing")
        val close = NavigationVision.findTemplate(CHAT_CLOSE, CHAT_THRESHOLD)
        if (close != null) {
            NavigationVision.tapMatch(close)
        } else {
            NavigationVision.tapMatch(open)
        }
        delay(700L)
        return NavigationVision.findTemplate(CHAT_OPEN, CHAT_THRESHOLD) == null
    }

    private fun layoutFromTitle(
        title: PcTemplateMatchResult,
        fallbackScroll: SwipeCoords,
    ): PopupLayout {
        val (screenW, screenH) = ScreenCaptureManager.peekLatestBitmapSize()
            ?: RefCoords.activeScreenSize()

        val gap = RefCoords.scaleY(24, screenH)
        val listTop = min(screenH - 8, title.bestY + title.templateHeight + gap)
        // Must include 3 channel rows (~y256/360/464 @720p). Prior height capped at ~458 and
        // clipped wire 6 so OCR never saw it. Leave a strip for the Switch Line button.
        val listBottom = min(
            screenH - RefCoords.scaleY(160, screenH),
            listTop + RefCoords.scaleY(900, screenH),
        )
        val halfW = RefCoords.scaleX(480, screenW)
        val cx = title.centerX.coerceIn(halfW, screenW - halfW)
        val left = max(0, cx - halfW)
        val right = min(screenW, cx + halfW)
        val listRoi = Rect(left, listTop, right, max(listTop + 8, listBottom))

        val swipePad = RefCoords.scaleY(28, screenH)
        val travel = RefCoords.scaleY(SWIPE_TRAVEL_REF_Y, screenH)
        val midY = (listRoi.top + listRoi.bottom) / 2
        val yBottom = min(listRoi.bottom - swipePad, midY + travel / 2)
        val yTop = max(listRoi.top + swipePad, midY - travel / 2)
        val forward = ScreenSwipe(cx, yBottom, cx, max(yTop, listRoi.top + swipePad))
        val reverse = ScreenSwipe(cx, forward.y2, cx, forward.y1)

        Log.d(
            TAG,
            "[WIRE] layout title=(${title.centerX},${title.centerY}) " +
                "roi=[$left,$listTop-$right,$listBottom] " +
                "swipe (${forward.x1},${forward.y1})->(${forward.x2},${forward.y2}) " +
                "dur=${forward.durationMs}ms travel=${forward.y1 - forward.y2} " +
                "fallbackRef=(${fallbackScroll.x1},${fallbackScroll.y1})",
        )
        return PopupLayout(title, listRoi, forward, reverse)
    }

    private suspend fun findWireOptionWithScroll(
        config: WireSwitchConfig,
        wireId: Int,
        layout: PopupLayout,
    ): WireChannelOcr.ChannelHit? {
        var activeLayout = refreshLayout(layout, config) ?: layout
        val maxSearchScrolls = max(config.popupScroll.maxAttempts, config.availableWires.size * 3)

        for (attempt in 0..maxSearchScrolls) {
            coroutineContext.ensureActive()
            if (BotController.state.value != BotRuntimeState.RUNNING) {
                Log.d(TAG, "[WIRE] aborted — bot not running")
                return null
            }
            if (!requireCapture("findWireOption attempt=${attempt + 1}")) {
                return null
            }

            activeLayout = refreshLayout(activeLayout, config) ?: activeLayout
            val frame = NavigationVision.captureFrame() ?: return null
            val visible = try {
                WireChannelOcr.findVisibleChannels(frame, activeLayout.listRoi)
            } finally {
                frame.recycle()
            }

            val target = visible.firstOrNull { it.wireId == wireId }
            if (target != null) {
                Log.d(
                    TAG,
                    "[WIRE] OCR found wire=$wireId attempt=${attempt + 1} " +
                        "raw=\"${target.rawText}\" at=(${target.centerX},${target.centerY})",
                )
                return target
            }

            Log.d(
                TAG,
                "[WIRE] OCR miss wire=$wireId attempt=${attempt + 1} " +
                    "visible=${visible.map { it.wireId }}",
            )

            if (attempt >= maxSearchScrolls) {
                break
            }

            val topVisible = visible.minByOrNull { it.centerY }?.wireId
            val bottomVisible = visible.maxByOrNull { it.centerY }?.wireId
            val swipe = chooseDirectionalSwipe(
                wireId = wireId,
                topVisible = topVisible,
                bottomVisible = bottomVisible,
                layout = activeLayout,
                attempt = attempt,
            )
            val dir = if (swipe.y2 > swipe.y1) "toward_start" else "toward_end"
            Log.d(
                TAG,
                "[WIRE] scroll dir=$dir top=$topVisible bottom=$bottomVisible want=$wireId " +
                    "screen=(${swipe.x1},${swipe.y1})->(${swipe.x2},${swipe.y2}) " +
                    "dur=${swipe.durationMs}ms attempt=${attempt + 1}",
            )
            NavigationVision.swipeScreen(swipe.x1, swipe.y1, swipe.x2, swipe.y2, swipe.durationMs)
            delay(SCROLL_WAIT_MS)
        }

        Log.w(TAG, "[WIRE] OCR option not found wire=$wireId")
        return null
    }

    /**
     * Decide scroll from current OCR-visible range:
     * - top visible > target → swipe toward list start (earlier / lower wires)
     * - bottom visible < target → swipe toward list end (later / higher wires)
     */
    private fun chooseDirectionalSwipe(
        wireId: Int,
        topVisible: Int?,
        bottomVisible: Int?,
        layout: PopupLayout,
        attempt: Int,
    ): ScreenSwipe {
        return when {
            topVisible != null && topVisible > wireId -> layout.reverseSwipe
            bottomVisible != null && bottomVisible < wireId -> layout.forwardSwipe
            topVisible != null && bottomVisible != null &&
                wireId in topVisible..bottomVisible -> {
                if (attempt % 2 == 0) layout.forwardSwipe else layout.reverseSwipe
            }
            else -> if (attempt % 2 == 0) layout.forwardSwipe else layout.reverseSwipe
        }
    }

    private suspend fun refreshLayout(
        layout: PopupLayout,
        config: WireSwitchConfig,
    ): PopupLayout? {
        val title = NavigationVision.findTemplate(config.templates.popupOpen, WIRE_THRESHOLD)
            ?: return null
        return layoutFromTitle(title, config.popupScroll)
    }

    private suspend fun confirmWireSwitch(
        config: WireSwitchConfig,
        layout: PopupLayout,
    ): Boolean {
        val enterPath = config.templates.enterButton
        val enterRoi = NavigationVision.wirePopupEnterRoiFromList(layout.listRoi)

        var enter = NavigationVision.waitForTemplate(
            assetPath = enterPath,
            threshold = WIRE_ENTER_THRESHOLD,
            timeoutMs = WIRE_ENTER_WAIT_MS,
            pollMs = 300L,
            roi = enterRoi,
        )
        if (enter == null) {
            enter = NavigationVision.waitForTemplate(
                assetPath = enterPath,
                threshold = WIRE_ENTER_THRESHOLD,
                timeoutMs = 1500L,
                pollMs = 300L,
            )
        }

        if (enter != null) {
            Log.d(
                TAG,
                "[WIRE] confirming switch score=${enter.score} at=(${enter.centerX},${enter.centerY}) " +
                    "wait=${config.switchWaitSeconds}s",
            )
            NavigationVision.tapMatch(enter)
            delay(config.switchWaitSeconds * 1000L)
            return true
        }

        NavigationVision.logBestScore(enterPath, enterRoi)
        Log.w(TAG, "[WIRE] enter button not found — fallback ref tap ($WIRE_ENTER_REF_X,$WIRE_ENTER_REF_Y)")
        if (!NavigationVision.tap(WIRE_ENTER_REF_X, WIRE_ENTER_REF_Y)) {
            return false
        }
        delay(config.switchWaitSeconds * 1000L)
        return true
    }

    private suspend fun dismissWirePopup(config: WireSwitchConfig) {
        if (NavigationVision.findTemplate(config.templates.popupOpen, WIRE_THRESHOLD) == null) {
            return
        }
        Log.d(TAG, "[WIRE] dismissing open popup after failure")
        NavigationVision.findTemplate(config.templates.switchButton, WIRE_THRESHOLD)?.let {
            NavigationVision.tapMatch(it)
            delay(800L)
        }
        if (NavigationVision.findTemplate(config.templates.popupOpen, WIRE_THRESHOLD) != null) {
            NavigationVision.findTemplate(
                MapWindowActions.CLOSE_X,
                NavigationTemplateThresholds.closeX(),
            )?.let { close ->
                NavigationVision.tapMatch(close)
                delay(600L)
            }
        }
        closeChatIfOpen()
    }

    private fun hudTemplate(config: WireSwitchConfig, wireId: Int): String? {
        val path = config.templates.hud[wireId] ?: return null
        return path.takeIf { TemplateRepository.getByPath(it) != null }
    }

    private fun requireCapture(phase: String): Boolean {
        if (ScreenCaptureManager.isReady()) {
            return true
        }
        Log.e(
            TAG,
            "[WIRE] capture not ready phase=$phase active=${ScreenCaptureManager.isActiveNow()} " +
                "hasFrame=${ScreenCaptureManager.hasFrame()}",
        )
        return false
    }
}
