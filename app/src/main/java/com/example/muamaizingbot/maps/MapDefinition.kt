package com.example.muamaizingbot.maps

import org.json.JSONObject

data class MapMaintenance(
    val mapUiImageAssetPath: String,
    val imageWidth: Int = 2560,
    val imageHeight: Int = 1440,
)

data class AffineTransform(
    val coordX: List<Double>,
    val coordY: List<Double>,
    val sourceWidth: Int = 2560,
    val sourceHeight: Int = 1440,
)

data class MapDefinition(
    val id: String,
    val name: String,
    val group: String,
    val order: Int = 999,
    val wireCount: Int? = null,
    val wireKeys: List<Int> = emptyList(),
    val maintenance: MapMaintenance?,
    val coordinateMapping: AffineTransform?,
    val coordinateBounds: CoordinateBounds? = null,
    val navigation: MapNavigation? = null,
    val wireSwitch: WireSwitchConfig? = null,
) {
    fun availableWires(): List<Int> {
        wireSwitch?.availableWires?.takeIf { it.isNotEmpty() }?.let { return it.sorted() }
        if (wireKeys.isNotEmpty()) {
            return wireKeys.sorted()
        }
        val count = wireCount ?: return listOf(1)
        return (1..count).toList()
    }

    fun hasMaintenanceImage(): Boolean {
        return maintenance?.mapUiImageAssetPath?.isNotBlank() == true
    }

    fun isNavigable(): Boolean = MapNavigationParser.isNavigable(navigation)

    fun supportsWireSwitch(): Boolean {
        val config = wireSwitch ?: return false
        return config.enabled && config.availableWires.size > 1
    }

    companion object {
        fun fromJson(json: JSONObject): MapDefinition {
            val maintenanceJson = json.optJSONObject("maintenance")
            val maintenance = maintenanceJson?.let { maint ->
                val pcPath = maint.optString("map_ui_image", "")
                MapMaintenance(
                    mapUiImageAssetPath = MapNavigationParser.pcPathToAssetPath(pcPath),
                    imageWidth = maint.optInt("image_width", 2560),
                    imageHeight = maint.optInt("image_height", 1440),
                )
            }

            val wiresJson = json.optJSONObject("wires")
            val wireKeys = wiresJson?.keys()?.asSequence()?.mapNotNull { it.toIntOrNull() }?.toList().orEmpty()

            val mappingJson = json.optJSONObject("coordinate_mapping")
            val coordinateMapping = mappingJson?.let { mapping ->
                if (mapping.optString("type") != "affine") {
                    return@let null
                }
                val transform = mapping.optJSONObject("transform") ?: return@let null
                val coordX = transform.optJSONArray("coord_x")?.let { arr ->
                    (0 until arr.length()).map { arr.getDouble(it) }
                } ?: return@let null
                val coordY = transform.optJSONArray("coord_y")?.let { arr ->
                    (0 until arr.length()).map { arr.getDouble(it) }
                } ?: return@let null
                if (coordX.size != 3 || coordY.size != 3) {
                    return@let null
                }
                val source = mapping.optJSONObject("source_image_size")
                AffineTransform(
                    coordX = coordX,
                    coordY = coordY,
                    sourceWidth = source?.optInt("width", 2560) ?: 2560,
                    sourceHeight = source?.optInt("height", 1440) ?: 1440,
                )
            }

            val wireCount = json.optInt("wire").takeIf { json.has("wire") && wireKeys.isEmpty() }

            return MapDefinition(
                id = json.getString("id"),
                name = json.optString("name", json.getString("id")),
                group = json.optString("group", ""),
                order = json.optInt("order", 999),
                wireCount = wireCount,
                wireKeys = wireKeys,
                maintenance = maintenance,
                coordinateMapping = coordinateMapping,
                coordinateBounds = CoordinateBounds.fromJson(json.optJSONObject("coordinate_bounds")),
                navigation = MapNavigationParser.parseNavigation(json),
                wireSwitch = MapNavigationParser.parseWireSwitch(json),
            )
        }

        /** @see MapNavigationParser.pcPathToAssetPath */
        fun pcPathToAssetPath(pcPath: String): String = MapNavigationParser.pcPathToAssetPath(pcPath)
    }
}
