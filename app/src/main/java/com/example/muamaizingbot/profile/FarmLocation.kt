package com.example.muamaizingbot.profile

import org.json.JSONObject

data class FarmLocation(
    val id: String,
    val profile: String,
    val type: String = "farm_spot",
    val name: String,
    val map: String,
    val wire: Int,
    val x: Int,
    val y: Int,
    val coordX: Int? = null,
    val coordY: Int? = null,
    val arrivalRadius: Int = 5,
    val farmRadius: Int = 5,
    val lostRadius: Int = 35,
    /**
     * PK Union template variant for this spot.
     * `true` → UnionKuaFu (cross-server); `false` → Union (local).
     * Defaults to the map's [com.example.muamaizingbot.maps.MapDefinition.isCross] when omitted in JSON.
     */
    val isCross: Boolean = true,
    /** Pixel space for [x]/[y]: always [COORD_REF_2560] after load (UI REF). */
    val coordRefVersion: Int = COORD_REF_2560,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("profile", profile)
            put("type", type)
            put("name", name)
            put("map", map)
            put("wire", wire)
            put("x", x)
            put("y", y)
            coordX?.let { put("coord_x", it) }
            coordY?.let { put("coord_y", it) }
            put("arrival_radius", arrivalRadius)
            put("farm_radius", farmRadius)
            put("lost_radius", lostRadius)
            put("is_cross", isCross)
            put("coord_ref_version", COORD_REF_2560)
        }
    }

    fun summaryLabel(mapName: String? = null): String {
        val mapLabel = mapName ?: map
        val coordPart = if (coordX != null && coordY != null) " ($coordX,$coordY)" else ""
        val unionPart = if (isCross) " Cross" else " Local"
        return "$mapLabel W$wire @ ($x,$y)$coordPart$unionPart"
    }

    companion object {
        const val COORD_REF_2560 = 2560
        const val COORD_REF_1280 = 1280

        fun fromJson(json: JSONObject): FarmLocation {
            var x = json.getInt("x")
            var y = json.getInt("y")
            val storedVersion = json.optInt("coord_ref_version", 0).takeIf {
                json.has("coord_ref_version")
            }
            // Previous APK tagged native-1280 spots (and halved 2560 leftovers) as 1280.
            // Convert those back to UI REF 2560 once. Untagged spots stay as authored.
            val from1280 = storedVersion == COORD_REF_1280
            if (from1280) {
                x *= 2
                y *= 2
            }
            return FarmLocation(
                id = json.getString("id"),
                profile = json.getString("profile"),
                type = json.optString("type", "farm_spot"),
                name = json.optString("name", "Farm Spot"),
                map = json.getString("map"),
                wire = json.getInt("wire"),
                x = x,
                y = y,
                coordX = json.optInt("coord_x").takeIf { json.has("coord_x") && !json.isNull("coord_x") },
                coordY = json.optInt("coord_y").takeIf { json.has("coord_y") && !json.isNull("coord_y") },
                arrivalRadius = json.optInt("arrival_radius", 5),
                farmRadius = json.optInt("farm_radius", 5).let { saved ->
                    // Migrate old default (20) down to the intended farm radius.
                    if (saved == 20) 5 else saved
                },
                lostRadius = json.optInt("lost_radius", 35),
                isCross = when {
                    json.has("is_cross") -> json.optBoolean("is_cross", true)
                    else -> {
                        // Legacy spots: inherit map default when available.
                        runCatching {
                            com.example.muamaizingbot.maps.MapDefinitionRepository
                                .getById(json.getString("map"))
                                ?.isCross
                        }.getOrNull() ?: true
                    }
                },
                coordRefVersion = COORD_REF_2560,
            )
        }
    }
}
