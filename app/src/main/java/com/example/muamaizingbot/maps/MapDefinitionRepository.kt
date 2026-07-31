package com.example.muamaizingbot.maps

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Locale

object MapDefinitionRepository {

    private const val TAG = "MapDefinitionRepo"
    private const val MAPS_ROOT = "navigation/maps"

    private val mapsById = linkedMapOf<String, MapDefinition>()
    private var initialized = false

    fun init(context: Context, overlayRoot: File? = null) {
        reload(context, overlayRoot)
    }

    /**
     * Load APK asset maps, then overlay any JSON from [overlayRoot]/navigation/maps
     * (downloaded pack wins by map id).
     */
    fun reload(context: Context, overlayRoot: File? = null) {
        mapsById.clear()
        val assetManager = context.applicationContext.assets
        val files = assetManager.list(MAPS_ROOT).orEmpty()
        for (file in files) {
            if (!file.endsWith(".json")) {
                continue
            }
            val jsonText = assetManager.open("$MAPS_ROOT/$file").bufferedReader().use { it.readText() }
            putFromJson(jsonText, source = "asset:$file")
        }

        val overlayMaps = overlayRoot?.let { File(it, MAPS_ROOT) }
        if (overlayMaps != null && overlayMaps.isDirectory) {
            overlayMaps.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    val jsonText = file.readText()
                    putFromJson(jsonText, source = "overlay:${file.name}")
                }
        }

        initialized = true
        Log.d(TAG, "[MAPS] loaded count=${mapsById.size} overlay=${overlayRoot != null}")
    }

    private fun putFromJson(jsonText: String, source: String) {
        runCatching {
            val mapDef = MapDefinition.fromJson(JSONObject(jsonText))
            mapsById[mapDef.id] = mapDef
        }.onFailure { err ->
            Log.w(TAG, "[MAPS] skip $source: ${err.message}")
        }
    }

    fun getById(mapId: String): MapDefinition? = mapsById[mapId]

    /**
     * Maps ready for Farm Spot / Elf Buff / Farm Bosses / SpotPicker:
     * maintenance image + navigable templates + affine calibration.
     */
    fun listForPicker(): List<MapDefinition> {
        return mapsById.values
            .filter { it.isFullyConfigured() }
            .sortedWith(compareBy({ it.order }, { it.name.lowercase(Locale.getDefault()) }))
    }

    /** Same as [listForPicker] — only fully configured maps are selectable in the APK. */
    fun listForSpotPicker(): List<MapDefinition> = listForPicker()

    fun allMaps(): List<MapDefinition> {
        return mapsById.values.sortedBy { it.order }
    }

    /** Same zone head (e.g. Plains 1/2) — used to disambiguate similar sub-map labels. */
    fun siblingsSharingHead(mapDef: MapDefinition): List<MapDefinition> {
        val head = mapDef.navigation?.mapHeadTemplate?.takeIf { it.isNotBlank() }
            ?: return listOf(mapDef)
        return mapsById.values.filter { it.navigation?.mapHeadTemplate == head }
    }

    fun isInitialized(): Boolean = initialized
}
