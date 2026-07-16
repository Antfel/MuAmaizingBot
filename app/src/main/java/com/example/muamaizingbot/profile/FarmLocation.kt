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
        }
    }

    fun summaryLabel(mapName: String? = null): String {
        val mapLabel = mapName ?: map
        val coordPart = if (coordX != null && coordY != null) " ($coordX,$coordY)" else ""
        return "$mapLabel W$wire @ ($x,$y)$coordPart"
    }

    companion object {
        fun fromJson(json: JSONObject): FarmLocation {
            return FarmLocation(
                id = json.getString("id"),
                profile = json.getString("profile"),
                type = json.optString("type", "farm_spot"),
                name = json.optString("name", "Farm Spot"),
                map = json.getString("map"),
                wire = json.getInt("wire"),
                x = json.getInt("x"),
                y = json.getInt("y"),
                coordX = json.optInt("coord_x").takeIf { json.has("coord_x") && !json.isNull("coord_x") },
                coordY = json.optInt("coord_y").takeIf { json.has("coord_y") && !json.isNull("coord_y") },
                arrivalRadius = json.optInt("arrival_radius", 5),
                farmRadius = json.optInt("farm_radius", 5).let { saved ->
                    // Migrate old default (20) down to the intended farm radius.
                    if (saved == 20) 5 else saved
                },
                lostRadius = json.optInt("lost_radius", 35),
            )
        }
    }
}
