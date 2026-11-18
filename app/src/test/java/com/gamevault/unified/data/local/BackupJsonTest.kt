package com.gamevault.unified.data.local

import com.gamevault.unified.data.model.ServerProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backup/restore document: saved PCs + UI settings in one file. */
class BackupJsonTest {

    private val profiles = listOf(
        ServerProfile(id = "p1", name = "Home PC", baseUrl = "http://192.168.1.10:19821", token = "tok1"),
        ServerProfile(id = "p2", name = "", baseUrl = "http://10.0.0.5:19821", token = "tok2")
    )

    private val prefs = UiViewPrefs(
        viewMode = ViewMode.GRID,
        gridSpan = 4,
        pageSize = 100,
        retainImages = true,
        appThemeName = "MIDNIGHT",
        fullOffline = true,
        lastFullSync = 1_700_000_000_000L,
        notes = mapOf("g1" to "note one"),
        collections = mapOf("Weekend" to setOf("g1"))
    )

    @Test
    fun `encode produces a backup document`() {
        val json = BackupJson.encode(profiles, prefs)
        val obj = JSONObject(json)
        assertEquals(BackupJson.FORMAT, obj.getString("format"))
        assertEquals(BackupJson.FORMAT_VERSION, obj.getInt("version"))
        assertTrue(obj.has("servers"))
        assertTrue(obj.has("settings"))
    }

    @Test
    fun `round trip preserves the profiles`() {
        val decoded = BackupJson.decode(BackupJson.encode(profiles, prefs))
        assertEquals(profiles, decoded.profiles)
    }

    @Test
    fun `round trip preserves the settings`() {
        val decoded = BackupJson.decode(BackupJson.encode(profiles, prefs))
        assertEquals(prefs, decoded.settings)
    }

    @Test
    fun `an empty profile list is preserved`() {
        val decoded = BackupJson.decode(BackupJson.encode(emptyList(), prefs))
        assertTrue(decoded.profiles.isEmpty())
        assertEquals(1, decoded.version)
    }

    @Test
    fun `a file without servers decodes to an empty list`() {
        val decoded = BackupJson.decode("""{"format":"playnite-remote-backup","version":1}""")
        assertTrue(decoded.profiles.isEmpty())
        assertNull(decoded.settings)
    }

    @Test
    fun `a file without settings keeps the settings null`() {
        val decoded = BackupJson.decode(
            """{"format":"playnite-remote-backup","version":1,"servers":[]}"""
        )
        assertNull(decoded.settings)
    }

    @Test
    fun `a foreign format is rejected`() {
        val error = runCatching {
            BackupJson.decode("""{"format":"something-else","version":1}""")
        }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test
    fun `profiles without a token are skipped on restore`() {
        val json = """
            {"format":"playnite-remote-backup","version":1,
             "servers":[{"id":"a","baseUrl":"http://pc:19821","token":""}]}
        """.trimIndent()
        val decoded = BackupJson.decode(json)
        assertTrue(decoded.profiles.isEmpty())
    }

    @Test
    fun `an unversioned document still decodes`() {
        val decoded = BackupJson.decode(
            """{"servers":[{"id":"a","baseUrl":"http://pc:19821","token":"t"}]}"""
        )
        assertEquals(1, decoded.profiles.size)
        assertEquals(1, decoded.version)
    }
}
