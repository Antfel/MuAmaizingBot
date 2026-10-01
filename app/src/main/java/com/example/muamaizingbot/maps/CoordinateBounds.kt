package com.example.muamaizingbot.maps

import org.json.JSONObject

data class CoordinateBounds(
    val xMin: Int,
    val xMax: Int,
    val yMin: Int,
    val yMax: Int,
) {
    fun contains(x: Int, y: Int, margin: Int = 0): Boolean {
        return x in (xMin - margin)..(xMax + margin) &&
            y in (yMin - margin)..(yMax + margin)
    }

    companion object {
        fun fromJson(json: JSONObject?): CoordinateBounds? {
            if (json == null) {
                return null
            }
            return try {
                CoordinateBounds(
                    xMin = json.getInt("x_min"),
                    xMax = json.getInt("x_max"),
                    yMin = json.getInt("y_min"),
                    yMax = json.getInt("y_max"),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
