package com.example.muamaizingbot.bot.combat

import android.graphics.Bitmap
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.template.PcTemplateMatcher
import com.example.muamaizingbot.vision.template.TemplateRepository

object AutoModeDetector {

    private const val THRESHOLD = 0.80f

    fun detect(frame: Bitmap): AutoModeDetection {
        val hudRoi = MuCombatRois.autoHudRoi(frame)
        val autoTemplate = TemplateRepository.getByName("auto_text")
        val manualTemplate = TemplateRepository.getByName("manual_text")

        val autoMatch = autoTemplate?.let {
            PcTemplateMatcher.matchDebug(
                source = frame,
                template = it.bitmap,
                templateName = it.sourceName,
                category = it.category,
                roi = hudRoi
            )
        }

        val manualMatch = manualTemplate?.let {
            PcTemplateMatcher.findTemplate(
                source = frame,
                template = it.bitmap,
                threshold = THRESHOLD,
                templateName = it.sourceName,
                category = it.category,
                roi = hudRoi
            )
        }

        return AutoModeDetection(
            isAutoMode = (autoMatch?.score ?: 0f) >= THRESHOLD,
            isManualMode = manualMatch != null,
            autoScore = autoMatch?.score ?: 0f,
            manualScore = manualMatch?.score ?: 0f,
            manualTapX = manualMatch?.centerX,
            manualTapY = manualMatch?.centerY
        )
    }
}

data class AutoModeDetection(
    val isAutoMode: Boolean,
    val isManualMode: Boolean,
    val autoScore: Float,
    val manualScore: Float,
    val manualTapX: Int?,
    val manualTapY: Int?
)
