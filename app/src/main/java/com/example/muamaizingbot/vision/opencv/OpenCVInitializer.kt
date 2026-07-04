package com.example.muamaizingbot.vision.opencv

import android.system.Os
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core

object OpenCVInitializer {

    private const val TAG = "OpenCV"

    @Volatile
    var isInitialized: Boolean = false
        private set

    fun init(): Boolean {
        if (isInitialized) {
            return true
        }
        disableAdvancedCpuFeatures()
        val loaded = OpenCVLoader.initLocal()
        if (loaded) {
            Core.setUseOptimized(false)
            Core.setNumThreads(1)
            Log.d(TAG, "[OpenCV] loaded successfully version=4.9.0 threads=1 optimized=false")
        } else {
            Log.e(TAG, "[OpenCV] initialization failed")
        }
        isInitialized = loaded
        return loaded
    }

    /** Must run before OpenCVLoader loads libopencv_java4.so (BlueStacks SIGILL on NEON_DOTPROD). */
    private fun disableAdvancedCpuFeatures() {
        val disable = "NEON_DOTPROD,NEON_FP16,NEON_BF16,NEON,FP16,BF16,KLEIDICV"
        try {
            Os.setenv("OPENCV_CPU_DISABLE", disable, true)
        } catch (t: Throwable) {
            System.setProperty("OPENCV_CPU_DISABLE", disable)
        }
    }
}
