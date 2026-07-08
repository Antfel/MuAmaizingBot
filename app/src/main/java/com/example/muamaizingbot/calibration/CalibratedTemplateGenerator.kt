package com.example.muamaizingbot.calibration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object CalibratedTemplateGenerator {

    private const val TAG = "CalTemplateGen"

    fun generate(
        context: Context,
        transform: CalibrationTransform,
        captureWidth: Int,
        captureHeight: Int,
    ): Int {
        val appContext = context.applicationContext
        CalibratedTemplateStore.clearForCapture(appContext, captureWidth, captureHeight)
        val muRoot = CalibratedTemplateStore.templatesRoot(appContext, captureWidth, captureHeight)
        muRoot.mkdirs()

        val scaleX = transform.averageCalibrationScaleX()
        val scaleY = transform.averageCalibrationScaleY()
        var count = 0

        walkAssetTemplates(appContext, TemplateSourceResolution.ASSET_ROOT) { assetPath, relative ->
            val source = appContext.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@walkAssetTemplates

            val (refCx, refCy) = TemplateRefRegions.estimateRefCenter(relative)
            val (targetW, targetH) = transform.calibratedTemplateSize(
                source.width,
                source.height,
                refCx,
                refCy,
            )
            val scaled = if (targetW == source.width && targetH == source.height) {
                source
            } else {
                Bitmap.createScaledBitmap(source, targetW, targetH, true).also {
                    if (it !== source) {
                        source.recycle()
                    }
                }
            }

            val outFile = File(muRoot, relative)
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { stream ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            scaled.recycle()
            count++
        }

        CalibratedTemplateStore.writeManifest(
            context = appContext,
            captureWidth = captureWidth,
            captureHeight = captureHeight,
            templateCount = count,
            scaleX = scaleX,
            scaleY = scaleY,
        )
        CalibratedTemplateStore.clearExcept(appContext, captureWidth, captureHeight)

        Log.d(
            TAG,
            "[CAL-TPL] generated count=$count capture=${captureWidth}x$captureHeight " +
                "scaleX=${"%.3f".format(scaleX)} scaleY=${"%.3f".format(scaleY)}",
        )
        return count
    }

    private fun walkAssetTemplates(
        context: Context,
        physicalRoot: String,
        onPng: (assetPath: String, relativePath: String) -> Unit,
    ) {
        val assetManager = context.assets
        val entries = assetManager.list(physicalRoot) ?: return
        for (entry in entries) {
            val fullPath = "$physicalRoot/$entry"
            if (entry.endsWith(".png", ignoreCase = true)) {
                val relative = fullPath.removePrefix("${TemplateSourceResolution.ASSET_ROOT}/")
                onPng(fullPath, relative)
            } else {
                walkAssetTemplates(context, fullPath, onPng)
            }
        }
    }
}
