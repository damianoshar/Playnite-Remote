package com.gamevault.unified.data.local

import com.gamevault.unified.data.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure codec for the saved server profiles. */
class ServerProfilesJsonTest {

    private val profiles = listOf(
        ServerProfile(id = "p1", name = "Home PC", baseUrl = "http://192.168.1.10:19821", token = "tok1"),
        ServerProfile(id = "p2", name = "", baseUrl = "http://10.0.0.5:19821", token = "tok2")
    )

    @Test
    fun `round trip preserves every profile`() {
        assertEquals(profiles, ServerProfilesJson.decode(ServerProfilesJson.encode(profiles)))
    }

    @Test
    fun `blank input decodes to an empty list`() {
        assertTrue(ServerProfilesJson.decode("").isEmpty())
        assertTrue(ServerProfilesJson.decode("not json").isEmpty())
    }

    @Test
    fun `profiles without a token are skipped`() {
        val json = """[{"id":"a","baseUrl":"http://pc:19821","token":""}]"""
        assertTrue(ServerProfilesJson.decode(json).isEmpty())
    }

    @Test
    fun `profiles without a url are skipped`() {
        val json = """[{"id":"a","baseUrl":"","token":"tok"}]"""
        assertTrue(ServerProfilesJson.decode(json).isEmpty())
    }

    @Test
    fun `order is preserved`() {
        val decoded = ServerProfilesJson.decode(ServerProfilesJson.encode(profiles))
        assertEquals(listOf("p1", "p2"), decoded.map { it.id })
    }

    @Test
    fun `a missing id gets a generated one`() {
        val json = """[{"baseUrl":"http://pc:19821","token":"tok"}]"""
        val decoded = ServerProfilesJson.decode(json)
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].id.isNotBlank())
    }
}
