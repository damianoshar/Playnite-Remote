package com.gamevault.unified.viewmodel

import com.gamevault.unified.data.local.ViewMode
import com.gamevault.unified.data.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Client-side filter/sort/pagination logic lives in [LibraryUiState] as pure
 * functions over the synced snapshot, so it is testable without Android.
 */
class LibraryUiStateFilterTest {

    private fun game(
        id: String,
        title: String,
        source: String = "Steam",
        playtime: Long = 0,
        playCount: Long = 0,
        installed: Boolean = false,
        favorite: Boolean = false,
        hidden: Boolean = false,
        status: String = "Played",
        genres: List<String> = emptyList(),
        categories: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        features: List<String> = emptyList(),
        platforms: List<String> = emptyList(),
        score: Int? = null,
        lastActivity: String = ""
    ) = Game(
        id = id,
        title = title,
        source = source,
        playtimeSeconds = playtime,
        playCount = playCount,
        isInstalled = installed,
        favorite = favorite,
        hidden = hidden,
        completionStatus = status,
        genres = genres,
        categories = categories,
        tags = tags,
        features = features,
        platforms = platforms,
        userScore = score,
        lastActivity = lastActivity
    )

    private fun state(
        games: List<Game>,
        filters: GameFilters = GameFilters(),
        collections: Map<String, Set<String>> = emptyMap(),
        pageSize: Int = 0,
        page: Int = 0
    ) = LibraryUiState(
        games = games,
        filters = filters,
        collections = collections,
        pageSize = pageSize,
        page = page
    )

    private val library = listOf(
        game("1", "Alpha", source = "Steam", playtime = 7200, installed = true, favorite = true, genres = listOf("RPG"), categories = listOf("Fav"), tags = listOf("Co-op"), platforms = listOf("PC"), score = 9),
        game("2", "Beta", source = "GOG", playtime = 60, installed = false, genres = listOf("Action"), categories = emptyList(), score = 5),
        game("3", "Gamma", source = "Epic", playtime = 0, installed = true, genres = listOf("RPG", "Action"), score = null),
        game("4", "Hidden One", source = "Steam", hidden = true, score = 10)
    )

    @Test
    fun `hidden games are excluded unless requested`() {
        assertEquals(3, state(library).filteredGames.size)
        assertEquals(4, state(library, GameFilters(showHidden = true)).filteredGames.size)
    }

    @Test
    fun `source facet filters by exact source`() {
        val result = state(library, GameFilters(selectedSources = setOf("Steam"))).filteredGames
        assertEquals(listOf("Alpha"), result.map { it.title })
    }

    @Test
    fun `genre facet matches any listed genre`() {
        val result = state(library, GameFilters(selectedGenres = setOf("RPG"))).filteredGames
        assertEquals(listOf("Alpha", "Gamma"), result.map { it.title })
    }

    @Test
    fun `multiple values in one facet are ORed`() {
        val result = state(library, GameFilters(selectedGenres = setOf("RPG", "Action"))).filteredGames
        assertEquals(listOf("Alpha", "Beta", "Gamma"), result.map { it.title })
    }

    @Test
    fun `different facets are ANDed`() {
        val result = state(
            library,
            GameFilters(selectedSources = setOf("Steam"), selectedGenres = setOf("RPG"))
        ).filteredGames
        assertEquals(listOf("Alpha"), result.map { it.title })
    }

    @Test
    fun `installed only filter`() {
        val result = state(library, GameFilters(installedOnly = true)).filteredGames
        assertEquals(listOf("Alpha", "Gamma"), result.map { it.title })
    }

    @Test
    fun `favorite only filter`() {
        val result = state(library, GameFilters(favoriteOnly = true)).filteredGames
        assertEquals(listOf("Alpha"), result.map { it.title })
    }

    @Test
    fun `uncategorized only filter`() {
        val result = state(library, GameFilters(uncategorizedOnly = true)).filteredGames
        assertEquals(listOf("Beta", "Gamma"), result.map { it.title })
    }

    @Test
    fun `unplayed only filter uses zero playtime`() {
        val result = state(library, GameFilters(unplayedOnly = true)).filteredGames
        assertEquals(listOf("Gamma"), result.map { it.title })
    }

    @Test
    fun `playtime bounds are in hours`() {
        val result = state(library, GameFilters(minHours = 1)).filteredGames
        assertEquals(listOf("Alpha"), result.map { it.title })
    }

    @Test
    fun `score range treats missing score as lowest`() {
        val result = state(library, GameFilters(minScore = 6)).filteredGames
        assertEquals(listOf("Alpha"), result.map { it.title })
    }

    @Test
    fun `query searches title case insensitively`() {
        val result = state(library, GameFilters(query = "alp")).filteredGames
        assertEquals(listOf("Alpha"), result.map { it.title })
    }

    @Test
    fun `collection filter keeps only member ids`() {
        val result = state(
            library,
            GameFilters(selectedCollection = "Weekend"),
            collections = mapOf("Weekend" to setOf("2", "3"))
        ).filteredGames
        assertEquals(listOf("Beta", "Gamma"), result.map { it.title })
    }

    @Test
    fun `unknown collection does not hide everything`() {
        val result = state(
            library,
            GameFilters(selectedCollection = "Missing"),
            collections = mapOf("Weekend" to setOf("2"))
        ).filteredGames
        assertEquals(3, result.size)
    }

    @Test
    fun `sort by name is case insensitive`() {
        val result = state(library, GameFilters(sort = SortMode.NAME)).filteredGames
        assertEquals(listOf("Alpha", "Beta", "Gamma"), result.map { it.title })
    }

    @Test
    fun `sort by playtime descending`() {
        val result = state(library, GameFilters(sort = SortMode.PLAYTIME)).filteredGames
        assertEquals(listOf("Alpha", "Beta", "Gamma"), result.map { it.title })
    }

    @Test
    fun `sort by play count descending`() {
        val games = listOf(
            game("a", "A", playCount = 1),
            game("b", "B", playCount = 9)
        )
        val result = state(games, GameFilters(sort = SortMode.PLAY_COUNT)).filteredGames
        assertEquals(listOf("B", "A"), result.map { it.title })
    }

    @Test
    fun `sort by install size descending with unknown sizes last`() {
        val games = listOf(
            Game(id = "a", title = "A", installSize = 1024L),
            Game(id = "b", title = "B", installSize = null),
            Game(id = "c", title = "C", installSize = 4096L)
        )
        val result = state(games, GameFilters(sort = SortMode.INSTALL_SIZE)).filteredGames
        assertEquals(listOf("C", "A", "B"), result.map { it.title })
    }

    @Test
    fun `sort by last played compares instants not strings`() {
        val games = listOf(
            Game(id = "a", title = "A", lastActivity = "2026-03-04T10:00:00.0000000+00:00"),
            Game(id = "b", title = "B", lastActivity = "2026-03-04T12:00:00.0000000+02:00"),
            Game(id = "c", title = "C", lastActivity = "2026-01-01T00:00:00.0000000+00:00")
        )
        // A and B are the same instant; C is the oldest.
        val result = state(games, GameFilters(sort = SortMode.LAST_PLAYED)).filteredGames
        assertEquals("C", result.last().title)
        assertTrue(result.indexOfFirst { it.title == "A" } < result.indexOfFirst { it.title == "B" })
    }

    @Test
    fun `sort by score puts missing scores last`() {
        val result = state(library, GameFilters(sort = SortMode.SCORE)).filteredGames
        assertEquals(listOf("Alpha", "Beta", "Gamma"), result.map { it.title })
    }

    @Test
    fun `active count counts every enabled facet`() {
        val filters = GameFilters(
            selectedSources = setOf("Steam", "GOG"),
            selectedGenres = setOf("RPG"),
            installedOnly = true,
            minScore = 3
        )
        assertEquals(5, filters.activeCount)
    }

    @Test
    fun `pagination splits the filtered list into pages`() {
        val s = state(library, pageSize = 2)
        assertEquals(2, s.totalPages)
        assertEquals(listOf("Alpha", "Beta"), s.visibleGames.map { it.title })
    }

    @Test
    fun `visible games follow the current page`() {
        val s = state(library, pageSize = 2, page = 1)
        assertEquals(listOf("Gamma"), s.visibleGames.map { it.title })
    }

    @Test
    fun `page size zero shows everything`() {
        val s = state(library, pageSize = 0)
        assertEquals(1, s.totalPages)
        assertEquals(3, s.visibleGames.size)
    }

    @Test
    fun `safe page clamps out of range values`() {
        val s = state(library, pageSize = 2, page = 99)
        assertEquals(1, s.safePage)
        assertTrue(s.visibleGames.isNotEmpty())
    }

    @Test
    fun `facet options are derived from the snapshot`() {
        val s = state(library)
        assertEquals(listOf("Action", "RPG"), s.genreOptions)
        assertEquals(listOf("Fav"), s.categoryOptions)
        assertEquals(listOf("Co-op"), s.tagOptions)
        assertEquals(listOf("PC"), s.platformOptions)
        assertEquals(listOf("Played"), s.statusOptions)
    }

    @Test
    fun `view mode default is list`() {
        assertEquals(ViewMode.LIST, LibraryUiState().viewMode)
    }
}
