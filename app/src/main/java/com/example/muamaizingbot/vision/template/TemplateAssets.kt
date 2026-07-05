package com.example.muamaizingbot.vision.template

import com.example.muamaizingbot.settings.ResolutionSettingsRepository
import com.example.muamaizingbot.vision.coord.RefCoords

/**
 * Canonical template paths stay as `templates/mu/...` (2560×1440 reference).
 * Physical assets live under `templates/{width}x{height}/mu/...`.
 */
object TemplateAssets {

    const val CANONICAL_PREFIX = "templates/mu"
    const val REF_RESOLUTION_KEY = "2560x1440"

    val SUPPORTED_RESOLUTION_KEYS = listOf(
        REF_RESOLUTION_KEY,
        "1920x1080",
        "1280x720",
    )

    fun resolutionKey(width: Int, height: Int): String = "${width}x$height"

    /** Resolution folder used to load pre-scaled PNG assets. */
    fun templateResolutionKey(): String {
        val preset = ResolutionSettingsRepository.preset.value
        if (!preset.isAuto) {
            return preset.resolutionKey
        }
        val capture = ResolutionSettingsRepository.detectedCaptureSize()
        if (capture != null) {
            return snapToSupported(capture.first, capture.second)
        }
        return REF_RESOLUTION_KEY
    }

    fun snapToSupported(width: Int, height: Int): String {
        val exact = resolutionKey(width, height)
        if (exact in SUPPORTED_RESOLUTION_KEYS) {
            return exact
        }
        val ratio = minOf(
            width.toFloat() / RefCoords.REF_WIDTH,
            height.toFloat() / RefCoords.REF_HEIGHT,
        )
        return when {
            ratio >= 0.95f -> REF_RESOLUTION_KEY
            ratio >= 0.65f -> "1920x1080"
            else -> "1280x720"
        }
    }

    fun toPhysicalPath(canonicalPath: String, resolutionKey: String = templateResolutionKey()): String {
        val normalized = normalizeToCanonical(canonicalPath)
        require(normalized.startsWith("$CANONICAL_PREFIX/")) {
            "Not a canonical template path: $canonicalPath"
        }
        val relative = normalized.removePrefix("$CANONICAL_PREFIX/")
        return "templates/$resolutionKey/mu/$relative"
    }

    fun normalizeToCanonical(path: String): String {
        return when {
            path.startsWith("$CANONICAL_PREFIX/") -> path
            path.startsWith("templates/maps/") ->
                path.replaceFirst("templates/maps/", "$CANONICAL_PREFIX/maps/")
            path.startsWith("templates/ui/") ->
                path.replaceFirst("templates/ui/", "$CANONICAL_PREFIX/ui/")
            path.startsWith("templates/wires/") ->
                path.replaceFirst("templates/wires/", "$CANONICAL_PREFIX/wires/")
            else -> path
        }
    }

    fun parseResolutionKey(key: String): Pair<Int, Int>? {
        val parts = key.split('x')
        if (parts.size != 2) {
            return null
        }
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return w to h
    }
}
