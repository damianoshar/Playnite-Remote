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
 */
class LibraryCache(context: Context) {

    private val gson = Gson()
    private val dir = File(context.filesDir, "library_cache").apply { mkdirs() }
    private val libraryFile = File(dir, "library.json")
    private val detailsDir = File(dir, "details").apply { mkdirs() }

    suspend fun saveLibrary(data: CachedLibrary) = withContext(Dispatchers.IO) {
        libraryFile.writeText(gson.toJson(data))
    }

    suspend fun loadLibrary(): CachedLibrary? = withContext(Dispatchers.IO) {
        if (!libraryFile.exists()) return@withContext null
        try {
            gson.fromJson(libraryFile.readText(), CachedLibrary::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveDetails(details: GameDetails) = withContext(Dispatchers.IO) {
        try {
            File(detailsDir, safeName(details.id) + ".json").writeText(gson.toJson(details))
        } catch (e: Exception) {
            // Cache is best-effort; network data was already delivered.
        }
    }

    suspend fun loadDetails(id: String): GameDetails? = withContext(Dispatchers.IO) {
        val file = File(detailsDir, safeName(id) + ".json")
        if (!file.exists()) return@withContext null
        try {
            gson.fromJson(file.readText(), GameDetails::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun formatTime(timestamp: Long): String =
        SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

    /** Deletes the snapshot + all cached details. Returns bytes freed. */
    suspend fun clearAll(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        if (libraryFile.exists()) {
            freed += libraryFile.length()
            libraryFile.delete()
        }
        detailsDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                freed += file.length()
                file.delete()
            }
        }
        freed
    }

    private fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
