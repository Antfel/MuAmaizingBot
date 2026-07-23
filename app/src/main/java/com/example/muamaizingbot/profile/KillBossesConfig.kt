package com.example.muamaizingbot.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * Farm Bosses config: ordered list of map ids to hunt.
 * No manual spots/coords — bosses are found via map templates.
 */
data class KillBossesConfig(
    val includeGoldenMobs: Boolean = false,
    /** Soft fight timeout after Focus+Auto before treating kill as done. */
    val holdSec: Int = DEFAULT_HOLD_SEC,
    /** Ordered map definition ids (e.g. plain_of_four_winds_2). */
    val maps: List<String> = emptyList(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("enabled", true)
            put("include_golden_mobs", includeGoldenMobs)
            put("hold_sec", holdSec.coerceIn(MIN_HOLD_SEC, MAX_HOLD_SEC))
            put(
                "maps",
                JSONArray().apply {
                    maps.forEach { put(it) }
                },
            )
            // Legacy key (spots-based MVP) — keep empty for old readers.
            put("spots", JSONArray())
        }
    }

    companion object {
        const val DEFAULT_HOLD_SEC = 90
        const val MIN_HOLD_SEC = 15
        const val MAX_HOLD_SEC = 600

        fun fromJson(json: JSONObject?): KillBossesConfig {
            if (json == null) return KillBossesConfig()
            val mapsArr = json.optJSONArray("maps")
            val maps = buildList {
                if (mapsArr != null) {
                    for (i in 0 until mapsArr.length()) {
                        val id = mapsArr.optString(i).trim()
                        if (id.isNotEmpty()) add(id)
                    }
                }
            }
            // Migrate old spot-based profiles: unique map ids from spots, preserve order.
            val migrated = if (maps.isEmpty()) {
                val spotsArr = json.optJSONArray("spots")
                buildList {
                    if (spotsArr != null) {
                        val seen = linkedSetOf<String>()
                        for (i in 0 until spotsArr.length()) {
                            val item = spotsArr.optJSONObject(i) ?: continue
                            val mapId = item.optString("map").trim()
                            if (mapId.isNotEmpty() && seen.add(mapId)) add(mapId)
                        }
                    }
                }
            } else {
                maps
            }
            return KillBossesConfig(
                includeGoldenMobs = json.optBoolean("include_golden_mobs", false),
                holdSec = json.optInt("hold_sec", DEFAULT_HOLD_SEC)
                    .coerceIn(MIN_HOLD_SEC, MAX_HOLD_SEC),
                maps = migrated,
            )
        }
    }
}
