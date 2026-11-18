package com.gamevault.unified.data.local

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local crash log.
 *
 * No crash-reporting service, no network calls and no extra dependencies:
 * uncaught exceptions are appended to a file inside the app's private
 * storage and the user can read, copy or share them from the diagnostics
 * screen. This keeps the privacy model of the app ("nothing leaves the
 * phone unless the user sends it").
 */
object CrashLog {

    private const val FILE_NAME = "crash_log.txt"

    /** Keep the log small — it is a diagnostic aid, not an archive. */
    private const val MAX_BYTES = 64 * 1024

    /** One recorded crash. */
    data class Entry(
        val timestamp: Long,
        val threadName: String,
        val stackTrace: String
    ) {
        val summary: String
            get() = stackTrace.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Appends a crash. Never throws — it runs while the app is dying. */
    fun record(context: Context, thread: Thread, error: Throwable) {
        recordIn(file(context), thread, error)
    }

    /** Appends a crash to [file]. Never throws. */
    fun recordIn(file: File, thread: Thread, error: Throwable) {
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val entry = buildString {
                append("--- ").append(stamp)
                append(" [").append(thread.name).append("]\n")
                append(error.stackTraceToString())
                append("\n")
            }
            file.appendText(entry)
            trim(file)
        } catch (ignored: Throwable) {
            // A failing crash logger must never mask the original crash.
        }
    }

    /** Reads the recorded crashes, newest first. */
    fun entries(context: Context): List<Entry> = entriesIn(file(context))

    /** Reads the recorded crashes from [file], newest first. */
    fun entriesIn(file: File): List<Entry> {
        if (!file.exists()) return emptyList()
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return emptyList()
        }
        val blocks = text.split("--- ").filter { it.isNotBlank() }
        return blocks.mapNotNull { block ->
            // A valid entry is a header line plus a body; anything else is
            // a corrupt file and is skipped instead of crashing the reader.
            val newline = block.indexOf('\n')
            if (newline <= 0) return@mapNotNull null
            val header = block.substring(0, newline)
            val body = block.substring(newline + 1).trim()
            val threadName = header.substringAfter('[', "").substringBefore(']', "")
            if (body.isBlank()) null else Entry(parseTimestamp(header), threadName, body)
        }.reversed()
    }

    /** Removes every recorded crash. */
    fun clear(context: Context) {
        clearIn(file(context))
    }

    /** Removes every recorded crash from [file]. */
    fun clearIn(file: File) {
        try {
            file.delete()
        } catch (ignored: Throwable) {
        }
    }

    private fun parseTimestamp(header: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(header.trim())?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    /** Keeps the log under [MAX_BYTES] by dropping the oldest entries. */
    private fun trim(file: File) {
        if (!file.exists() || file.length() <= MAX_BYTES) return
        val text = file.readText()
        val blocks = text.split("--- ").filter { it.isNotBlank() }
        var kept = StringBuilder()
        // Newest last in the file -> walk backwards and keep the tail.
        for (i in blocks.indices.reversed()) {
            val candidate = "--- " + blocks[i] + "\n"
            if (kept.length + candidate.length > MAX_BYTES) break
            kept.insert(0, candidate)
        }
        file.writeText(kept.toString())
    }
}
