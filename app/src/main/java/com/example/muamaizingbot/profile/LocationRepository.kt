package com.example.muamaizingbot.profile

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LocationRepository {

    private const val TAG = "LocationRepository"
    private const val LOCATIONS_DIR = "special_locations"
    private const val LOCATIONS_FILE = "user_locations.json"

    private lateinit var locationsFile: File
    private var initialized = false

    private val _farmSpot = MutableStateFlow<FarmLocation?>(null)
    val farmSpot: StateFlow<FarmLocation?> = _farmSpot.asStateFlow()

    private val _elfBuff = MutableStateFlow<FarmLocation?>(null)
    val elfBuff: StateFlow<FarmLocation?> = _elfBuff.asStateFlow()

    private val _warPost = MutableStateFlow<FarmLocation?>(null)
    val warPost: StateFlow<FarmLocation?> = _warPost.asStateFlow()

    fun init(context: Context) {
        if (initialized) {
            return
        }
        val dir = File(context.applicationContext.filesDir, LOCATIONS_DIR).apply { mkdirs() }
        locationsFile = File(dir, LOCATIONS_FILE)
        if (!locationsFile.exists()) {
            saveAll(emptyList())
        }
        refreshForCurrentProfile()
        initialized = true
        Log.d(
            TAG,
            "[LOCATIONS] init farmSpot=${_farmSpot.value?.id} " +
                "elfBuff=${_elfBuff.value?.id} warPost=${_warPost.value?.id}",
        )
    }

    fun refreshForCurrentProfile() {
        if (!initialized) {
            return
        }
        val profile = ProfileRepository.currentProfile.value ?: run {
            _farmSpot.value = null
            _elfBuff.value = null
            _warPost.value = null
            return
        }
        _farmSpot.value = getFarmSpot(profile.filename)
        _elfBuff.value = getElfBuff(profile.filename)
        _warPost.value = getWarPost(profile.filename)
    }

    fun getFarmSpot(profileFilename: String): FarmLocation? {
        return findLocation(profileFilename, "farm_spot")
    }

    fun getElfBuff(profileFilename: String): FarmLocation? {
        return findLocation(profileFilename, "elf_buff")
    }

    fun getWarPost(profileFilename: String): FarmLocation? {
        return findLocation(profileFilename, "war_post")
    }

    fun upsertFarmSpot(
        profileFilename: String,
        mapId: String,
        wire: Int,
        x: Int,
        y: Int,
        name: String = "Farm Spot",
        coordX: Int? = null,
        coordY: Int? = null,
    ): FarmLocation {
        val profile = normalizeProfileName(profileFilename)
        val existing = getFarmSpot(profileFilename)
        val location = FarmLocation(
            id = existing?.id ?: "${profile.removeSuffix(".json")}_farm_spot",
            profile = profile,
            type = "farm_spot",
            name = name,
            map = mapId,
            wire = wire,
            x = x,
            y = y,
            coordX = coordX,
            coordY = coordY,
        )
        upsertLocation(location)
        if (isCurrentProfile(profile)) {
            _farmSpot.value = location
        }
        Log.d(TAG, "[LOCATIONS] saved farm_spot profile=$profile map=$mapId wire=$wire ($x,$y)")
        return location
    }

    fun upsertElfBuff(
        profileFilename: String,
        mapId: String,
        wire: Int,
        x: Int,
        y: Int,
        name: String = "Elf Buff",
        coordX: Int? = null,
        coordY: Int? = null,
    ): FarmLocation {
        val profile = normalizeProfileName(profileFilename)
        val existing = getElfBuff(profileFilename)
        val location = FarmLocation(
            id = existing?.id ?: "${profile.removeSuffix(".json")}_elf_buff",
            profile = profile,
            type = "elf_buff",
            name = name,
            map = mapId,
            wire = wire,
            x = x,
            y = y,
            coordX = coordX,
            coordY = coordY,
        )
        upsertLocation(location)
        if (isCurrentProfile(profile)) {
            _elfBuff.value = location
        }
        Log.d(TAG, "[LOCATIONS] saved elf_buff profile=$profile map=$mapId wire=$wire ($x,$y)")
        return location
    }

    fun upsertWarPost(
        profileFilename: String,
        mapId: String,
        wire: Int,
        x: Int,
        y: Int,
        name: String = "War Post",
        coordX: Int? = null,
        coordY: Int? = null,
    ): FarmLocation {
        val profile = normalizeProfileName(profileFilename)
        val existing = getWarPost(profileFilename)
        val location = FarmLocation(
            id = existing?.id ?: "${profile.removeSuffix(".json")}_war_post",
            profile = profile,
            type = "war_post",
            name = name,
            map = mapId,
            wire = wire,
            x = x,
            y = y,
            coordX = coordX,
            coordY = coordY,
        )
        upsertLocation(location)
        if (isCurrentProfile(profile)) {
            _warPost.value = location
        }
        Log.d(
            TAG,
            "[LOCATIONS] saved war_post profile=$profile map=$mapId " +
                "pixel=($x,$y) coords=(${coordX},${coordY})",
        )
        return location
    }

    fun deleteElfBuff(profileFilename: String) {
        val profile = normalizeProfileName(profileFilename)
        val remaining = loadAll().filterNot {
            normalizeProfileName(it.profile) == profile && it.type == "elf_buff"
        }
        saveAll(remaining)
        if (isCurrentProfile(profile)) {
            _elfBuff.value = null
        }
    }

    fun deleteForProfile(profileFilename: String) {
        val profile = normalizeProfileName(profileFilename)
        val remaining = loadAll().filterNot {
            normalizeProfileName(it.profile) == profile
        }
        saveAll(remaining)
        if (isCurrentProfile(profile)) {
            _farmSpot.value = null
            _elfBuff.value = null
            _warPost.value = null
        }
    }

    private fun findLocation(profileFilename: String, type: String): FarmLocation? {
        val normalized = normalizeProfileName(profileFilename)
        return loadAll().firstOrNull {
            normalizeProfileName(it.profile) == normalized && it.type == type
        }
    }

    private fun upsertLocation(location: FarmLocation) {
        val profile = normalizeProfileName(location.profile)
        val all = loadAll().filterNot {
            normalizeProfileName(it.profile) == profile && it.type == location.type
        } + location
        saveAll(all)
    }

    private fun isCurrentProfile(profileFilename: String): Boolean {
        return ProfileRepository.currentProfile.value?.filename?.let { normalizeProfileName(it) } == profileFilename
    }

    private fun loadAll(): List<FarmLocation> {
        if (!locationsFile.exists()) {
            return emptyList()
        }
        val json = JSONObject(locationsFile.readText())
        val arr = json.optJSONArray("locations") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                runCatching { add(FarmLocation.fromJson(item)) }
            }
        }
    }

    private fun saveAll(locations: List<FarmLocation>) {
        val json = JSONObject().put(
            "locations",
            JSONArray().apply {
                locations.forEach { put(it.toJson()) }
            }
        )
        locationsFile.writeText(json.toString(2))
    }

    private fun normalizeProfileName(profileName: String): String {
        val name = profileName.trim()
        return if (name.endsWith(".json")) name else "$name.json"
    }
}
