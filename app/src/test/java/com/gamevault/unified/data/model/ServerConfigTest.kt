package com.gamevault.unified.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    @Test
    fun `base url without scheme gets http prepended`() {
        assertEquals("http://192.168.1.20:19821", ServerConfig("192.168.1.20:19821").trimmedBase())
    }

    @Test
    fun `https is preserved`() {
        assertEquals("https://pc.local:19821", ServerConfig("https://pc.local:19821").trimmedBase())
    }

    @Test
    fun `trailing slashes are removed`() {
        assertEquals("http://pc:19821", ServerConfig("http://pc:19821///").trimmedBase())
    }

    @Test
    fun `retrofit base ends with slash`() {
        assertEquals("http://pc:19821/", ServerConfig("http://pc:19821").retrofitBase())
    }

    @Test
    fun `cover url includes type and width`() {
        val url = ServerConfig("http://pc:19821", "tok").coverUrl("abc", "background", 512)
        assertEquals("http://pc:19821/api/games/abc/cover?type=background&width=512", url)
    }

    @Test
    fun `cover url without width omits the parameter`() {
        val url = ServerConfig("http://pc:19821", "tok").coverUrl("abc")
        assertEquals("http://pc:19821/api/games/abc/cover?type=cover", url)
    }

    @Test
    fun `is configured requires both url and token`() {
        assertFalse(ServerConfig("http://pc:19821", "").isConfigured)
        assertFalse(ServerConfig("", "tok").isConfigured)
        assertTrue(ServerConfig("http://pc:19821", "tok").isConfigured)
    }
}

class GameModelTest {

    @Test
    fun `never played when playtime is zero`() {
        assertEquals("Never played", Game(title = "X", playtimeSeconds = 0).playtimeText)
    }

    @Test
    fun `minutes under an hour`() {
        assertEquals("42 min", Game(title = "X", playtimeSeconds = 42 * 60).playtimeText)
    }

    @Test
    fun `hours and minutes over an hour`() {
        assertEquals("3h 5m", Game(title = "X", playtimeSeconds = (3 * 60 + 5) * 60L).playtimeText)
    }
}
