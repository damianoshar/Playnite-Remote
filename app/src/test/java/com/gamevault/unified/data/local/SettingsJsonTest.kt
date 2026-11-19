package com.gamevault.unified.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Export/import of the settings JSON. Pure serialization lives in
 * [SettingsJson] so it can be tested without Android.
 */
class SettingsJsonTest {

    private val prefs = UiViewPrefs(
        viewMode = ViewMode.GRID,
        gridSpan = 4,
        pageSize = 100,
        retainImages = true,
        appThemeName = "MIDNIGHT",
        fullOffline = true,
        lastFullSync = 1_700_000_000_000L,
        notes = mapOf("g1" to "note one", "g2" to "note two"),
        collections = mapOf("Weekend" to setOf("g1", "g2"), "Faves" to setOf("g2"))
    )

    @Test
    fun `export contains every preference`() {
        val json = SettingsJson.export(prefs)
        assertTrue(json.contains("\"viewMode\""))
        assertTrue(json.contains("\"gridSpan\""))
        assertTrue(json.contains("\"pageSize\""))
        assertTrue(json.contains("\"retainImages\""))
        assertTrue(json.contains("\"appThemeName\""))
        assertTrue(json.contains("\"fullOffline\""))
        assertTrue(json.contains("\"lastFullSync\""))
        assertTrue(json.contains("\"notes\""))
        assertTrue(json.contains("\"collections\""))
    }

    @Test
    fun `export writes the theme name and view mode verbatim`() {
        val json = SettingsJson.export(prefs)
        assertTrue(json.contains("\"MIDNIGHT\""))
        assertTrue(json.contains("\"GRID\""))
    }

    @Test
    fun `export writes notes and collection members`() {
        val json = SettingsJson.export(prefs)
        assertTrue(json.contains("note one"))
        assertTrue(json.contains("Weekend"))
        assertTrue(json.contains("g1"))
    }

    @Test
    fun `round trip preserves every field`() {
        val parsed = SettingsJson.parse(SettingsJson.export(prefs))
        assertEquals(prefs, parsed)
    }

    @Test
    fun `parse clamps grid span into range`() {
        val parsed = SettingsJson.parse("""{"gridSpan": 99}""")
        assertEquals(5, parsed.gridSpan)
    }

    @Test
    fun `parse clamps page size into range`() {
        assertEquals(0, SettingsJson.parse("""{"pageSize": -5}""").pageSize)
        assertEquals(500, SettingsJson.parse("""{"pageSize": 99999}""").pageSize)
    }

    @Test
    fun `parse falls back to defaults for an empty document`() {
        val parsed = SettingsJson.parse("{}")
        assertEquals(ViewMode.LIST, parsed.viewMode)
        assertEquals(3, parsed.gridSpan)
        assertEquals(0, parsed.pageSize)
        assertEquals("DEFAULT", parsed.appThemeName)
        assertTrue(parsed.notes.isEmpty())
        assertTrue(parsed.collections.isEmpty())
    }

    @Test
    fun `parse ignores an unknown view mode`() {
        val parsed = SettingsJson.parse("""{"viewMode": "HOLOVISION"}""")
        assertEquals(ViewMode.LIST, parsed.viewMode)
    }

    @Test
    fun `the cover width survives a round trip`() {
        val prefs = UiViewPrefs(coverWidth = 1024)
        assertEquals(1024, SettingsJson.parse(SettingsJson.export(prefs)).coverWidth)
    }

    @Test
    fun `an unknown cover width falls back to the default`() {
        val parsed = SettingsJson.parse("""{"coverWidth": 123}""")
        assertEquals(UiViewPrefs.DEFAULT_COVER_WIDTH, parsed.coverWidth)
    }

    @Test
    fun `a missing cover width falls back to the default`() {
        assertEquals(UiViewPrefs.DEFAULT_COVER_WIDTH, SettingsJson.parse("{}").coverWidth)
    }

    @Test
    fun `only the offered cover widths are kept`() {
        UiViewPrefs.COVER_WIDTHS.forEach { width ->
            assertEquals(width, UiViewPrefs.coerceCoverWidth(width))
        }
        assertEquals(UiViewPrefs.DEFAULT_COVER_WIDTH, UiViewPrefs.coerceCoverWidth(999))
        assertEquals(UiViewPrefs.DEFAULT_COVER_WIDTH, UiViewPrefs.coerceCoverWidth(0))
    }
}
