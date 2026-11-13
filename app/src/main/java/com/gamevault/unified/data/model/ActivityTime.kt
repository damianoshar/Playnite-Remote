package com.gamevault.unified.data.model

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Parses the bridge's `lastActivity` timestamps.
 *
 * The plugin emits ISO-8601 round-trip strings (`DateTime.ToString("o")`),
 * but offsets differ between machines, so sorting the raw strings is not
 * reliable — the values must be compared as instants.
 */
object ActivityTime {

    /** Milliseconds since the epoch, or [Long.MIN_VALUE] when unparseable. */
    fun parseMillis(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return Long.MIN_VALUE
        return try {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                java.time.Instant.parse(raw).toEpochMilli()
            } catch (e2: DateTimeParseException) {
                Long.MIN_VALUE
            }
        }
    }

    /** True when the timestamp could be parsed. */
    fun isValid(value: String?): Boolean = parseMillis(value) != Long.MIN_VALUE
}
