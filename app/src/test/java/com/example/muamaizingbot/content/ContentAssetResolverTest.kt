package com.example.muamaizingbot.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class ContentAssetResolverTest {

    @Test
    fun prefersOverlayFileWhenBound() {
        val root = createTempDirectory("resolver-overlay").toFile()
        try {
            val store = ContentPackStore(root)
            val rel = "navigation/maps/overlay.json"
            val file = store.stagingFileFor(rel)
            file.writeText("""{"id":"overlay"}""")
            val sha = ContentPackStore.sha256Hex(file)
            val manifest = MapContentManifest(
                packVersion = 1,
                schemaVersion = 1,
                files = listOf(MapContentFileEntry(rel, sha, file.length())),
            )
            store.writeStagingManifest(manifest)
            assertTrue(store.commitStaging(manifest))

            ContentAssetResolver.bindOverlayForTests(store.currentRoot())
            val text = ContentAssetResolver.open(rel)?.bufferedReader()?.use { it.readText() }
            assertEquals("""{"id":"overlay"}""", text)
            assertNull(ContentAssetResolver.open("navigation/maps/missing.json"))
        } finally {
            ContentAssetResolver.bindOverlayForTests(null)
            root.deleteRecursively()
        }
    }
}
