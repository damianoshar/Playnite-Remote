package com.gamevault.unified.data.local

import android.content.Context
import com.gamevault.unified.data.model.Game
import com.gamevault.unified.data.model.GameDetails
import com.gamevault.unified.data.model.StatsSnapshot
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Snapshot of the last successful sync, stored in internal files. */
data class CachedLibrary(
    val savedAt: Long = 0,
    val games: List<Game> = emptyList(),
    val sources: List<String> = emptyList(),
    val statsSummary: String = "",
    val stats: StatsSnapshot? = null
)

/**
 * File-based offline cache. No database needed: a full library is a few MB
 * of JSON, and game details are cached lazily when opened.
 *
 * The cache is scoped per server profile ([scope]) so switching between PCs
 * never shows one library's data under another server's covers.
 */
class LibraryCache(context: Context) {

    private val gson = Gson()
    private val dir = File(context.filesDir, CacheLayout.ROOT_DIR).apply { mkdirs() }

    /** Legacy (pre-multi-server) location, kept so upgrades do not lose data. */
    private val legacyLibraryFile = File(context.filesDir, CacheLayout.LEGACY_LIBRARY_FILE)
    private val legacyDetailsDir = File(dir, "details")

    private fun scopeDir(scope: String): File =
        File(dir, CacheLayout.scopeDirName(scope)).apply { mkdirs() }

    private fun libraryFile(scope: String) = CacheLayout.libraryFile(dir, scope)
    private fun detailsDir(scope: String) = CacheLayout.detailsDir(dir, scope).apply { mkdirs() }

    suspend fun saveLibrary(scope: String, data: CachedLibrary) = withContext(Dispatchers.IO) {
        libraryFile(scope).writeText(gson.toJson(data))
    }

    suspend fun loadLibrary(scope: String): CachedLibrary? = withContext(Dispatchers.IO) {
        val file = libraryFile(scope)
        if (file.exists()) {
            try {
                return@withContext gson.fromJson(file.readText(), CachedLibrary::class.java)
            } catch (e: Exception) {
                return@withContext null
            }
        }
        // Migration: the first scope to ask for data inherits the old
        // single-server cache (and moves it, so it is read only once).
        if (legacyLibraryFile.exists()) {
            try {
                val migrated = gson.fromJson(legacyLibraryFile.readText(), CachedLibrary::class.java)
                legacyLibraryFile.renameTo(libraryFile(scope))
                return@withContext migrated
            } catch (e: Exception) {
                return@withContext null
            }
        }
        null
    }

    suspend fun saveDetails(scope: String, details: GameDetails) = withContext(Dispatchers.IO) {
        try {
            File(detailsDir(scope), safeName(details.id) + ".json").writeText(gson.toJson(details))
        } catch (e: Exception) {
            // Cache is best-effort; network data was already delivered.
        }
    }

    suspend fun loadDetails(scope: String, id: String): GameDetails? = withContext(Dispatchers.IO) {
        val file = File(detailsDir(scope), safeName(id) + ".json")
        if (!file.exists()) {
            // Legacy details live in the old flat folder.
            val legacy = File(legacyDetailsDir, safeName(id) + ".json")
            if (!legacy.exists()) return@withContext null
            return@withContext try {
                gson.fromJson(legacy.readText(), GameDetails::class.java)
            } catch (e: Exception) {
                null
            }
        }
        try {
            gson.fromJson(file.readText(), GameDetails::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun formatTime(timestamp: Long): String =
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

    /** Deletes the snapshot + all cached details for one scope. Returns bytes freed. */
    suspend fun clearAll(scope: String): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        val file = libraryFile(scope)
        if (file.exists()) {
            freed += file.length()
            file.delete()
        }
        detailsDir(scope).listFiles()?.forEach { f ->
            if (f.isFile) {
                freed += f.length()
                f.delete()
            }
        }
        freed
    }

    /** Wipes every scope (all servers). Returns bytes freed. */
    suspend fun clearEverything(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        dir.listFiles()?.forEach { f ->
            if (f.isFile) {
                freed += f.length()
                f.delete()
            } else if (f.isDirectory) {
                freed += f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                f.deleteRecursively()
            }
        }
        freed
    }

    private fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
