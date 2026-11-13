package com.gamevault.unified.data.local

import java.io.File

/**
 * File layout of the offline cache.
 *
 * Shared by [LibraryCache] and the home-screen widget so both agree on where
 * a given server profile's snapshot lives.
 */
object CacheLayout {

    const val ROOT_DIR = "library_cache"

    /** Legacy (pre-multi-server) snapshot path. */
    const val LEGACY_LIBRARY_FILE = "library_cache/library.json"

    fun scopeDirName(scope: String): String =
        "scope_" + scope.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun libraryFile(root: File, scope: String): File =
        File(File(root, scopeDirName(scope)), "library.json")

    fun detailsDir(root: File, scope: String): File =
        File(File(root, scopeDirName(scope)), "details")
}
