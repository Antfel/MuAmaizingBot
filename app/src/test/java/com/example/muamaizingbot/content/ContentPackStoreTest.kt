package com.example.muamaizingbot.content

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ContentPackStoreTest {

    @Test
    fun sanitizeRejectsTraversal() {
        try {
            ContentPackStore.sanitizeRelativePath("../etc/passwd")
            fail("expected rejection")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun commitStagingSwapsAtomicallyAndKeepsCurrentOnBadHash() {
        val root = createTempDirectory("content-pack-test").toFile()
        try {
            val store = ContentPackStore(root)
            val good = MapContentManifest(
                packVersion = 1,
                schemaVersion = 1,
                files = listOf(
                    MapContentFileEntry(
                        path = "navigation/maps/demo.json",
                        sha256 = writeStagingFile(store, "navigation/maps/demo.json", """{"id":"demo"}"""),
                        size = 1,
                    ),
                ),
            )
            store.writeStagingManifest(good)
            assertTrue(store.commitStaging(good))
            assertEquals(1, store.localPackVersion())
            assertTrue(File(store.currentRoot(), "navigation/maps/demo.json").isFile)

            // Prepare a bad staging update: wrong hash → current stays v1
            store.prepareStagingFromCurrent()
            writeStagingFile(store, "navigation/maps/demo.json", """{"id":"demo2"}""")
            val bad = MapContentManifest(
                packVersion = 2,
                schemaVersion = 1,
                files = listOf(
                    MapContentFileEntry(
                        path = "navigation/maps/demo.json",
                        sha256 = "0".repeat(64),
                        size = 1,
                    ),
                ),
            )
            assertFalse(store.commitStaging(bad))
            assertEquals(1, store.localPackVersion())
            assertEquals("""{"id":"demo"}""", File(store.currentRoot(), "navigation/maps/demo.json").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun committedOverlayIsReadableFromCurrentRoot() {
        val root = createTempDirectory("content-resolver-test").toFile()
        try {
            val store = ContentPackStore(root)
            val sha = writeStagingFile(
                store,
                "templates/mu/maps/Demo/maintenance/demo.png",
                "png-bytes",
            )
            val manifest = MapContentManifest(
                packVersion = 3,
                schemaVersion = 1,
                files = listOf(
                    MapContentFileEntry(
                        path = "templates/mu/maps/Demo/maintenance/demo.png",
                        sha256 = sha,
                        size = 9,
                    ),
                ),
            )
            store.writeStagingManifest(manifest)
            assertTrue(store.commitStaging(manifest))
            val overlay = store.currentRoot()
            assertTrue(overlay != null && overlay.isDirectory)
            val disk = File(overlay, "templates/mu/maps/Demo/maintenance/demo.png")
            assertTrue(disk.isFile)
            assertEquals("png-bytes", disk.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeStagingFile(store: ContentPackStore, path: String, contents: String): String {
        store.stagingRoot()
        val file = store.stagingFileFor(path)
        file.writeText(contents)
        return ContentPackStore.sha256Hex(file)
    }
}

class MapContentSyncPolicyTest {

    @Test
    fun skipsWhenRemoteSchemaIsNewer() {
        assertTrue(MapContentSyncPolicy.shouldSkipUnsupportedSchema(2, 1))
        assertFalse(MapContentSyncPolicy.shouldSkipUnsupportedSchema(1, 1))
        assertFalse(MapContentSyncPolicy.shouldSkipUnsupportedSchema(1, 2))
    }

    @Test
    fun detectsSamePackAndDeltaFiles() {
        val file = MapContentFileEntry("navigation/maps/a.json", "abc", 10)
        val remote = MapContentManifest(1, 1, files = listOf(file))
        assertTrue(MapContentSyncPolicy.isSamePack(remote, remote))
        assertFalse(MapContentSyncPolicy.isSamePack(null, remote))

        val need = MapContentSyncPolicy.filesNeedingDownload(
            remote,
            mapOf("navigation/maps/a.json" to "abc"),
        )
        assertTrue(need.isEmpty())

        val need2 = MapContentSyncPolicy.filesNeedingDownload(
            remote,
            mapOf("navigation/maps/a.json" to "zzz"),
        )
        assertEquals(1, need2.size)
    }

    @Test
    fun parsesManifestJson() {
        val json = JSONObject(
            """
            {
              "pack_version": 4,
              "schema_version": 1,
              "files": [
                {"path":"/navigation/maps/x.json","sha256":"Aa","size":3}
              ]
            }
            """.trimIndent(),
        )
        val m = MapContentManifest.fromJson(json)
        assertEquals(4, m.packVersion)
        assertEquals("navigation/maps/x.json", m.files.single().path)
        assertEquals("aa", m.files.single().sha256)
    }
}
