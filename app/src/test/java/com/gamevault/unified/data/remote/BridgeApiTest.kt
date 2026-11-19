package com.gamevault.unified.data.remote

import com.gamevault.unified.data.model.ServerConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/**
 * API parsing against a local mock server — the shape of the bridge
 * responses is checked without needing a PC.
 */
class BridgeApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BridgeApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = ServerConfig(server.url("/").toString().trimEnd('/'), "secret-token")
        api = BridgeClient.api(config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun `the bearer token is sent with every request`() = runBlocking {
        json("""{"total":0,"offset":0,"limit":500,"games":[]}""")
        api.getGames()
        val request = server.takeRequest()
        assertEquals("Bearer secret-token", request.getHeader("Authorization"))
    }

    @Test
    fun `the games payload is parsed`() = runBlocking {
        json(
            """
            {
              "total": 2, "offset": 0, "limit": 500,
              "games": [
                {
                  "id": "abc", "name": "Hades", "source": "Steam",
                  "genres": ["Roguelike"], "categories": ["Favorites"], "tags": ["Co-op"],
                  "features": ["Single Player"], "platforms": ["PC (Windows)"],
                  "completionStatus": "Played", "isInstalled": true,
                  "favorite": true, "hidden": false,
                  "playtime": 7200, "playCount": 12,
                  "lastActivity": "2026-03-20T15:30:00.0000000+00:00",
                  "userScore": 85
                },
                { "id": "def", "name": "Untitled" }
              ]
            }
            """.trimIndent()
        )
        val page = api.getGames()
        assertEquals(2, page.total)
        assertEquals(2, page.games.size)

        val hades = page.games[0]
        assertEquals("abc", hades.id)
        assertEquals("Hades", hades.name)
        assertEquals("Steam", hades.source)
        assertEquals(listOf("Roguelike"), hades.genres)
        assertEquals(listOf("Favorites"), hades.categories)
        assertEquals(listOf("Co-op"), hades.tags)
        assertEquals(listOf("Single Player"), hades.features)
        assertEquals(listOf("PC (Windows)"), hades.platforms)
        assertEquals("Played", hades.completionStatus)
        assertTrue(hades.isInstalled)
        assertTrue(hades.favorite)
        assertEquals(7200L, hades.playtime)
        assertEquals(12L, hades.playCount)
        assertEquals(85, hades.userScore)

        val other = page.games[1]
        assertEquals("def", other.id)
        assertNull(other.source)
        assertEquals(0L, other.playtime)
        assertNull(other.userScore)
    }

    @Test
    fun `the full game payload carries the install size`() = runBlocking {
        json(
            """
            {
              "id": "abc", "name": "Hades", "source": "Steam",
              "description": "A roguelike.", "notes": "notes",
              "developers": ["Supergiant"], "publishers": ["Supergiant"],
              "installDirectory": "C:/Games/Hades",
              "installSize": 3221225472,
              "hasCover": true, "hasBackground": false,
              "communityScore": 90, "criticScore": 88, "userScore": 85,
              "releaseDate": "2020-09-17"
            }
            """.trimIndent()
        )
        val details = api.getGame("abc")
        assertEquals("abc", details.id)
        assertEquals("A roguelike.", details.description)
        assertEquals(listOf("Supergiant"), details.developers)
        assertEquals("C:/Games/Hades", details.installDirectory)
        assertEquals(3221225472L, details.installSize)
        assertTrue(details.hasCover)
        assertEquals(90, details.communityScore)
    }

    @Test
    fun `the stats payload is parsed`() = runBlocking {
        json(
            """
            {
              "totalGames": 150, "installed": 120, "notInstalled": 30,
              "hidden": 2, "favorite": 9, "categorized": 100, "uncategorized": 50,
              "totalPlaytimeHours": 1234.5,
              "bySource": [{"source":"Steam","count":100}],
              "byCompletionStatus": [{"status":"Played","count":80}],
              "topGenres": [{"genre":"RPG","count":40}],
              "recentlyPlayed": [{"id":"a","name":"Hades","lastPlayed":"2026-03-20T15:30:00.0000000+00:00"}]
            }
            """.trimIndent()
        )
        val stats = api.getStats()
        assertEquals(150, stats.totalGames)
        assertEquals(120, stats.installed)
        assertEquals(1234.5, stats.totalPlaytimeHours, 0.001)
        assertEquals(1, stats.bySource.size)
        assertEquals("Steam", stats.bySource[0].source)
        assertEquals(1, stats.topGenres.size)
        assertEquals("Hades", stats.recentlyPlayed[0].name)
    }

    @Test
    fun `an unauthorized response raises an http exception`() = runBlocking {
        json("""{"error":"Unauthorized"}""", code = 401)
        val error = runCatching { api.getGames() }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error is HttpException)
        assertEquals(401, (error as HttpException).code())
    }

    @Test
    fun `the source list is parsed`() = runBlocking {
        json("""[{"id":"1","name":"Steam"},{"id":"2","name":"GOG"}]""")
        val sources = api.getSources()
        assertEquals(2, sources.size)
        assertEquals("Steam", sources[0].name)
    }

    @Test
    fun `the app info is parsed`() = runBlocking {
        json("""{"version":"1.6.0"}""")
        assertEquals("1.6.0", api.getAppInfo().version)
    }
}
