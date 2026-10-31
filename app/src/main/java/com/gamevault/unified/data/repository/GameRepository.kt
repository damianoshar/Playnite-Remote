package com.gamevault.unified.data.repository

import com.gamevault.unified.data.local.CachedLibrary
import com.gamevault.unified.data.local.LibraryCache
import com.gamevault.unified.data.local.MissingArtStore
import com.gamevault.unified.data.local.ServerStorage
import com.gamevault.unified.data.model.AchievementInfo
import com.gamevault.unified.data.model.Achievements
import com.gamevault.unified.data.model.Activity
import com.gamevault.unified.data.model.Game
import com.gamevault.unified.data.model.GameDetails
import com.gamevault.unified.data.model.GameLink
import com.gamevault.unified.data.model.GenreCount
import com.gamevault.unified.data.model.PlaySession
import com.gamevault.unified.data.model.RecentGame
import com.gamevault.unified.data.model.ServerConfig
import com.gamevault.unified.data.model.SourceCount
import com.gamevault.unified.data.model.StatusCount
import com.gamevault.unified.data.model.StatsSnapshot
import com.gamevault.unified.data.remote.BridgeClient
import com.gamevault.unified.data.remote.BridgeGameDto
import com.gamevault.unified.data.remote.GameDetailsDto
import com.gamevault.unified.data.remote.StatsDto
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context

/**
 * Talks to ONE backend: the playnite-bridge plugin on the user's PC.
 * Playnite itself handles all store logins; this repo only maps JSON.
 *
 * Offline strategy: every successful sync is snapshotted to [LibraryCache].
 * The ViewModel renders the snapshot instantly and refreshes in background;
 * if the PC is unreachable, the snapshot (and disk-cached covers) is served.
 */
class GameRepository(
    private val storage: ServerStorage,
    private val cache: LibraryCache,
    private val missingArt: MissingArtStore
) {
    suspend fun config(): ServerConfig? = storage.snapshot()

    fun formatTime(timestamp: Long): String = cache.formatTime(timestamp)

    private suspend fun requireApi() =
        BridgeClient.api(config() ?: throw IllegalStateException("Server not configured"))

    private fun ServerConfig.mapGame(dto: BridgeGameDto) = Game(
        id = dto.id,
        title = dto.name,
        source = dto.source.orEmpty(),
        coverUrl = coverUrl(dto.id, "cover", 512),
        playtimeSeconds = dto.playtime,
        playCount = dto.playCount,
        isInstalled = dto.isInstalled,
        favorite = dto.favorite,
        hidden = dto.hidden,
        completionStatus = dto.completionStatus.orEmpty(),
        genres = dto.genres,
        categories = dto.categories,
        tags = dto.tags,
        features = dto.features,
        platforms = dto.platforms,
        userScore = dto.userScore,
        lastActivity = dto.lastActivity.orEmpty()
    )

    private fun ServerConfig.mapDetails(dto: GameDetailsDto) = GameDetails(
        id = dto.id,
        title = dto.name,
        sortingName = dto.sortingName.orEmpty(),
        source = dto.source.orEmpty(),
        description = dto.description.orEmpty(),
        notes = dto.notes.orEmpty(),
        developers = dto.developers,
        publishers = dto.publishers,
        series = dto.series,
        genres = dto.genres,
        categories = dto.categories,
        tags = dto.tags,
        features = dto.features,
        platforms = dto.platforms,
        ageRatings = dto.ageRatings,
        regions = dto.regions,
        links = dto.links.orEmpty().map { GameLink(it.name, it.url) },
        completionStatus = dto.completionStatus.orEmpty(),
        releaseDate = dto.releaseDate.orEmpty(),
        added = dto.added.orEmpty(),
        modified = dto.modified.orEmpty(),
        coverUrl = coverUrl(dto.id, "cover", 512),
        backgroundUrl = if (dto.hasBackground) coverUrl(dto.id, "background") else "",
        playtimeSeconds = dto.playtime,
        playCount = dto.playCount,
        lastActivity = dto.lastActivity.orEmpty(),
        isInstalled = dto.isInstalled,
        installDirectory = dto.installDirectory.orEmpty(),
        installSize = dto.installSize,
        favorite = dto.favorite,
        hidden = dto.hidden,
        communityScore = dto.communityScore,
        criticScore = dto.criticScore,
        userScore = dto.userScore,
        gameId = dto.gameId.orEmpty(),
        version = dto.version.orEmpty(),
        hasCover = dto.hasCover
    )

    suspend fun artlessIds(): Set<String> = missingArt.snapshot()
    suspend fun markArtless(ids: Collection<String>) = missingArt.add(ids)
    suspend fun unmarkArtless(id: String) = missingArt.remove(id)
    suspend fun replaceArtless(ids: Set<String>) = missingArt.replace(ids)
    suspend fun cachedDetails(id: String): GameDetails? = cache.loadDetails(id)

    private fun mapStats(dto: StatsDto) = StatsSnapshot(
        totalGames = dto.totalGames,
        installed = dto.installed,
        notInstalled = dto.notInstalled,
        hidden = dto.hidden,
        favorite = dto.favorite,
        uncategorized = dto.uncategorized,
        totalPlaytimeHours = dto.totalPlaytimeHours,
        bySource = dto.bySource.map { SourceCount(it.source, it.count) },
        byStatus = dto.byCompletionStatus.map { StatusCount(it.status, it.count) },
        topGenres = dto.topGenres.map { GenreCount(it.genre, it.count) },
        recentlyPlayed = dto.recentlyPlayed.map { RecentGame(it.id, it.name, it.lastPlayed.orEmpty()) }
    )

    /** Cached library with cover URLs rebased onto the CURRENT server
     *  config: a snapshot must never keep firing cover requests at a stale
     *  PC address (e.g. after a DHCP change) — that burst tripped the
     *  image breaker right at connect/startup. */
    suspend fun cached(): CachedLibrary? {
        val lib = cache.loadLibrary() ?: return null
        val cfg = config() ?: return lib
        return lib.copy(games = lib.games.map { it.copy(coverUrl = cfg.coverUrl(it.id, width = 512)) })
    }

    /** Fetches everything from the PC and snapshots it for offline use. */
    suspend fun refreshAndCache(): CachedLibrary {
        val cfg = config() ?: throw IllegalStateException("Server not configured")
        val api = BridgeClient.api(cfg)

        val all = mutableListOf<Game>()
        var offset = 0
        val limit = 2000
        while (true) {
            val page = api.getGames(limit = limit, offset = offset, includeHidden = true)
            all += page.games.map { cfg.mapGame(it) }
            offset += page.games.size
            if (page.games.isEmpty() || offset >= page.total) break
        }
        val sources = api.getSources().map { it.name }.filter { it.isNotBlank() }.sorted()
        val stats = mapStats(api.getStats())

        val data = CachedLibrary(
            savedAt = System.currentTimeMillis(),
            games = all.sortedBy { it.title.lowercase() },
            sources = sources,
            statsSummary = "${stats.totalGames} games • ${stats.installed} installed",
            stats = stats
        )
        cache.saveLibrary(data)
        return data
    }

    /** Network first, cached details as offline fallback. */
    suspend fun details(id: String): GameDetails {
        return try {
            fetchAndCacheDetails(id)
        } catch (e: Exception) {
            cache.loadDetails(id) ?: throw e
        }
    }

    /** Network-only details fetch that updates the cache. Throws on failure. */
    suspend fun fetchAndCacheDetails(id: String): GameDetails {
        val cfg = config() ?: throw IllegalStateException("Server not configured")
        val dto = BridgeClient.api(cfg).getGame(id)
        return cfg.mapDetails(dto).also { cache.saveDetails(it) }
    }

    suspend fun launch(id: String) {
        requireApi().launch(id)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val body = JsonObject().apply { addProperty("favorite", favorite) }
        val res = requireApi().updateGame(id, body)
        if (!res.isSuccessful) throw IllegalStateException("Update failed: HTTP ${res.code()}")
    }

    /** SuccessStory achievements; null when unavailable (plugin/data missing or offline). */
    suspend fun achievements(id: String): Achievements? {
        return try {
            val dto = requireApi().getAchievements(id)
            if (!dto.installed) return Achievements(available = false)
            Achievements(
                available = true,
                total = dto.total,
                unlocked = dto.unlocked,
                locked = dto.locked,
                items = dto.achievements.map {
                    AchievementInfo(
                        name = it.name.orEmpty(),
                        description = it.description.orEmpty(),
                        unlocked = it.unlocked,
                        dateUnlocked = it.dateUnlocked.orEmpty(),
                        percent = it.percent
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    /** GameActivity sessions; null when unavailable. */
    suspend fun activity(id: String): Activity? {
        return try {
            val dto = requireApi().getActivity(id)
            if (!dto.installed) return Activity(available = false)
            Activity(
                available = true,
                totalSeconds = dto.totalSeconds,
                totalHours = dto.totalHours,
                sessionCount = dto.sessionCount,
                sessions = dto.sessions.map {
                    PlaySession(
                        date = it.date.orEmpty(),
                        elapsedSeconds = it.elapsedSeconds,
                        action = it.action.orEmpty()
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Returns the Playnite version string on success, throws otherwise. */
    suspend fun testConnection(cfg: ServerConfig): String {
        val info = BridgeClient.api(cfg).getAppInfo()
        return info.version?.takeIf { it.isNotBlank() } ?: "OK"
    }

    /**
     * Wipes all offline data: snapshot, cached details, downloaded covers,
     * artless set. Server connection is kept. Returns bytes freed.
     */
    suspend fun clearOfflineData(appContext: Context): Long = withContext(Dispatchers.IO) {
        var freed = cache.clearAll()
        missingArt.replace(emptySet())
        val coverDir = appContext.cacheDir.resolve("cover_cache")
        if (coverDir.exists()) {
            freed += coverDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            coverDir.deleteRecursively()
        }
        BridgeClient.clearMemoryCaches()
        freed
    }
}
