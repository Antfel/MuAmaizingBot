package com.example.muamaizingbot.vision.template

data class PcTemplateMatchResult(
    val score: Float,
    val bestX: Int,
    val bestY: Int,
    val templateWidth: Int,
    val templateHeight: Int,
    val templateName: String = "unknown",
    val category: String = "unknown"
) {
    val centerX: Int
        get() = bestX + templateWidth / 2

    val centerY: Int
        get() = bestY + templateHeight / 2
}
