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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.muamaizingbot.R
import com.example.muamaizingbot.overlay.ui.OverlayHudStyle
import com.example.muamaizingbot.overlay.ui.OverlayPanel
import com.example.muamaizingbot.ui.theme.MUAmaizingBotTheme
import kotlin.math.roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var positionStore: OverlayPositionStore
    private val compositionOwner = OverlayCompositionOwner()
    private var overlayView: ComposeView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var layoutListener: View.OnLayoutChangeListener? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[OVERLAY] service onCreate")
        compositionOwner.onCreate()
        OverlayManager.markRunning(true)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        positionStore = OverlayPositionStore(this)
        startInForeground()
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "[OVERLAY] service onDestroy")
        removeOverlay()
        compositionOwner.onDestroy()
        OverlayManager.markRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            getString(R.string.overlay_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun edgeMarginPx(): Int {
        return (8 * resources.displayMetrics.density).roundToInt()
    }

    private fun estimatedOverlaySizePx(): Int {
        val density = resources.displayMetrics.density
        return (OverlayHudStyle.bubbleSize.value * density).roundToInt()
    }

    private fun showOverlay() {
        if (overlayView != null) {
            return
        }

        val (screenW, screenH) = OverlayBounds.displaySize(windowManager)
        val estimated = estimatedOverlaySizePx()
        val (startX, startY) = OverlayBounds.defaultPosition(
            screenWidth = screenW,
            screenHeight = screenH,
            overlayWidth = estimated,
            overlayHeight = estimated,
            marginPx = edgeMarginPx(),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(compositionOwner)
            setViewTreeViewModelStoreOwner(compositionOwner)
            setViewTreeSavedStateRegistryOwner(compositionOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(compositionOwner.lifecycle),
            )
            setContent {
                MUAmaizingBotTheme {
                    OverlayPanel(
                        onDragBy = { dx, dy ->
                            val windowParams = overlayLayoutParams ?: return@OverlayPanel
                            val view = overlayView ?: return@OverlayPanel
                            windowParams.x += dx
                            windowParams.y += dy
                            applyClampedPosition(view, windowParams)
                        },
                        onDragEnd = {
                            overlayLayoutParams?.let { positionStore.save(it.x, it.y) }
                        },
                    )
                }
            }
        }

        val layoutChangeListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            overlayLayoutParams?.let { applyClampedPosition(view, it) }
        }
        layoutListener = layoutChangeListener
        composeView.addOnLayoutChangeListener(layoutChangeListener)

        windowManager.addView(composeView, params)
        overlayView = composeView
        overlayLayoutParams = params

        composeView.post {
            placeAtDefaultStartPosition()
        }

        Log.d(TAG, "[OVERLAY] overlay view added x=$startX y=$startY screen=${screenW}x$screenH")
    }

    private fun placeAtDefaultStartPosition() {
        val view = overlayView ?: return
        val params = overlayLayoutParams ?: return
        val (screenW, screenH) = OverlayBounds.displaySize(windowManager)
        val w = view.width.coerceAtLeast(estimatedOverlaySizePx())
        val h = view.height.coerceAtLeast(estimatedOverlaySizePx())
        val (x, y) = OverlayBounds.defaultPosition(
            screenWidth = screenW,
            screenHeight = screenH,
            overlayWidth = w,
            overlayHeight = h,
            marginPx = edgeMarginPx(),
        )
        params.x = x
        params.y = y
        windowManager.updateViewLayout(view, params)
        positionStore.save(x, y)
        Log.d(TAG, "[OVERLAY] placed default x=$x y=$y size=${w}x$h")
    }

    private fun applyClampedPosition(view: View, params: WindowManager.LayoutParams) {
        val (screenW, screenH) = OverlayBounds.displaySize(windowManager)
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val (clampedX, clampedY) = OverlayBounds.clamp(
            x = params.x,
            y = params.y,
            screenWidth = screenW,
            screenHeight = screenH,
            overlayWidth = w,
            overlayHeight = h,
        )
        if (params.x != clampedX || params.y != clampedY) {
            params.x = clampedX
            params.y = clampedY
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            layoutListener?.let { view.removeOnLayoutChangeListener(it) }
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        overlayLayoutParams = null
        layoutListener = null
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1001
    }
}
