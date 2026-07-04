package com.example.muamaizingbot.overlay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object OverlayManager {

    @Volatile
    var isRunning: Boolean = false
        private set

    internal fun markRunning(running: Boolean) {
        isRunning = running
    }

    fun start(context: Context) {
        val appContext = context.applicationContext
        OverlayPositionStore(appContext).clear()
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, OverlayService::class.java),
        )
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.stopService(Intent(appContext, OverlayService::class.java))
    }
}
