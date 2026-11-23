package com.gamevault.unified.data.local

import com.gamevault.unified.data.model.Game
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Snapshot cache: per-profile folders, first save, isolation, migration. */
class LibraryCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun cache(): LibraryCache = LibraryCache(folder.root)

    /** Where the cache stores a scope's snapshot (library_cache/scope_x/…). */
    private fun libraryFile(scope: String) =
        CacheLayout.libraryFile(File(folder.root, CacheLayout.ROOT_DIR), scope)

    private fun library(title: String = "Hades") = CachedLibrary(
        savedAt = 1_700_000_000_000L,
        games = listOf(Game(id = "g1", title = title)),
        sources = listOf("Steam"),
        statsSummary = "1 games • 1 installed"
    )

    @Test
    fun `the first save creates the profile folder`() = runBlocking {
        val cache = cache()
        val file = libraryFile("p1")
        assertTrue("precondition: no snapshot yet", !file.exists())

        cache.saveLibrary("p1", library())

        assertTrue(file.exists())
    }

    @Test
    fun `a saved library loads back`() = runBlocking {
        val cache = cache()
        cache.saveLibrary("p1", library())
        val loaded = cache.loadLibrary("p1")
        assertEquals(listOf("Hades"), loaded?.games?.map { it.title })
        assertEquals(listOf("Steam"), loaded?.sources)
    }

    @Test
    fun `scopes do not see each other's libraries`() = runBlocking {
        val cache = cache()
        cache.saveLibrary("p1", library("From the first PC"))
        cache.saveLibrary("p2", library("From the second PC"))

        assertEquals(listOf("From the first PC"), cache.loadLibrary("p1")?.games?.map { it.title })
        assertEquals(listOf("From the second PC"), cache.loadLibrary("p2")?.games?.map { it.title })
    }

    @Test
    fun `an unknown scope has no snapshot`() = runBlocking {
        assertNull(cache().loadLibrary("never-saved"))
    }

    @Test
    fun `a corrupt snapshot is treated as missing`() = runBlocking {
        val cache = cache()
        cache.saveLibrary("p1", library())
        libraryFile("p1").writeText("{ not json")
        assertNull(cache.loadLibrary("p1"))
    }

    @Test
    fun `a legacy snapshot is migrated into the first scope`() = runBlocking {
        val legacy = File(folder.root, CacheLayout.LEGACY_LIBRARY_FILE)
        legacy.parentFile?.mkdirs()
        legacy.writeText(
            com.google.gson.Gson().toJson(CachedLibrary(games = listOf(Game(id = "old", title = "Old PC game"))))
        )

        val loaded = cache().loadLibrary("p1")

        assertEquals(listOf("Old PC game"), loaded?.games?.map { it.title })
        assertTrue("legacy file moved", !legacy.exists())
        assertTrue(libraryFile("p1").exists())
    }

    @Test
    fun `clearing a scope frees its snapshot and details`() = runBlocking {
        val cache = cache()
        cache.saveLibrary("p1", library())
        cache.saveDetails("p1", com.gamevault.unified.data.model.GameDetails(id = "g1", title = "Hades"))

        val freed = cache.clearAll("p1")

        assertTrue(freed > 0)
        assertNull(cache.loadLibrary("p1"))
    }

    @Test
    fun `clearing everything removes every scope`() = runBlocking {
        val cache = cache()
        cache.saveLibrary("p1", library())
        cache.saveLibrary("p2", library())

        cache.clearEverything()

        assertNull(cache.loadLibrary("p1"))
        assertNull(cache.loadLibrary("p2"))
    }
}
