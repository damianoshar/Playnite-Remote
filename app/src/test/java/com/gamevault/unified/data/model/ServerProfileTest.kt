package com.gamevault.unified.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileTest {

    @Test
    fun `label falls back to the address when unnamed`() {
        val p = ServerProfile(name = "", baseUrl = "http://pc:19821", token = "tok")
        assertEquals("http://pc:19821", p.label)
    }

    @Test
    fun `label uses the explicit name`() {
        val p = ServerProfile(name = "Home PC", baseUrl = "http://pc:19821", token = "tok")
        assertEquals("Home PC", p.label)
    }

    @Test
    fun `profile converts to a server config`() {
        val p = ServerProfile(name = "PC", baseUrl = "http://pc:19821", token = "tok")
        assertEquals(ServerConfig("http://pc:19821", "tok"), p.toConfig())
    }

    @Test
    fun `matching ignores trailing slash and url case`() {
        val a = ServerProfile(baseUrl = "http://PC:19821/", token = "tok")
        val b = ServerProfile(baseUrl = "http://pc:19821", token = "tok")
        assertTrue(ServerProfile.matches(a, b))
    }

    @Test
    fun `a different token is a different profile`() {
        val a = ServerProfile(baseUrl = "http://pc:19821", token = "tok1")
        val b = ServerProfile(baseUrl = "http://pc:19821", token = "tok2")
        assertFalse(ServerProfile.matches(a, b))
    }

    @Test
    fun `a different address is a different profile`() {
        val a = ServerProfile(baseUrl = "http://pc:19821", token = "tok")
        val b = ServerProfile(baseUrl = "http://other:19821", token = "tok")
        assertFalse(ServerProfile.matches(a, b))
    }
}
