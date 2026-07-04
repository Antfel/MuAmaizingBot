package com.example.muamaizingbot.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

object ScreenCapturePermission {

    fun createRequestIntent(context: Context): Intent {
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        return projectionManager.createScreenCaptureIntent()
    }

    fun handleResult(
        context: Context,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return false
        }
        ScreenCaptureManager.start(context, resultCode, data)
        return true
    }
}
