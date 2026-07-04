package com.example.muamaizingbot.profile

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.Locale

object ProfileRepository {

    private const val TAG = "ProfileRepository"
    private const val PROFILES_DIR = "profiles"
    private const val CURRENT_FILE = "current.json"
    private const val POINTER_FILE = ".current_profile_name"

    private lateinit var profilesDir: File
    private var initialized = false

    private val _profiles = MutableStateFlow<List<BotProfile>>(emptyList())
    val profiles: StateFlow<List<BotProfile>> = _profiles.asStateFlow()

    private val _currentProfile = MutableStateFlow<BotProfile?>(null)
    val currentProfile: StateFlow<BotProfile?> = _currentProfile.asStateFlow()

    fun init(context: Context) {
        if (initialized) {
            return
        }
        profilesDir = File(context.applicationContext.filesDir, PROFILES_DIR).apply { mkdirs() }
        ensureDefaultProfile()
        refresh()
        initialized = true
        Log.d(TAG, "[PROFILE] init count=${_profiles.value.size} current=${_currentProfile.value?.filename}")
    }

    fun refresh() {
        val loaded = profilesDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") && it.name != CURRENT_FILE }
            ?.mapNotNull { file -> loadProfileFile(file) }
            ?.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
            .orEmpty()

        _profiles.value = loaded

        val currentName = readCurrentPointer()
        val current = when {
            currentName != null -> loaded.firstOrNull { it.filename == currentName }
            loaded.size == 1 -> loaded.first()
            else -> loaded.firstOrNull()
        }
        if (current != null) {
            setCurrentProfile(current.filename, persistCopy = false)
        } else {
            _currentProfile.value = null
        }
    }

    fun setCurrentProfile(filename: String, persistCopy: Boolean = true) {
        val profile = _profiles.value.firstOrNull { it.filename == filename }
            ?: loadProfileFile(File(profilesDir, filename))
            ?: return

        if (persistCopy) {
            val source = File(profilesDir, filename)
            val current = File(profilesDir, CURRENT_FILE)
            source.copyTo(current, overwrite = true)
            File(profilesDir, POINTER_FILE).writeText(filename)
        }
        _currentProfile.value = profile
        LocationRepository.refreshForCurrentProfile()
        Log.d(TAG, "[PROFILE] current=${profile.displayName} file=$filename")
    }

    fun saveProfile(profile: BotProfile) {
        val file = File(profilesDir, profile.filename)
        file.writeText(profile.toJson().toString(2))
        refresh()
        if (_currentProfile.value?.filename == profile.filename) {
            setCurrentProfile(profile.filename)
        }
    }

    fun createProfile(displayName: String): BotProfile {
        val stem = slugify(displayName)
        var filename = "$stem.json"
        var index = 2
        while (File(profilesDir, filename).exists()) {
            filename = "${stem}_$index.json"
            index++
        }
        val profile = BotProfile.defaultNew(filename, displayName.trim())
        saveProfile(profile)
        setCurrentProfile(filename)
        return profile
    }

    fun deleteProfile(filename: String) {
        File(profilesDir, filename).delete()
        if (readCurrentPointer() == filename) {
            File(profilesDir, CURRENT_FILE).delete()
            File(profilesDir, POINTER_FILE).delete()
        }
        LocationRepository.deleteForProfile(filename)
        refresh()
    }

    fun getProfile(filename: String): BotProfile? {
        return _profiles.value.firstOrNull { it.filename == filename }
            ?: loadProfileFile(File(profilesDir, filename))
    }

    fun updateProfileMapWire(profile: BotProfile, mapId: String, wire: Int) {
        saveProfile(profile.copy(map = mapId, wire = wire))
    }

    fun duplicateProfile(sourceFilename: String): BotProfile? {
        val source = getProfile(sourceFilename) ?: return null
        var displayName = "${source.displayName} (copia)"
        var stem = slugify(displayName)
        var filename = "$stem.json"
        var index = 2
        while (File(profilesDir, filename).exists()) {
            displayName = "${source.displayName} (copia $index)"
            stem = slugify(displayName)
            filename = "$stem.json"
            index++
        }

        val copy = source.copy(filename = filename, displayName = displayName)
        saveProfile(copy)

        LocationRepository.getFarmSpot(sourceFilename)?.let { spot ->
            LocationRepository.upsertFarmSpot(
                profileFilename = filename,
                mapId = spot.map,
                wire = spot.wire,
                x = spot.x,
                y = spot.y,
                name = spot.name,
                coordX = spot.coordX,
                coordY = spot.coordY,
            )
        }
        LocationRepository.getElfBuff(sourceFilename)?.let { elf ->
            LocationRepository.upsertElfBuff(
                profileFilename = filename,
                mapId = elf.map,
                wire = elf.wire,
                x = elf.x,
                y = elf.y,
                name = elf.name,
                coordX = elf.coordX,
                coordY = elf.coordY,
            )
        }

        setCurrentProfile(filename)
        Log.d(TAG, "[PROFILE] duplicated ${source.filename} -> $filename")
        return copy
    }

    private fun ensureDefaultProfile() {
        val existing = profilesDir.listFiles()
            ?.any { it.isFile && it.name.endsWith(".json") && it.name != CURRENT_FILE }
            ?: false
        if (existing) {
            return
        }
        val default = BotProfile.defaultNew("default.json", "Perfil 1")
        saveProfile(default)
        setCurrentProfile(default.filename)
    }

    private fun readCurrentPointer(): String? {
        val pointer = File(profilesDir, POINTER_FILE)
        if (!pointer.exists()) {
            return null
        }
        return pointer.readText().trim().takeIf { it.isNotEmpty() }
    }

    private fun loadProfileFile(file: File): BotProfile? {
        return runCatching {
            BotProfile.fromJson(file.name, JSONObject(file.readText()))
        }.getOrNull()
    }

    private fun slugify(displayName: String): String {
        val normalized = displayName.trim().lowercase(Locale.getDefault())
        val slug = normalized.replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return slug.ifEmpty { "perfil" }
    }
}
