package com.gamevault.unified.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun `all seven themes exist with display labels`() {
        assertEquals(7, AppTheme.entries.size)
        assertEquals("Default", AppTheme.DEFAULT.label)
        assertEquals("Midnight", AppTheme.MIDNIGHT.label)
        assertEquals("Forest", AppTheme.FOREST.label)
        assertEquals("Neon Pulse", AppTheme.NEON.label)
        assertEquals("Synthwave", AppTheme.SYNTHWAVE.label)
        assertEquals("RGB Neon", AppTheme.RGB.label)
        assertEquals("Arcade", AppTheme.ARCADE.label)
    }

    @Test
    fun `known name resolves to its theme`() {
        assertEquals(AppTheme.SYNTHWAVE, appThemeFromName("SYNTHWAVE"))
    }

    @Test
    fun `unknown name falls back to default`() {
        assertEquals(AppTheme.DEFAULT, appThemeFromName("does-not-exist"))
        assertEquals(AppTheme.DEFAULT, appThemeFromName(""))
    }

    @Test
    fun `every theme has a unique label`() {
        val labels = AppTheme.entries.map { it.label }
        assertEquals(labels.size, labels.distinct().size)
    }
}
