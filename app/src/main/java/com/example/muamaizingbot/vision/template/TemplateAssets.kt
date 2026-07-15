package com.example.muamaizingbot.vision.template

/**
 * Canonical template paths are `templates/mu/...`.
 * Physical assets live under the same path in app assets (fixed 1280×720 pack).
 */
object TemplateAssets {

    const val CANONICAL_PREFIX = "templates/mu"
    const val ASSET_ROOT = "templates/mu"

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
}
