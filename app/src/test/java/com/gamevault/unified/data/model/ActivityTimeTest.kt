package com.gamevault.unified.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge emits ISO-8601 timestamps, but offsets differ per machine, so
 * raw string sorting is wrong — these must be compared as instants.
 */
class ActivityTimeTest {

    @Test
    fun `parses a bridge round-trip timestamp`() {
        val millis = ActivityTime.parseMillis("2026-03-04T10:20:30.0000000+00:00")
        assertTrue(millis > 0)
    }

    @Test
    fun `same instant in different offsets parses to the same millis`() {
        val utc = ActivityTime.parseMillis("2026-03-04T10:00:00.0000000+00:00")
        val plusTwo = ActivityTime.parseMillis("2026-03-04T12:00:00.0000000+02:00")
        assertEquals(utc, plusTwo)
    }

    @Test
    fun `offset timestamps sort correctly regardless of offset`() {
        // Lexicographically "12:00+02:00" > "10:00+00:00", but they are the
        // same instant.
        val a = "2026-03-04T10:00:00.0000000+00:00"
        val b = "2026-03-04T12:00:00.0000000+02:00"
        assertEquals(ActivityTime.parseMillis(a), ActivityTime.parseMillis(b))
    }

    @Test
    fun `parses a plain instant string`() {
        assertTrue(ActivityTime.parseMillis("2026-03-04T10:00:00Z") > 0)
    }

    @Test
    fun `blank and null are invalid`() {
        assertEquals(Long.MIN_VALUE, ActivityTime.parseMillis(null))
        assertEquals(Long.MIN_VALUE, ActivityTime.parseMillis(""))
        assertEquals(Long.MIN_VALUE, ActivityTime.parseMillis("   "))
        assertFalse(ActivityTime.isValid(null))
    }

    @Test
    fun `garbage is invalid`() {
        assertEquals(Long.MIN_VALUE, ActivityTime.parseMillis("not a date"))
        assertFalse(ActivityTime.isValid("not a date"))
    }

    @Test
    fun `game exposes the parsed millis`() {
        val g = Game(title = "X", lastActivity = "2026-03-04T10:00:00.0000000+00:00")
        assertEquals(ActivityTime.parseMillis("2026-03-04T10:00:00.0000000+00:00"), g.lastActivityMillis)
    }
}
