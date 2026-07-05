package com.example.muamaizingbot.settings

enum class ResolutionPreset(
    val label: String,
    val width: Int,
    val height: Int,
) {
    AUTO("Automática (desde captura)", 0, 0),
    REF_2560x1440("2560 × 1440 (BlueStacks)", 2560, 1440),
    REF_1920x1080("1920 × 1080", 1920, 1080),
    REF_1280x720("1280 × 720", 1280, 720),
    ;

    val isAuto: Boolean get() = this == AUTO

    val resolutionKey: String get() = "${width}x$height"
}
