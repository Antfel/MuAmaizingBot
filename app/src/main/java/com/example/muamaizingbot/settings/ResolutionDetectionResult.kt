package com.example.muamaizingbot.settings

data class ResolutionDetectionResult(
    val displayWidth: Int,
    val displayHeight: Int,
    val matchType: MatchType,
    val suggestedPreset: ResolutionPreset?,
    val userMessage: String,
) {
    enum class MatchType {
        /** Exact match in templates/{WxH}/ */
        EXACT,
        /** Nearest supported preset (e.g. 3088×1440 → 2560×1440). */
        NEAREST,
        /** Display too small or unsupported for bot templates. */
        UNSUPPORTED,
    }

    val isSupported: Boolean get() = matchType != MatchType.UNSUPPORTED && suggestedPreset != null
}
