package com.example.muamaizingbot.vision

import android.util.Log
import com.example.muamaizingbot.bot.combat.AutoModeDetection
import com.example.muamaizingbot.bot.combat.AutoModeDetector
import com.example.muamaizingbot.capture.ScreenCaptureManager
import com.example.muamaizingbot.vision.roi.MuCombatRois
import com.example.muamaizingbot.vision.template.PcTemplateMatcher
import com.example.muamaizingbot.vision.template.PcTemplateMatchResult
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TemplateMatcherTest {

    private const val TAG = "TemplateMatcherTest"

    private val phase3Templates = listOf(
        "auto_text",
        "manual_text"
    )

    @Volatile
    var lastResults: Map<String, PcTemplateMatchResult> = emptyMap()
        private set

    suspend fun runAndLog() {
        withContext(Dispatchers.Default) {
            val frame = ScreenCaptureManager.getLatestBitmap()
            if (frame == null) {
                Log.w(TAG, "[MATCH] test skipped reason=no_frame")
                return@withContext
            }

            val startedAt = System.currentTimeMillis()
            val results = mutableMapOf<String, PcTemplateMatchResult>()
            try {
                val hudRoi = MuCombatRois.autoHudRoi(frame)
                Log.d(
                    TAG,
                    "[MATCH] test start engine=opencv frame=${frame.width}x${frame.height} " +
                        "roi=${hudRoi.left},${hudRoi.top}-${hudRoi.right},${hudRoi.bottom}"
                )

                for (templateName in phase3Templates) {
                    val templateInfo = TemplateRepository.getByName(templateName)
                    if (templateInfo == null) {
                        Log.w(TAG, "[MATCH] template missing name=$templateName")
                        continue
                    }

                    val templateStartedAt = System.currentTimeMillis()
                    val result = PcTemplateMatcher.matchDebug(
                        source = frame,
                        template = templateInfo.bitmap,
                        templateName = templateInfo.sourceName,
                        category = templateInfo.category,
                        roi = hudRoi
                    )
                    results[templateName] = result
                    val elapsedMs = System.currentTimeMillis() - templateStartedAt
                    Log.d(
                        TAG,
                        "[MATCH] template=$templateName score=${result.score} " +
                            "best=(${result.bestX},${result.bestY}) " +
                            "size=${result.templateWidth}x${result.templateHeight} " +
                            "elapsedMs=$elapsedMs"
                    )
                }

                lastResults = results.toMap()
                val totalMs = System.currentTimeMillis() - startedAt
                Log.d(TAG, "[MATCH] test done totalMs=$totalMs")
            } finally {
                frame.recycle()
            }
        }
    }

    suspend fun detectAutoMode(frame: android.graphics.Bitmap): AutoModeDetection {
        return AutoModeDetector.detect(frame)
    }
}
