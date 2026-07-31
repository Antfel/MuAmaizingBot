package com.example.muamaizingbot.content

import android.content.Context
import android.util.Log
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.vision.template.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class MapContentStatus(
    val localPackVersion: Int = 0,
    val lastSyncMessage: String? = null,
    val lastSyncOk: Boolean? = null,
    val syncing: Boolean = false,
)

/**
 * Incremental map-pack sync against license-server content endpoints.
 */
object MapContentSync {

    private const val TAG = "MapContentSync"

    private lateinit var appContext: Context
    private lateinit var store: ContentPackStore
    private val syncing = AtomicBoolean(false)

    private val _status = MutableStateFlow(MapContentStatus())
    val status: StateFlow<MapContentStatus> = _status.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        store = ContentPackStore.fromContext(appContext)
        ContentAssetResolver.init(appContext, store)
        _status.value = MapContentStatus(localPackVersion = store.localPackVersion())
        // Apply any previously downloaded overlay on cold start.
        applyOverlayLoaders()
    }

    fun packStore(): ContentPackStore = store

    /**
     * Sync remote pack. Safe to call from IO. No-ops if another sync is running.
     */
    fun sync(
        baseUrl: String,
        licenseKey: String,
        sessionId: String?,
        schemaVersion: Int = MAP_CONTENT_SCHEMA_VERSION,
    ): MapContentSyncResult {
        if (licenseKey.isBlank() || baseUrl.isBlank()) {
            return MapContentSyncResult.Skipped("missing_license_or_url")
        }
        if (!syncing.compareAndSet(false, true)) {
            return MapContentSyncResult.Skipped("already_syncing")
        }
        _status.value = _status.value.copy(syncing = true)
        return try {
            Log.i(TAG, "[CONTENT] sync start schema=$schemaVersion")
            when (val remote = MapContentApiClient.fetchManifest(baseUrl, licenseKey, sessionId, schemaVersion)) {
                is MapContentApiResult.Failed -> {
                    fail(remote.code, remote.message)
                }
                is MapContentApiResult.Ok -> {
                    val manifest = remote.value
                    if (MapContentSyncPolicy.shouldSkipUnsupportedSchema(
                            manifest.schemaVersion,
                            schemaVersion,
                        )
                    ) {
                        val msg = "schema_unsupported remote=${manifest.schemaVersion} local=$schemaVersion"
                        Log.w(TAG, "[CONTENT] $msg")
                        _status.value = _status.value.copy(
                            syncing = false,
                            lastSyncOk = true,
                            lastSyncMessage = msg,
                            localPackVersion = store.localPackVersion(),
                        )
                        return MapContentSyncResult.Skipped(msg)
                    }
                    if (manifest.packVersion <= 0 || manifest.files.isEmpty()) {
                        return fail("EMPTY_PACK", "Remote pack is empty")
                    }
                    val local = store.readLocalManifest()
                    if (MapContentSyncPolicy.isSamePack(local, manifest)) {
                        Log.i(TAG, "[CONTENT] sync ok up_to_date version=${manifest.packVersion}")
                        _status.value = _status.value.copy(
                            syncing = false,
                            lastSyncOk = true,
                            lastSyncMessage = "up_to_date v${manifest.packVersion}",
                            localPackVersion = manifest.packVersion,
                        )
                        return MapContentSyncResult.UpToDate(manifest.packVersion)
                    }

                    store.prepareStagingFromCurrent()
                    store.writeStagingManifest(manifest)
                    var downloaded = 0
                    for (entry in MapContentSyncPolicy.filesNeedingDownload(
                        manifest,
                        manifest.files.associate { file ->
                            file.path to (
                                store.sha256OfCurrentOrStaging(file.path, preferStaging = true)
                                    ?: ""
                                )
                        }.filterValues { it.isNotBlank() },
                    )) {
                        val dest = store.stagingFileFor(entry.path)
                        when (
                            val dl = MapContentApiClient.downloadFile(
                                baseUrl,
                                licenseKey,
                                sessionId,
                                entry.path,
                                dest,
                            )
                        ) {
                            is MapContentApiResult.Failed -> {
                                store.discardStaging()
                                return fail(dl.code, "download ${entry.path}: ${dl.message}")
                            }
                            is MapContentApiResult.Ok -> downloaded++
                        }
                    }

                    if (!store.commitStaging(manifest)) {
                        store.discardStaging()
                        return fail("VERIFY_FAILED", "Staging pack failed verification")
                    }

                    ContentAssetResolver.refreshOverlay(store)
                    applyOverlayLoaders()
                    Log.i(
                        TAG,
                        "[CONTENT] sync ok version=${manifest.packVersion} downloaded=$downloaded",
                    )
                    _status.value = _status.value.copy(
                        syncing = false,
                        lastSyncOk = true,
                        lastSyncMessage = "updated v${manifest.packVersion} (+$downloaded)",
                        localPackVersion = manifest.packVersion,
                    )
                    MapContentSyncResult.Updated(manifest.packVersion, downloaded)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "[CONTENT] sync fail: ${t.message}", t)
            store.discardStaging()
            fail("ERROR", t.message ?: "sync error")
        } finally {
            syncing.set(false)
            if (_status.value.syncing) {
                _status.value = _status.value.copy(syncing = false)
            }
        }
    }

    private fun fail(code: String, message: String): MapContentSyncResult.Failed {
        Log.w(TAG, "[CONTENT] sync fail code=$code msg=$message")
        _status.value = _status.value.copy(
            syncing = false,
            lastSyncOk = false,
            lastSyncMessage = "$code: $message",
            localPackVersion = store.localPackVersion(),
        )
        return MapContentSyncResult.Failed(code, message)
    }

    private fun applyOverlayLoaders() {
        if (!::appContext.isInitialized) return
        val overlay = store.currentRoot()
        MapDefinitionRepository.reload(appContext, overlay)
        TemplateRepository.reload(overlayRoot = overlay)
    }
}
