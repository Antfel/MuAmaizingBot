package com.example.muamaizingbot.maps

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Locale

object MapDefinitionRepository {

    private const val TAG = "MapDefinitionRepo"
    private const val MAPS_ROOT = "navigation/maps"

    private val mapsById = linkedMapOf<String, MapDefinition>()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) {
            return
        }
        val assetManager = context.applicationContext.assets
        val files = assetManager.list(MAPS_ROOT).orEmpty()
        for (file in files) {
            if (!file.endsWith(".json")) {
                continue
            }
            val jsonText = assetManager.open("$MAPS_ROOT/$file").bufferedReader().use { it.readText() }
            val mapDef = MapDefinition.fromJson(JSONObject(jsonText))
            mapsById[mapDef.id] = mapDef
        }
        initialized = true
        Log.d(TAG, "[MAPS] loaded count=${mapsById.size}")
    }

    fun getById(mapId: String): MapDefinition? = mapsById[mapId]

    /**
     * Maps ready for profile Farm Spot / Elf Buff: maintenance image, affine calibration,
     * and navigable templates (option + current/modal).
     */
    fun listForPicker(): List<MapDefinition> {
        return mapsById.values
            .filter {
                it.hasMaintenanceImage() &&
                    CoordinateMapping.hasMapping(it) &&
                    it.isNavigable()
            }
            .sortedWith(compareBy({ it.order }, { it.name.lowercase(Locale.getDefault()) }))
    }

    fun allMaps(): List<MapDefinition> {
        return mapsById.values.sortedBy { it.order }
    }

    /** Same zone head (e.g. Plains 1/2) — used to disambiguate similar sub-map labels. */
    fun siblingsSharingHead(mapDef: MapDefinition): List<MapDefinition> {
        val head = mapDef.navigation?.mapHeadTemplate?.takeIf { it.isNotBlank() }
            ?: return listOf(mapDef)
        return mapsById.values.filter { it.navigation?.mapHeadTemplate == head }
    }
}
