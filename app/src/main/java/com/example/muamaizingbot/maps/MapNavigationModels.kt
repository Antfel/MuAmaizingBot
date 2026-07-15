package com.example.muamaizingbot.maps

import org.json.JSONObject

data class SwipeCoords(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val durationMs: Long = 300L,
    val maxAttempts: Int = 10,
)

data class MapNavigation(
    val behavior: String,
    val currentMapTemplate: String = "",
    val currentMapThreshold: Float = 0.72f,
    val mapHeadTemplate: String = "",
    val mapOptionTemplate: String = "",
    /** Floor/row label inside the enter dialog (e.g. modalui/kalima_N.png). */
    val modalOptionTemplate: String = "",
    /** Common checked checkbox icon (left of modal row). */
    val checkedTemplate: String = "",
    /** Common unchecked checkbox icon (left of modal row). */
    val uncheckedTemplate: String = "",
    val enterTemplate: String = "",
    val enterWaitSeconds: Int = 8,
    val mapListSwipe: SwipeCoords? = null,
) {
    val isDirectTeleport: Boolean get() = behavior == "direct_teleport"
    val isModalEnter: Boolean get() = behavior == "modal_enter"
    val isImplemented: Boolean get() = isDirectTeleport || isModalEnter
}

data class WireSwitchTemplates(
    val switchButton: String,
    val popupOpen: String,
    val enterButton: String,
    val selected: String,
    val options: Map<Int, String>,
    val hud: Map<Int, String>,
)

data class WireSwitchConfig(
    val enabled: Boolean,
    val availableWires: List<Int>,
    val hudDetection: Boolean,
    val templates: WireSwitchTemplates,
    val popupScroll: SwipeCoords,
    val switchWaitSeconds: Int = 5,
)

object MapNavigationParser {

    private val IMPLEMENTED_BEHAVIORS = setOf("direct_teleport", "modal_enter")

    private const val DEFAULT_CHECKED_TEMPLATE = "templates/ui/common/checked.png"
    private const val DEFAULT_UNCHECKED_TEMPLATE = "templates/ui/common/unchecked.png"

    private val COMMON_WIRE_DEFAULTS = WireSwitchTemplates(
        switchButton = "templates/mu/wires/common/switch_button.png",
        popupOpen = "templates/mu/wires/common/wire_popup_open.png",
        enterButton = "templates/mu/wires/common/wire_enter_button.png",
        selected = "templates/mu/wires/common/wire_selected.png",
        options = emptyMap(),
        hud = emptyMap(),
    )

    fun parseNavigation(json: JSONObject): MapNavigation? {
        val nav = json.optJSONObject("navigation") ?: return null
        val behavior = nav.optString("behavior", "modal_enter").trim()
        val modalOptionRaw = nav.optString("modal_option_template", "").trim()
        val checkedRaw = nav.optString("checked_template", "").trim()
        val uncheckedRaw = nav.optString("unchecked_template", "").trim()

        // Legacy: checked_template pointed at the modal floor label (modalui/kalima_N.png).
        val modalOptionTemplate = when {
            modalOptionRaw.isNotBlank() -> pcPathToAssetPath(modalOptionRaw)
            checkedRaw.contains("modalui") -> pcPathToAssetPath(checkedRaw)
            else -> ""
        }
        val checkedTemplate = when {
            checkedRaw.isNotBlank() && !checkedRaw.contains("modalui") ->
                pcPathToAssetPath(checkedRaw)
            else -> pcPathToAssetPath(DEFAULT_CHECKED_TEMPLATE)
        }
        val uncheckedTemplate = pcPathToAssetPath(
            uncheckedRaw.ifBlank { DEFAULT_UNCHECKED_TEMPLATE },
        )

        return MapNavigation(
            behavior = behavior,
            currentMapTemplate = pcPathToAssetPath(nav.optString("current_map_template", "")),
            currentMapThreshold = nav.optDouble("current_map_threshold", 0.72).toFloat(),
            mapHeadTemplate = pcPathToAssetPath(nav.optString("map_head_template", "")),
            mapOptionTemplate = pcPathToAssetPath(nav.optString("map_option_template", "")),
            modalOptionTemplate = modalOptionTemplate,
            checkedTemplate = checkedTemplate,
            uncheckedTemplate = uncheckedTemplate,
            enterTemplate = pcPathToAssetPath(nav.optString("enter_template", "")),
            enterWaitSeconds = nav.optInt("enter_wait", 8),
            mapListSwipe = parseSwipe(nav.optJSONObject("map_list_swipe")),
        )
    }

    fun parseWireSwitch(json: JSONObject): WireSwitchConfig? {
        val explicit = json.optJSONObject("wire_switch")
        if (explicit != null) {
            return parseExplicitWireSwitch(explicit)
        }
        return buildInferredWireSwitch(json)
    }

    fun isNavigable(navigation: MapNavigation?): Boolean {
        if (navigation == null || !navigation.isImplemented) {
            return false
        }
        if (navigation.mapOptionTemplate.isBlank()) {
            return false
        }
        return when {
            navigation.isModalEnter -> navigation.modalOptionTemplate.isNotBlank()
            navigation.isDirectTeleport -> navigation.currentMapTemplate.isNotBlank()
            else -> false
        }
    }

    private fun parseExplicitWireSwitch(json: JSONObject): WireSwitchConfig? {
        val templatesJson = json.optJSONObject("templates") ?: return null
        val options = parseTemplateMap(templatesJson.optJSONObject("options"))
        val hud = parseTemplateMap(templatesJson.optJSONObject("hud"))
        val available = json.optJSONArray("available_wires")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optInt(it).takeIf { w -> w > 0 } }
        }.orEmpty()

        return WireSwitchConfig(
            enabled = json.optBoolean("enabled", true),
            availableWires = available.ifEmpty { options.keys.sorted() },
            hudDetection = json.optBoolean("hud_detection", true),
            templates = WireSwitchTemplates(
                switchButton = pcPathToAssetPath(
                    templatesJson.optString("switch_button", COMMON_WIRE_DEFAULTS.switchButton)
                ),
                popupOpen = pcPathToAssetPath(
                    templatesJson.optString("popup_open", COMMON_WIRE_DEFAULTS.popupOpen)
                ),
                enterButton = pcPathToAssetPath(
                    templatesJson.optString("enter_button", COMMON_WIRE_DEFAULTS.enterButton)
                ),
                selected = pcPathToAssetPath(
                    templatesJson.optString("selected", COMMON_WIRE_DEFAULTS.selected)
                ),
                options = options,
                hud = hud,
            ),
            // Fallback only — live flow anchors swipe to "Switch Channel" title match.
            popupScroll = parseSwipe(json.optJSONObject("popup_scroll"))
                ?: SwipeCoords(1280, 780, 1280, 420, maxAttempts = 10),
            switchWaitSeconds = json.optInt("switch_wait", 5),
        )
    }

    private fun buildInferredWireSwitch(json: JSONObject): WireSwitchConfig? {
        val wireCount = wireCountFromMetadata(json) ?: return null
        if (wireCount <= 1) {
            return null
        }

        val options = linkedMapOf<Int, String>()
        val hud = linkedMapOf<Int, String>()
        for (wireId in 1..wireCount) {
            val optionPath = "templates/mu/wires/common/wire_${wireId}_option.png"
            options[wireId] = optionPath
            val hudPath = "templates/mu/wires/common/wire_${wireId}_hud.png"
            hud[wireId] = hudPath
        }

        if (options.isEmpty()) {
            return null
        }

        return WireSwitchConfig(
            enabled = true,
            availableWires = (1..wireCount).toList(),
            hudDetection = true,
            templates = COMMON_WIRE_DEFAULTS.copy(options = options, hud = hud),
            popupScroll = SwipeCoords(1280, 780, 1280, 420, maxAttempts = 10),
            switchWaitSeconds = 5,
        )
    }

    private fun wireCountFromMetadata(json: JSONObject): Int? {
        if (json.has("wire") && !json.isNull("wire")) {
            val count = when (val wireValue = json.opt("wire")) {
                is Number -> wireValue.toInt()
                is String -> wireValue.trim().toIntOrNull()
                else -> null
            } ?: json.optInt("wire", 0)
            if (count > 1) {
                return count
            }
        }
        val wires = json.optJSONObject("wires")
        if (wires != null && wires.length() > 1) {
            return wires.length()
        }
        return null
    }

    private fun parseTemplateMap(json: JSONObject?): Map<Int, String> {
        if (json == null) {
            return emptyMap()
        }
        return buildMap {
            for (key in json.keys()) {
                val wireId = key.toIntOrNull() ?: continue
                val path = pcPathToAssetPath(json.optString(key, ""))
                if (path.isNotBlank()) {
                    put(wireId, path)
                }
            }
        }
    }

    private fun parseSwipe(json: JSONObject?): SwipeCoords? {
        if (json == null) {
            return null
        }
        return SwipeCoords(
            x1 = json.optInt("x1"),
            y1 = json.optInt("y1"),
            x2 = json.optInt("x2"),
            y2 = json.optInt("y2"),
            durationMs = json.optLong("duration", 300L),
            maxAttempts = json.optInt("max_attempts", 10),
        )
    }

    fun pcPathToAssetPath(pcPath: String): String {
        if (pcPath.isBlank()) {
            return ""
        }
        return when {
            pcPath.startsWith("templates/mu/") -> pcPath
            pcPath.startsWith("templates/maps/") ->
                pcPath.replaceFirst("templates/maps/", "templates/mu/maps/")
            pcPath.startsWith("templates/ui/") ->
                pcPath.replaceFirst("templates/ui/", "templates/mu/ui/")
            pcPath.startsWith("templates/wires/") ->
                pcPath.replaceFirst("templates/wires/", "templates/mu/wires/")
            else -> pcPath
        }
    }
}
