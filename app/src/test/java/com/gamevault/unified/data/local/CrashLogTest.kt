package com.gamevault.unified.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Local crash log — file format, ordering and trimming. */
class CrashLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun logFile(): File = File(folder.root, "crash_log.txt")

    @Test
    fun `an empty log has no entries`() {
        assertTrue(CrashLog.entriesIn(logFile()).isEmpty())
    }

    @Test
    fun `a recorded crash is read back`() {
        val file = logFile()
        CrashLog.recordIn(file, Thread("main"), IllegalStateException("boom"))
        val entries = CrashLog.entriesIn(file)
        assertEquals(1, entries.size)
        assertTrue(entries[0].stackTrace.contains("boom"))
        assertEquals("main", entries[0].threadName)
    }

    @Test
    fun `the summary is the first stack trace line`() {
        val file = logFile()
        CrashLog.recordIn(file, Thread("main"), IllegalStateException("kaboom"))
        assertTrue(CrashLog.entriesIn(file).first().summary.contains("kaboom"))
    }

    @Test
    fun `the newest crash comes first`() {
        val file = logFile()
        CrashLog.recordIn(file, Thread("a"), IllegalStateException("first"))
        CrashLog.recordIn(file, Thread("b"), IllegalStateException("second"))
        val entries = CrashLog.entriesIn(file)
        assertEquals(2, entries.size)
        assertTrue(entries[0].stackTrace.contains("second"))
        assertTrue(entries[1].stackTrace.contains("first"))
    }

    @Test
    fun `clearing removes every entry`() {
        val file = logFile()
        CrashLog.recordIn(file, Thread("main"), IllegalStateException("boom"))
        CrashLog.clearIn(file)
        assertTrue(CrashLog.entriesIn(file).isEmpty())
    }

    @Test
    fun `a corrupt file does not crash the reader`() {
        val file = logFile()
        file.writeText("not a crash log at all")
        // No header -> nothing to parse, and no exception.
        assertTrue(CrashLog.entriesIn(file).isEmpty())
    }
}
