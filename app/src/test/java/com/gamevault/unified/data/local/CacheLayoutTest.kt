package com.gamevault.unified.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** File layout of the offline cache — shared by the cache and the widget. */
class CacheLayoutTest {

    @Test
    fun `scope directories are namespaced`() {
        assertEquals("scope_p1", CacheLayout.scopeDirName("p1"))
    }

    @Test
    fun `scope directories sanitise unsafe characters`() {
        val name = CacheLayout.scopeDirName("a/b c:d")
        assertTrue(!name.contains('/'))
        assertTrue(!name.contains(' '))
        assertTrue(!name.contains(':'))
    }

    @Test
    fun `legacy path points at the old flat file`() {
        assertEquals("library_cache/library.json", CacheLayout.LEGACY_LIBRARY_FILE)
    }

    @Test
    fun `library file lives inside the scope directory`() {
        val root = java.io.File("/tmp/cache")
        val file = CacheLayout.libraryFile(root, "p1")
        assertEquals("library.json", file.name)
        assertEquals("scope_p1", file.parentFile.name)
    }

    @Test
    fun `details directory lives inside the scope directory`() {
        val root = java.io.File("/tmp/cache")
        val dir = CacheLayout.detailsDir(root, "p1")
        assertEquals("details", dir.name)
        assertEquals("scope_p1", dir.parentFile.name)
    }
}
