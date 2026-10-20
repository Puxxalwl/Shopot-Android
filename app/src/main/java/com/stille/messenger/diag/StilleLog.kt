package com.stille.messenger.diag

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Redacted, bounded, file-backed diagnostics log (AGENTS.md §6).
 *
 * Only fixed event names, stage identifiers and exception class names are
 * recorded. Message bodies, file contents, invite codes, contact names and
 * key material must never be passed to this logger; call sites are reviewed
 * against that contract.
 *
 * Layout on disk (always inside the app's private storage):
 * ```
 * files/logs/stille.log     — current log (rotated at MAX_BYTES)
 * files/logs/stille-1.log   — previous generation, replaced on rotation
 * ```
 *
 * [StilleLog] schedules every write on a single daemon worker with a bounded
 * queue: a log flood drops entries instead of blocking the caller.
 */
object StilleLog {
    const val FILE_NAME = "stille.log"
    const val ROTATED_NAME = "stille-1.log"
    const val MAX_BYTES = 512 * 1024

    @Volatile private var writer: LogWriter? = null

    /** Call once from [com.stille.messenger.StilleMessengerApp.onCreate]. */
    fun init(context: Context) {
        if (writer != null) return
        writer = LogWriter(LogFile(File(context.filesDir, "logs")))
    }

    /** The log directory, initializing it lazily when the app object was not used. */
    fun directory(context: Context): File {
        writer?.let { return it.file.directory }
        return File(context.applicationContext.filesDir, "logs")
            .also { created -> writer = LogWriter(LogFile(created)) }
    }

    fun i(tag: String, event: String, detail: String? = null) = write('I', tag, event, detail)
    fun w(tag: String, event: String, detail: String? = null) = write('W', tag, event, detail)

    /** Records the exception's class name only; never its message or stack text. */
    fun e(tag: String, event: String, error: Throwable? = null, detail: String? = null) =
        write('E', tag, event, detail ?: error?.let { "exc=${it.javaClass.simpleName}" })

    fun clear() {
        writer?.submit { it.clear() }
    }

    fun totalBytes(): Long = writer?.file?.totalBytes() ?: 0L

    fun lastModified(): Long = writer?.file?.lastModified() ?: 0L

    private fun write(level: Char, tag: String, event: String, detail: String?) {
        writer?.submit { it.append(formatLine(System.currentTimeMillis(), level, tag, event, detail)) }
    }

    /** Bounded-queue async wrapper over the synchronous [LogFile]. */
    private class LogWriter(val file: LogFile) {
        private val executor: ExecutorService = ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(64),
            { runnable -> Thread(runnable, "stille-log").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy(),
        )

        fun submit(block: (LogFile) -> Unit) {
            try {
                executor.execute { block(file) }
            } catch (_: RejectedExecutionException) {
                // Bounded queue overflow: drop the record.
            }
        }
    }
}

/** Single line, control-char-free, length-capped log record. */
internal fun formatLine(timeMs: Long, level: Char, tag: String, event: String, detail: String?): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(timeMs))
    val body = buildString {
        append(stamp).append(' ').append(level).append(' ')
        append(sanitize(tag)).append(' ').append(sanitize(event))
        if (!detail.isNullOrBlank()) {
            append(' ').append(sanitize(detail))
        }
    }
    return if (body.length > MAX_LINE_CHARS) body.take(MAX_LINE_CHARS) else body
}

private const val MAX_LINE_CHARS = 512

private fun sanitize(value: String): String =
    value.map { if (it.isISOControl() || (it.isWhitespace() && it != ' ')) ' ' else it }
        .joinToString("")
        .trim()
        .ifBlank { "-" }

/**
 * Synchronous, size-capped append-only file with single-generation rotation.
 * All methods are [Synchronized]: safe for the background writer and for
 * read-only access from the UI (tiny, bounded operations).
 */
internal class LogFile(
    val directory: File,
    private val maxBytes: Int = StilleLog.MAX_BYTES
) {
    private val current: File get() = File(directory, StilleLog.FILE_NAME)
    private val rotated: File get() = File(directory, StilleLog.ROTATED_NAME)

    @Synchronized
    fun append(line: String) {
        try {
            directory.mkdirs()
            val lineBytes = (line + "\n").toByteArray(Charsets.UTF_8)
            // Rotate before writing so the current file never exceeds the cap.
            if (current.isFile && current.length() + lineBytes.size > maxBytes) rotate()
            FileOutputStream(current, true).use { output ->
                output.write(lineBytes)
            }
        } catch (_: Exception) {
            // Diagnostics must never crash the app or log about themselves.
        }
    }

    @Synchronized
    fun clear() {
        try {
            current.delete()
            rotated.delete()
        } catch (_: Exception) { }
    }

    @Synchronized
    fun totalBytes(): Long = runCatching { current.length() + rotated.length() }.getOrDefault(0L)

    @Synchronized
    fun lastModified(): Long =
        runCatching { maxOf(current.lastModified(), rotated.lastModified()) }.getOrDefault(0L)

    private fun rotate() {
        rotated.delete()
        if (!current.renameTo(rotated)) current.delete()
    }
}
