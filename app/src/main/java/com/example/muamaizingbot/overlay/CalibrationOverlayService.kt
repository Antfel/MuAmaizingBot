package com.example.muamaizingbot.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.muamaizingbot.R
import com.example.muamaizingbot.calibration.CalibrationAnchor
import com.example.muamaizingbot.calibration.CalibrationMatcher
import com.example.muamaizingbot.calibration.CalibrationRepository
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.overlay.ui.CalibrationInstructionPanel
import com.example.muamaizingbot.overlay.ui.CalibrationMarker
import com.example.muamaizingbot.ui.theme.MUAmaizingBotTheme
import com.example.muamaizingbot.vision.coord.RefCoords
import kotlin.math.roundToInt

class CalibrationOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private val compositionOwner = OverlayCompositionOwner()

    private var markerView: ComposeView? = null
    private var markerParams: WindowManager.LayoutParams? = null
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var stepIndex by mutableIntStateOf(0)
    private var markerCenterX by mutableIntStateOf(0)
    private var markerCenterY by mutableIntStateOf(0)
    private var markerWidthPx by mutableIntStateOf(0)
    private var markerHeightPx by mutableIntStateOf(0)
    private var markerWidthDp by mutableFloatStateOf(48f)
    private var markerHeightDp by mutableFloatStateOf(24f)

    override fun onCreate() {
        super.onCreate()
        compositionOwner.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        CalibrationOverlayManager.isRunning = true
        startInForeground()
        placeStep(0)
        showOverlays()
        Log.d(TAG, "[CAL-OVERLAY] service started")
    }

    override fun onDestroy() {
        removeOverlays()
        compositionOwner.onDestroy()
        CalibrationOverlayManager.isRunning = false
        Log.d(TAG, "[CAL-OVERLAY] service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun placeStep(index: Int) {
        stepIndex = index
        CalibrationOverlayManager.setStep(index)
        val anchor = CalibrationAnchor.ordered[index]
        updateMarkerDimensions(anchor)
        val frame = ScreenCaptureManager.getLatestBitmap()
        val suggested = if (frame != null) {
            CalibrationMatcher.suggestScreenPoint(this, frame, anchor)
        } else {
            null
        }
        val (screenW, screenH) = OverlayBounds.displaySize(windowManager)
        val (defaultX, defaultY) = if (frame != null) {
            CalibrationMatcher.defaultScreenPoint(frame.width, frame.height, anchor)
        } else {
            CalibrationMatcher.defaultScreenPoint(screenW, screenH, anchor)
        }
        markerCenterX = suggested?.first ?: defaultX
        markerCenterY = suggested?.second ?: defaultY
        updateMarkerPosition()
        updatePanelPosition(anchor)
        refreshOverlayContent()
        Log.d(TAG, "[CAL-OVERLAY] step=$index anchor=${anchor.id} marker=($markerCenterX,$markerCenterY)")
    }

    private fun updateMarkerDimensions(anchor: CalibrationAnchor) {
        val (w, h) = captureSize()
        markerWidthPx = (anchor.refTemplateWidth.toLong() * w / RefCoords.REF_WIDTH).toInt()
            .coerceAtLeast(pxFromDp(48f))
        markerHeightPx = (anchor.refTemplateHeight.toLong() * h / RefCoords.REF_HEIGHT).toInt()
            .coerceAtLeast(pxFromDp(24f))
        val density = resources.displayMetrics.density
        markerWidthDp = markerWidthPx / density
        markerHeightDp = markerHeightPx / density
    }

    private fun captureSize(): Pair<Int, Int> {
        ScreenCaptureManager.peekLatestBitmapSize()?.let { return it }
        return OverlayBounds.displaySize(windowManager)
    }

    private fun pxFromDp(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics,
        ).roundToInt()
    }

    private fun confirmCurrentStep() {
        val anchor = CalibrationAnchor.ordered[stepIndex]
        CalibrationRepository.recordScreenPoint(anchor, markerCenterX, markerCenterY)
        if (stepIndex + 1 >= CalibrationAnchor.ordered.size) {
            val ok = CalibrationRepository.completeSession(this)
            if (ok) {
                stopSelf()
            } else {
                Log.e(TAG, "[CAL-OVERLAY] complete failed")
            }
            return
        }
        placeStep(stepIndex + 1)
    }

    private fun cancelCalibration() {
        CalibrationRepository.cancelSession()
        stopSelf()
    }

    private fun showOverlays() {
        if (markerView != null) {
            return
        }

        markerParams = overlayParams().apply {
            gravity = Gravity.TOP or Gravity.START
        }

        markerView = ComposeView(this).apply {
            attachCompositionOwner(this)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(compositionOwner.lifecycle),
            )
            setContent { renderMarker() }
        }

        panelParams = overlayParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }

        panelView = ComposeView(this).apply {
            attachCompositionOwner(this)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(compositionOwner.lifecycle),
            )
            setContent { renderInstructionPanel() }
        }

        windowManager.addView(markerView, markerParams)
        windowManager.addView(panelView, panelParams)
        updatePanelPosition(CalibrationAnchor.ordered[stepIndex])
        updateMarkerPosition()
    }

    private fun updatePanelPosition(anchor: CalibrationAnchor) {
        val view = panelView ?: return
        val params = panelParams ?: return
        params.gravity = if (anchor.panelAtBottom) Gravity.BOTTOM else Gravity.TOP
        windowManager.updateViewLayout(view, params)
    }

    private fun ComposeView.attachCompositionOwner(view: ComposeView) {
        view.setViewTreeLifecycleOwner(compositionOwner)
        view.setViewTreeViewModelStoreOwner(compositionOwner)
        view.setViewTreeSavedStateRegistryOwner(compositionOwner)
    }

    private fun refreshOverlayContent() {
        markerView?.setContent { renderMarker() }
        panelView?.setContent { renderInstructionPanel() }
    }

    @Composable
    private fun renderMarker() {
        MUAmaizingBotTheme {
            CalibrationMarker(
                frameWidth = markerWidthDp.dp,
                frameHeight = markerHeightDp.dp,
                onDragBy = { dx, dy ->
                    markerCenterX += dx
                    markerCenterY += dy
                    updateMarkerPosition()
                },
            )
        }
    }

    @Composable
    private fun renderInstructionPanel() {
        val anchor = CalibrationAnchor.ordered[stepIndex]
        MUAmaizingBotTheme {
            CalibrationInstructionPanel(
                stepIndex = stepIndex,
                stepLabel = anchor.label,
                totalSteps = CalibrationAnchor.ordered.size,
                markerX = markerCenterX,
                markerY = markerCenterY,
                panelAtBottom = anchor.panelAtBottom,
                onConfirm = { confirmCurrentStep() },
                onCancel = { cancelCalibration() },
            )
        }
    }

    private fun updateMarkerPosition() {
        val view = markerView ?: return
        val params = markerParams ?: return
        val halfW = markerWidthPx / 2
        val halfH = markerHeightPx / 2
        val (screenW, screenH) = OverlayBounds.displaySize(windowManager)
        markerCenterX = markerCenterX.coerceIn(halfW, (screenW - halfW).coerceAtLeast(halfW))
        markerCenterY = markerCenterY.coerceIn(halfH, (screenH - halfH).coerceAtLeast(halfH))
        params.width = markerWidthPx
        params.height = markerHeightPx
        params.x = markerCenterX - halfW
        params.y = markerCenterY - halfH
        windowManager.updateViewLayout(view, params)
        panelView?.setContent { renderInstructionPanel() }
    }

    private fun removeOverlays() {
        markerView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        markerView = null
        panelView = null
        markerParams = null
        panelParams = null
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun startInForeground() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.calibration_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.calibration_notification_title))
            .setContentText(getString(R.string.calibration_notification_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "CalOverlay"
        private const val CHANNEL_ID = "calibration_overlay"
        private const val NOTIFICATION_ID = 1002
    }
}
