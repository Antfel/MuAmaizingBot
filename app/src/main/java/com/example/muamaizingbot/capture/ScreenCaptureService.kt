package com.example.muamaizingbot.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.core.content.IntentCompat
import androidx.core.app.NotificationCompat
import com.example.muamaizingbot.R
import com.example.muamaizingbot.vision.coord.RefCoords

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "[CAPTURE] media projection stopped")
            stopCaptureInternal()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[CAPTURE] service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        if (resultData == null) {
            Log.e(TAG, "[CAPTURE] start failed reason=missing_result_data")
            stopSelf()
            return START_NOT_STICKY
        }

        startCapture(resultCode, resultData)
        return START_STICKY
    }

    override fun onDestroy() {
        stopCaptureInternal()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        stopCaptureInternal()

        val (width, height, density) = resolveCaptureBounds()

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Log.e(TAG, "[CAPTURE] start failed reason=null_projection")
            stopSelf()
            return
        }

        mediaProjection = projection
        projection.registerCallback(projectionCallback, null)

        captureThread = HandlerThread("ScreenCaptureThread").apply { start() }
        captureHandler = Handler(captureThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    ScreenCaptureManager.updateFrame(bitmap)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "[CAPTURE] frame error message=${t.message}")
            } finally {
                image.close()
            }
        }, captureHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "MuBotCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )

        ScreenCaptureManager.setActive(true)
        Log.d(TAG, "[CAPTURE] started width=$width height=$height density=$density")
    }

    /**
     * Prefer physical default-display size over [resources.displayMetrics].
     * A Service context often reports portrait (720×1280) while the emulator is landscape
     * 1280×720; that swapped virtual display breaks all template matches and tap scaling.
     */
    private fun resolveCaptureBounds(): Triple<Int, Int, Int> {
        val density = resources.displayMetrics.densityDpi
        val metricsW = resources.displayMetrics.widthPixels
        val metricsH = resources.displayMetrics.heightPixels

        val (rawW, rawH) = readRealDisplaySize()
        val width: Int
        val height: Int
        if (rawW >= rawH) {
            width = rawW
            height = rawH
        } else {
            width = rawH
            height = rawW
            Log.w(
                TAG,
                "[CAPTURE] portrait metrics corrected ${rawW}x${rawH} → ${width}x${height} " +
                    "(serviceMetrics=${metricsW}x${metricsH})",
            )
        }

        if (width != RefCoords.TARGET_WIDTH || height != RefCoords.TARGET_HEIGHT) {
            Log.w(
                TAG,
                "[CAPTURE] size ${width}x${height} differs from contract " +
                    "${RefCoords.TARGET_WIDTH}x${RefCoords.TARGET_HEIGHT}",
            )
        }
        return Triple(width, height, density)
    }

    private fun readRealDisplaySize(): Pair<Int, Int> {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (display != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val mode = display.mode
                if (mode.physicalWidth > 0 && mode.physicalHeight > 0) {
                    return mode.physicalWidth to mode.physicalHeight
                }
            }
            @Suppress("DEPRECATION")
            val size = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            if (size.x > 0 && size.y > 0) {
                return size.x to size.y
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = (getSystemService(WINDOW_SERVICE) as WindowManager)
                .maximumWindowMetrics.bounds
            if (bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }

        return resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
    }

    private fun stopCaptureInternal() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        ScreenCaptureManager.clearFrame()
        ScreenCaptureManager.setActive(false)
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        if (image.planes.isEmpty()) {
            return null
        }
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also {
                bitmap.recycle()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.capture_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_capture_service"
        private const val NOTIFICATION_ID = 1002
    }
}
