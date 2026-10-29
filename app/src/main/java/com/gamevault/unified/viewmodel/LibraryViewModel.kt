package com.gamevault.unified.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamevault.unified.data.local.ServerStorage
import com.gamevault.unified.data.local.UiPrefs
import com.gamevault.unified.data.local.ViewMode
import com.gamevault.unified.data.local.CachedLibrary
import com.gamevault.unified.data.model.Achievements
import com.gamevault.unified.data.model.Activity
import com.gamevault.unified.data.model.Game
import com.gamevault.unified.data.model.GameDetails
import com.gamevault.unified.data.model.ServerConfig
import com.gamevault.unified.data.model.StatsSnapshot
import com.gamevault.unified.data.repository.GameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortMode { NAME, PLAYTIME, LAST_PLAYED, SCORE, SOURCE, PLAY_COUNT }

/** Progress of a full-offline download (game info + covers). */
data class OfflineProgress(
    val phase: String = "",
    val done: Int = 0,
    val total: Int = 1
)

/** Every filter the bridge API supports (skill.md: GET /api/games + POST /api/games/query).
 *  Facets are multi-select sets (OR within a facet, AND across facets). */
data class GameFilters(
    val query: String = "",
    val selectedSources: Set<String> = emptySet(),
    val selectedGenres: Set<String> = emptySet(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val selectedFeatures: Set<String> = emptySet(),
    val selectedPlatforms: Set<String> = emptySet(),
    val selectedStatuses: Set<String> = emptySet(),
    val installedOnly: Boolean = false,
    val favoriteOnly: Boolean = false,
    val showHidden: Boolean = false,
    val uncategorizedOnly: Boolean = false,
    val unplayedOnly: Boolean = false,
    val minHours: Int? = null,
    val maxHours: Int? = null,
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val sort: SortMode = SortMode.NAME
) {
    val activeCount: Int
        get() = selectedSources.size + selectedGenres.size + selectedCategories.size +
            selectedTags.size + selectedFeatures.size + selectedPlatforms.size +
            selectedStatuses.size + listOf(
                installedOnly, favoriteOnly, showHidden,
                uncategorizedOnly, unplayedOnly,
                minHours != null, maxHours != null,
                minScore != null, maxScore != null
            ).count { it }
}

data class LibraryUiState(
    val isLoading: Boolean = false,
    val games: List<Game> = emptyList(),
    val sources: List<String> = emptyList(),
    val filters: GameFilters = GameFilters(),
    val server: ServerConfig? = null,
    val statsSummary: String = "",
    val stats: StatsSnapshot? = null,
    val viewMode: ViewMode = ViewMode.LIST,
    val gridSpan: Int = 3,
    val pageSize: Int = 0, // 0 = All
    val page: Int = 0,
    val retainImages: Boolean = false,
    val warmingCovers: Boolean = false,
    val appThemeName: String = "DEFAULT",
    val isOffline: Boolean = false,
    val lastSynced: String = "",
    val error: String? = null,
    val connectionTest: String? = null,
    val detailsLoading: Boolean = false,
    val selectedDetails: GameDetails? = null,
    val selectedAchievements: Achievements? = null,
    val selectedActivity: Activity? = null,
    val favoriteBusy: Boolean = false,
    val fullOffline: Boolean = false,
    val lastFullSync: String = "",
    val lastDeleted: String = "",
    val artlessIds: Set<String> = emptySet(),
    val coverFailures: List<Long> = emptyList(),
    val imagesPausedUntil: Long = 0,
    val offlineProgress: OfflineProgress? = null,
    val launchMessage: String? = null
) {
    /** All games matching the filters (before pagination). */
    val filteredGames: List<Game> get() {
        val f = filters
        var list = games.asSequence()
        if (!f.showHidden) list = list.filter { !it.hidden }
        if (f.selectedSources.isNotEmpty()) list = list.filter { it.source in f.selectedSources }
        if (f.selectedGenres.isNotEmpty()) list = list.filter { it.genres.any { g -> g in f.selectedGenres } }
        if (f.selectedCategories.isNotEmpty()) list = list.filter { it.categories.any { c -> c in f.selectedCategories } }
        if (f.selectedTags.isNotEmpty()) list = list.filter { it.tags.any { t -> t in f.selectedTags } }
        if (f.selectedFeatures.isNotEmpty()) list = list.filter { it.features.any { a -> a in f.selectedFeatures } }
        if (f.selectedPlatforms.isNotEmpty()) list = list.filter { it.platforms.any { p -> p in f.selectedPlatforms } }
        if (f.selectedStatuses.isNotEmpty()) list = list.filter { it.completionStatus in f.selectedStatuses }
        if (f.installedOnly) list = list.filter { it.isInstalled }
        if (f.favoriteOnly) list = list.filter { it.favorite }
        if (f.uncategorizedOnly) list = list.filter { it.categories.isEmpty() }
        if (f.unplayedOnly) list = list.filter { it.playtimeSeconds == 0L }
        if (f.minHours != null) list = list.filter { it.playtimeSeconds >= f.minHours * 3600L }
        if (f.maxHours != null) list = list.filter { it.playtimeSeconds <= f.maxHours * 3600L }
        if (f.minScore != null) list = list.filter { (it.userScore ?: -1) >= f.minScore }
        if (f.maxScore != null) list = list.filter { (it.userScore ?: -1) <= f.maxScore }
        if (f.query.isNotBlank()) list = list.filter { it.title.contains(f.query, ignoreCase = true) }
        val sorted = when (f.sort) {
            SortMode.NAME -> list.sortedBy { it.title.lowercase() }
            SortMode.PLAYTIME -> list.sortedByDescending { it.playtimeSeconds }
            SortMode.LAST_PLAYED -> list.sortedByDescending { it.lastActivity }
            SortMode.SCORE -> list.sortedByDescending { it.userScore ?: -1 }
            SortMode.PLAY_COUNT -> list.sortedByDescending { it.playCount }
            SortMode.SOURCE -> list.sortedWith(compareBy({ it.source }, { it.title.lowercase() }))
        }
        return sorted.toList()
    }

    val totalPages: Int get() {
        if (pageSize <= 0) return 1
        val count = filteredGames.size
        if (count == 0) return 1
        return (count + pageSize - 1) / pageSize
    }

    val safePage: Int get() = page.coerceIn(0, maxOf(0, totalPages - 1))

    /** The slice actually shown (one page, or everything when pageSize is All). */
    val visibleGames: List<Game> get() {
        val all = filteredGames
        if (pageSize <= 0) return all
        val from = (safePage * pageSize).coerceAtMost(all.size)
        val to = (from + pageSize).coerceAtMost(all.size)
        return if (from >= to) emptyList() else all.subList(from, to)
    }

    // Facet values derived from the snapshot — works offline.
    val genreOptions: List<String> get() = games.flatMap { it.genres }.distinct().sorted()
    val categoryOptions: List<String> get() = games.flatMap { it.categories }.distinct().sorted()
    val tagOptions: List<String> get() = games.flatMap { it.tags }.distinct().sorted()
    val featureOptions: List<String> get() = games.flatMap { it.features }.distinct().sorted()
    val platformOptions: List<String> get() = games.flatMap { it.platforms }.distinct().sorted()
    val statusOptions: List<String> get() = games.map { it.completionStatus }.filter { it.isNotBlank() }.distinct().sorted()
}

class LibraryViewModel(
    private val repository: GameRepository,
    private val storage: ServerStorage,
    private val uiPrefs: UiPrefs
) : ViewModel() {

    private companion object {
        /** Loose breaker (bridge 1.6.0 hardened the server): 24 failures in
         *  120 s to trip, 5 s pause, auto-resumed afterwards. */
        const val COVER_FAIL_WINDOW_MS = 120_000L
        const val COVER_FAIL_TRIP_COUNT = 24
        const val COVER_PAUSE_MS = 5_000L
    }

    private val _ui = MutableStateFlow(LibraryUiState())
    val ui: StateFlow<LibraryUiState> = _ui.asStateFlow()
    private var refreshJob: Job? = null
    private var fullSyncJob: Job? = null
    private var warmJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = uiPrefs.snapshot()
            _ui.update {
                it.copy(
                    viewMode = prefs.viewMode,
                    gridSpan = prefs.gridSpan,
                    pageSize = prefs.pageSize,
                    retainImages = prefs.retainImages,
                    appThemeName = prefs.appThemeName,
                    artlessIds = repository.artlessIds(),
                    fullOffline = prefs.fullOffline,
                    lastFullSync = if (prefs.lastFullSync > 0)
                        repository.formatTime(prefs.lastFullSync) else ""
                )
            }
            // Show the offline snapshot instantly, then refresh in background.
            val cfg = storage.snapshot()
            val cached = repository.cached()
            if (cached != null) {
                _ui.update {
                    it.copy(
                        server = cfg,
                        games = cached.games,
                        sources = cached.sources,
                        statsSummary = cached.statsSummary,
                        stats = cached.stats,
                        lastSynced = repository.formatTime(cached.savedAt),
                        isOffline = cfg == null
                    )
                }
            } else {
                _ui.update { it.copy(server = cfg) }
            }
            if (cfg != null) refreshLibrary()
        }
    }

    fun updateFilters(f: GameFilters) = _ui.update { it.copy(filters = f, page = 0) }
    fun clearFilters() = _ui.update { it.copy(filters = GameFilters(query = it.filters.query, sort = it.filters.sort), page = 0) }
    fun clearError() = _ui.update { it.copy(error = null, launchMessage = null) }

    fun setPageSize(size: Int) {
        _ui.update { it.copy(pageSize = size.coerceIn(0, 500), page = 0) }
        viewModelScope.launch { uiPrefs.savePageSize(size.coerceIn(0, 500)) }
    }

    fun setPage(p: Int) {
        _ui.update { it.copy(page = p.coerceIn(0, maxOf(0, it.totalPages - 1))) }
    }

    fun setViewMode(mode: ViewMode) {
        _ui.update { it.copy(viewMode = mode) }
        viewModelScope.launch { uiPrefs.saveViewMode(mode) }
    }

    fun setGridSpan(span: Int) {
        val coerced = span.coerceIn(2, 5)
        _ui.update { it.copy(gridSpan = coerced) }
        viewModelScope.launch { uiPrefs.saveGridSpan(coerced) }
    }

    fun setAppTheme(name: String) {
        _ui.update { it.copy(appThemeName = name) }
        viewModelScope.launch { uiPrefs.saveAppTheme(name) }
    }

    fun setRetainImages(enabled: Boolean) {        _ui.update { it.copy(retainImages = enabled) }
        viewModelScope.launch { uiPrefs.saveRetainImages(enabled) }
        if (!enabled) {
            warmJob?.cancel()
            _ui.update { it.copy(warmingCovers = false) }
        }
    }

    /**
     * Pulls every cover of the current filtered list into the on-device disk
     * cache, so scrolling back never touches the network. Artless games are
     * skipped; failures are ignored (covers load on demand instead).
     */
    fun warmCovers(appContext: android.content.Context) {
        if (warmJob?.isActive == true) return
        warmJob = viewModelScope.launch {
            val cfg = repository.config() ?: return@launch
            val st = _ui.value
            val urls = st.filteredGames
                .filter { it.id !in st.artlessIds }
                .map { it.coverUrl }
                .filter { it.isNotBlank() }
                .distinct()
            if (urls.isEmpty()) return@launch
            _ui.update { it.copy(warmingCovers = true) }
            try {
                com.gamevault.unified.data.remote.BridgeClient.downloadCovers(
                    appContext.applicationContext, cfg, urls
                ) { _, _ -> }
            } catch (e: Exception) {
                // Best effort: anything missed loads on demand.
            }
            _ui.update { it.copy(warmingCovers = false) }
        }
    }

    fun refreshLibrary() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val hadGames = _ui.value.games.isNotEmpty()
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                applyRefreshed(repository.refreshAndCache())
                _ui.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        // With a snapshot on screen, a failed refresh becomes
                        // an offline banner instead of an error popup.
                        isOffline = hadGames || it.games.isNotEmpty(),
                        error = if (hadGames || it.games.isNotEmpty()) null else friendlyError(e)
                    )
                }
            }
        }
    }

    private suspend fun applyRefreshed(data: CachedLibrary) {
        _ui.update {
            it.copy(
                games = data.games,
                sources = data.sources,
                statsSummary = data.statsSummary,
                stats = data.stats,
                lastSynced = repository.formatTime(data.savedAt),
                lastDeleted = "",
                isOffline = false,
                page = 0,
                server = repository.config()
            )
        }
    }

    fun setFullOffline(enabled: Boolean) {
        _ui.update { it.copy(fullOffline = enabled) }
        viewModelScope.launch { uiPrefs.saveFullOffline(enabled) }
    }

    /**
     * Full offline download: every game's info, then every cover into the
     * disk cache. Sequential and resumable-safe (each game cached on success;
     * re-running only refills gaps). Cancel keeps partial progress.
     */
    fun startFullOfflineSync(appContext: android.content.Context) {
        if (fullSyncJob?.isActive == true) return
        fullSyncJob = viewModelScope.launch {
            val cfg = repository.config()
            if (cfg == null) {
                _ui.update { it.copy(error = "No PC server configured.") }
                return@launch
            }
            var games = _ui.value.games
            if (games.isEmpty()) {
                _ui.update { it.copy(offlineProgress = OfflineProgress("Syncing library…", 0, 1)) }
                try {
                    val data = repository.refreshAndCache()
                    applyRefreshed(data)
                    games = data.games
                } catch (e: Exception) {
                    _ui.update { it.copy(offlineProgress = null, error = friendlyError(e)) }
                    return@launch
                }
            }
            val total = games.size
            var done = 0
            val oldArtless = repository.artlessIds()
            val scannedOk = mutableSetOf<String>()
            val newArtless = mutableSetOf<String>()
            _ui.update { it.copy(offlineProgress = OfflineProgress("Game info", 0, total)) }
            for (game in games) {
                try {
                    val fresh = repository.fetchAndCacheDetails(game.id)
                    scannedOk += game.id
                    if (!fresh.hasCover) newArtless += game.id
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One bad game must not abort the other 1,499.
                }
                // Pace the bulk download so the PC-side plugin is never
                // hammered with back-to-back requests.
                delay(75)
                done++
                if (done % 20 == 0 || done == total) {
                    val current = done
                    _ui.update { it.copy(offlineProgress = OfflineProgress("Game info", current, total)) }
                }
            }
            // Keep old entries for games this run failed to scan (their
            // state is unknown), replace the rest with fresh knowledge.
            repository.replaceArtless((oldArtless - scannedOk) + newArtless)
            _ui.update { it.copy(artlessIds = repository.artlessIds()) }
            _ui.update { it.copy(offlineProgress = OfflineProgress("Covers", 0, total)) }
            val failedCovers = com.gamevault.unified.data.remote.BridgeClient.downloadCovers(
                appContext.applicationContext,
                cfg,
                games.filter { it.id !in newArtless }.map { it.coverUrl }
            ) { done, count ->
                _ui.update { it.copy(offlineProgress = OfflineProgress("Covers", done, count)) }
            }
            if (failedCovers >= 20) tripBreaker()
            val now = System.currentTimeMillis()
            uiPrefs.saveLastFullSync(now)
            _ui.update {
                it.copy(
                    offlineProgress = null,
                    fullOffline = true,
                    lastFullSync = repository.formatTime(now),
                    launchMessage = if (failedCovers == 0) "Offline download complete"
                    else "Offline download complete ($failedCovers covers failed)"
                )
            }
        }
    }

    fun cancelFullOfflineSync() {
        fullSyncJob?.cancel()
        _ui.update { it.copy(offlineProgress = null) }
    }

    /** Wipes snapshot, details, covers and artless set. Server stays connected. */
    fun clearOfflineData(appContext: android.content.Context) {
        viewModelScope.launch {
            cancelFullOfflineSync()
            val freed = repository.clearOfflineData(appContext.applicationContext)
            uiPrefs.saveFullOffline(false)
            uiPrefs.saveLastFullSync(0)
            val mb = freed / (1024.0 * 1024.0)
            _ui.update {
                it.copy(
                    games = emptyList(),
                    sources = emptyList(),
                    statsSummary = "",
                    stats = null,
                    lastSynced = "",
                    lastFullSync = "",
                    lastDeleted = if (mb >= 1) "Deleted %.0f MB of offline data.".format(mb)
                    else "Offline data deleted.",
                    fullOffline = false,
                    artlessIds = emptySet(),
                    filters = GameFilters(),
                    page = 0,
                    selectedDetails = null,
                    selectedAchievements = null,
                    selectedActivity = null
                )
            }
        }
    }

    fun saveServer(baseUrl: String, token: String) {
        viewModelScope.launch {
            _ui.update { it.copy(connectionTest = "Testing…", error = null) }
            val cfg = ServerConfig(baseUrl.trim(), token.trim())
            try {
                val version = repository.testConnection(cfg)
                storage.save(cfg)
                // Fresh session: don't carry breaker state from the previous
                // connection — fails from an old/absent server must not
                // briefly pause images right after connecting.
                _ui.update {
                    it.copy(
                        server = cfg,
                        connectionTest = "Connected (Playnite $version)",
                        coverFailures = emptyList(),
                        imagesPausedUntil = 0
                    )
                }
                refreshLibrary()
            } catch (e: Exception) {
                _ui.update { it.copy(connectionTest = null, error = friendlyError(e)) }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            storage.clear()
            refreshJob?.cancel()
            // Keep the cached library visible; it is still browsable offline.
            _ui.update {
                it.copy(server = null, connectionTest = null, isOffline = it.games.isNotEmpty())
            }
        }
    }

    fun openDetails(game: Game) {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    detailsLoading = true, selectedDetails = null,
                    selectedAchievements = null, selectedActivity = null
                )
            }
            try {
                val details = async {
                    try {
                        // Fresh fetch first: hasCover is only in full details,
                        // and it teaches us whether /cover will ever succeed.
                        val fresh = repository.fetchAndCacheDetails(game.id)
                        if (fresh.hasCover) repository.unmarkArtless(fresh.id)
                        else repository.markArtless(setOf(fresh.id))
                        _ui.update { state ->
                            state.copy(
                                artlessIds = if (fresh.hasCover)
                                    state.artlessIds - fresh.id
                                else
                                    state.artlessIds + fresh.id
                            )
                        }
                        fresh
                    } catch (e: Exception) {
                        repository.cachedDetails(game.id) ?: throw e
                    }
                }
                val achievements = async { repository.achievements(game.id) }
                val activity = async { repository.activity(game.id) }
                _ui.update {
                    it.copy(
                        detailsLoading = false,
                        selectedDetails = details.await(),
                        selectedAchievements = achievements.await(),
                        selectedActivity = activity.await()
                    )
                }
            } catch (e: Exception) {
                val msg = if (_ui.value.isOffline)
                    "Details not cached — connect to the PC to view this game."
                else friendlyError(e)
                _ui.update { it.copy(detailsLoading = false, error = msg) }
            }
        }
    }

    /** Favorite toggle from a list row (no details dialog). */
    fun toggleFavorite(game: Game) {
        val currently = game.favorite
        _ui.update { state ->
            state.copy(games = state.games.map {
                if (it.id == game.id) it.copy(favorite = !currently) else it
            })
        }
        viewModelScope.launch {
            try {
                repository.setFavorite(game.id, !currently)
            } catch (e: Exception) {
                _ui.update { state ->
                    state.copy(
                        games = state.games.map {
                            if (it.id == game.id) it.copy(favorite = currently) else it
                        },
                        error = friendlyError(e)
                    )
                }
            }
        }
    }

    fun closeDetails() = _ui.update {
        it.copy(
            selectedDetails = null, detailsLoading = false,
            selectedAchievements = null, selectedActivity = null
        )
    }

    fun toggleFavorite() {
        val details = _ui.value.selectedDetails ?: return
        viewModelScope.launch {
            _ui.update { it.copy(favoriteBusy = true) }
            try {
                repository.setFavorite(details.id, !details.favorite)
                val updated = details.copy(favorite = !details.favorite)
                _ui.update { state ->
                    state.copy(
                        favoriteBusy = false,
                        selectedDetails = updated,
                        games = state.games.map {
                            if (it.id == updated.id) it.copy(favorite = updated.favorite) else it
                        }
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(favoriteBusy = false, error = friendlyError(e)) }
            }
        }
    }

    /**
     * Cover result tracking with a circuit breaker (deliberately loose since
     * bridge 1.6.0 hardened the server side):
     * - success: clears the failure streak (PC healthy).
     * - 404: PC has no art -> artless set (never requested again).
     * - 401/403: config problem (bad token), NOT a struggling PC -> ignored.
     * - anything else (timeout/refused/reset): 24 such failures in 120 s
     *   pauses image loading for 30 s, so a truly unresponsive PC gets
     *   silence instead of a storm. A busy PC (import running etc.) never
     *   holds enough sustained failures to trip this.
     */
    fun noteCoverResult(id: String, success: Boolean, httpCode: Int?) {
        if (success) {
            if (_ui.value.coverFailures.isNotEmpty()) {
                _ui.update { it.copy(coverFailures = emptyList()) }
            }
            return
        }
        if (httpCode == 404) {
            if (id in _ui.value.artlessIds) return
            _ui.update { it.copy(artlessIds = it.artlessIds + id) }
            viewModelScope.launch { repository.markArtless(setOf(id)) }
            return
        }
        if (httpCode == 401 || httpCode == 403) return
        // Offline already has its own banner; an unreachable PC must not
        // also trip the "PC struggling" breaker on every scroll.
        if (_ui.value.isOffline) return
        val now = System.currentTimeMillis()
        val recent =
            (_ui.value.coverFailures + now).filter { it > now - COVER_FAIL_WINDOW_MS }
        if (recent.size >= COVER_FAIL_TRIP_COUNT) {
            tripBreaker()
        } else {
            _ui.update { it.copy(coverFailures = recent) }
        }
    }

    private fun tripBreaker() {
        val until = System.currentTimeMillis() + COVER_PAUSE_MS
        _ui.update {
            it.copy(
                coverFailures = emptyList(),
                imagesPausedUntil = until
            )
        }
        // Auto-resume: making the pause expiry a state change is what
        // retriggers image loading in the list ("Retrying shortly").
        viewModelScope.launch {
            kotlinx.coroutines.delay(COVER_PAUSE_MS + 300)
            if (_ui.value.imagesPausedUntil == until) {
                _ui.update { it.copy(imagesPausedUntil = 0) }
            }
        }
    }

    fun imagesPaused(): Boolean = System.currentTimeMillis() < _ui.value.imagesPausedUntil

    fun launchOnPc(id: String) {        viewModelScope.launch {
            try {
                repository.launch(id)
                _ui.update { it.copy(launchMessage = "Launch sent to PC") }
            } catch (e: Exception) {
                _ui.update { it.copy(error = friendlyError(e)) }
            }
        }
    }

    private fun friendlyError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Server not configured", ignoreCase = true) ->
                "No PC server configured."
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Cannot reach PC. Check the IP/hostname, port 19821, and that the PC firewall allows it."
            msg.contains("Failed to connect", ignoreCase = true) || msg.contains("ConnectException") ->
                "Connection refused. Is Playnite running with the bridge plugin on port 19821?"
            msg.contains("401", ignoreCase = true) || msg.contains("Unauthorized", ignoreCase = true) ->
                "Unauthorized (401). The token is wrong — copy it again from Playnite settings."
            msg.contains("Cleartext", ignoreCase = true) ->
                "Cleartext HTTP blocked. Use an http:// LAN address (allowed) or set up HTTPS."
            else -> msg.ifBlank { "Unknown error" }.take(300)
        }
    }
}

class LibraryViewModelFactory(
    private val repository: GameRepository,
    private val storage: ServerStorage,
    private val uiPrefs: UiPrefs
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LibraryViewModel(repository, storage, uiPrefs) as T
    }
}
