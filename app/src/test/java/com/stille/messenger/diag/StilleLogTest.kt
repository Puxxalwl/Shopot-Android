package com.stille.messenger.diag

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StilleLogTest {

    @Test
    fun `formatLine keeps one sanitized capped line`() {
        val line = formatLine(1_700_000_000_000, 'W', "update", "download-failed", "exc=IOException")
        assertTrue(line.startsWith("2023-11-14T22:13:20.000Z W update download-failed"))
        assertTrue(line.endsWith("exc=IOException"))
        assertFalse(line.contains('\n'))
    }

    @Test
    fun `formatLine strips control characters and newlines`() {
        val line = formatLine(0, 'I', "ta\ng", "ev\rent", "de tail\n")
        assertFalse(line.contains('\n'))
        assertFalse(line.contains('\r'))
    }

    @Test
    fun `formatLine caps line length`() {
        val line = formatLine(0, 'I', "tag", "event", "d".repeat(5000))
        assertTrue(line.length <= 512)
    }

    @Test
    fun `formatLine drops empty detail`() {
        val line = formatLine(1_700_000_000_000, 'I', "tag", "event", null)
        assertTrue(line.endsWith(" I tag event"))
    }

    @Test
    fun `formatLine never returns a blank field`() {
        val line = formatLine(0, 'I', " ", "event", null)
        assertFalse(line.contains("  event"))
    }

    @Test
    fun `log file appends lines`() {
        val directory = tempDirectory()
        val file = LogFile(directory)
        file.append("one")
        file.append("two")
        assertEquals("one\ntwo\n", File(directory, StilleLog.FILE_NAME).readText())
    }

    @Test
    fun `log file rotates at the byte cap`() {
        val directory = tempDirectory()
        val small = LogFile(directory, maxBytes = 100)
        repeat(20) { small.append("event-number-$it " + "x".repeat(10)) }
        assertTrue(File(directory, StilleLog.ROTATED_NAME).isFile)
        assertTrue(File(directory, StilleLog.FILE_NAME).length() <= 100)
        assertTrue(small.totalBytes() > 100)
    }

    @Test
    fun `clear removes both generations`() {
        val directory = tempDirectory()
        val small = LogFile(directory, maxBytes = 50)
        repeat(10) { small.append("event-number-$it " + "x".repeat(10)) }
        small.clear()
        assertEquals(0L, small.totalBytes())
        assertFalse(File(directory, StilleLog.FILE_NAME).isFile)
        assertFalse(File(directory, StilleLog.ROTATED_NAME).isFile)
    }

    @Test
    fun `concurrent appends never interleave inside one line`() {
        val directory = tempDirectory()
        val big = LogFile(directory, maxBytes = 1 shl 20)
        val threads = (1..8).map { index ->
            Thread {
                repeat(50) { big.append("thread-$index line-$it " + "y".repeat(40)) }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val lines = File(directory, StilleLog.FILE_NAME).readLines()
        assertEquals(400, lines.size)
        assertTrue(lines.all { it.startsWith("thread-") && it.endsWith("y".repeat(40)) })
    }

    private fun tempDirectory(): File =
        kotlin.io.path.createTempDirectory("stille-log").toFile()
}
