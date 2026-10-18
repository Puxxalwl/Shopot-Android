package com.stille.messenger.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupStoreTest {

    private class RequestLog {
        val requests = mutableListOf<HttpRequest>()
        val responses = ArrayDeque<HttpResponse>()
        val tokens = mutableListOf<String>()
    }

    /** Scripted transport: replies in order, records every request. */
    private class FakeTransport(private val log: RequestLog) : HttpTransport {
        override fun execute(request: HttpRequest): HttpResponse {
            log.requests.add(request)
            if (log.responses.isEmpty()) throw AssertionError("unexpected request: ${request.method} ${request.url}")
            return log.responses.removeFirst()
        }
    }

    private class FakeTokens(private val log: RequestLog, private val token: String = "token-1") : DriveTokenProvider {
        var invalidated: String? = null
        override fun accessToken(): String {
            log.tokens.add(token)
            return token
        }

        override fun invalidate(token: String) {
            invalidated = token
        }
    }

    private fun store(log: RequestLog, tokens: FakeTokens) = DriveBackupStore(FakeTransport(log), tokens)

    private fun ok(body: String) = HttpResponse(200, body.toByteArray())

    private fun blob(bytes: Int = 800) = ByteArray(bytes) { (it % 251).toByte() }

    private val sidecar = RecoveryMetadata.encode(
        RecoveryMetadata.Metadata(
            keySource = RecoveryMetadata.KEY_SOURCE_PASSKEY,
            prfSalt = ByteArray(32) { 7 },
            createdMs = 1_700_000_000_000,
            apiVersion = "2.6.0"
        )
    )

    @Test
    fun `upload creates blob and sidecar when none exist`() {
        val log = RequestLog().apply {
            responses.addAll(listOf(
                ok("""{"files":[]}"""),          // blob lookup
                ok("""{"id":"blob-1"}"""),       // blob create
                ok("""{"files":[]}"""),          // sidecar lookup
                ok("""{"id":"meta-1"}""")        // sidecar create
            ))
        }
        assertFalse(store(log, FakeTokens(log)).upload(blob(), sidecar))
        assertEquals(4, log.requests.size)
        assertEquals("POST", log.requests[1].method)
        assertTrue(log.requests[1].url.contains("uploadType=multipart"))
        val body = String(log.requests[1].body!!, Charsets.UTF_8)
        assertTrue(body.contains("stille-recovery-v1.bin"))
        assertTrue(body.contains("appDataFolder"))
        assertTrue(String(log.requests[3].body!!, Charsets.UTF_8).contains("stille-recovery-v1.meta"))
        assertEquals("Bearer token-1", log.requests[1].headers["Authorization"])
    }

    @Test
    fun `upload patches both files in place`() {
        val log = RequestLog().apply {
            responses.addAll(listOf(
                ok("""{"files":[{"id":"blob-42"}]}"""), ok("""{"id":"blob-42"}"""),
                ok("""{"files":[{"id":"meta-42"}]}"""), ok("""{"id":"meta-42"}""")
            ))
        }
        assertTrue(store(log, FakeTokens(log)).upload(blob(), sidecar))
        assertEquals("PATCH", log.requests[1].method)
        assertTrue(log.requests[1].url.contains("files/blob-42"))
        assertEquals(blob().size, log.requests[1].body!!.size)
        assertEquals("PATCH", log.requests[3].method)
        assertArrayEquals(sidecar, log.requests[3].body)
    }

    @Test
    fun `download returns blob and sidecar or null when absent`() {
        val payload = blob(120)
        val log = RequestLog().apply {
            responses.addAll(listOf(
                ok("""{"files":[{"id":"file-42"}]}"""),
                HttpResponse(200, payload),
                ok("""{"files":[{"id":"meta-42"}]}"""),
                HttpResponse(200, sidecar)
            ))
        }
        val downloaded = store(log, FakeTokens(log)).download()!!
        assertArrayEquals(payload, downloaded.blob)
        assertArrayEquals(sidecar, downloaded.sidecar)
        assertTrue(log.requests[1].url.contains("alt=media"))

        val empty = RequestLog().apply { responses.add(ok("""{"files":[]}""")) }
        assertNull(store(empty, FakeTokens(empty)).download())
    }

    @Test
    fun `download tolerates a missing sidecar`() {
        val log = RequestLog().apply {
            responses.addAll(listOf(
                ok("""{"files":[{"id":"file-42"}]}"""),
                HttpResponse(200, blob()),
                ok("""{"files":[]}""")
            ))
        }
        val downloaded = store(log, FakeTokens(log)).download()!!
        assertNull(downloaded.sidecar)
    }

    @Test
    fun `multiple backup files are refused instead of guessing`() {
        val log = RequestLog().apply {
            responses.add(ok("""{"files":[{"id":"a"},{"id":"b"}]}"""))
        }
        assertThrows(DriveBackupStore.StoreException::class.java) {
            store(log, FakeTokens(log)).hasBackup()
        }
    }

    @Test
    fun `expired token is invalidated and retried once`() {
        val log = RequestLog().apply {
            responses.addAll(listOf(
                HttpResponse(401, "Unauthorized".toByteArray()),
                ok("""{"files":[]}""") // retry after refresh
            ))
        }
        val tokens = FakeTokens(log)
        store(log, tokens).hasBackup()
        assertEquals("token-1", tokens.invalidated)
        // First call got 401, second (refreshed token) succeeded.
        assertEquals(2, log.requests.size)
    }

    @Test
    fun `delete removes both files and reports existence`() {
        val log = RequestLog().apply {
            responses.addAll(listOf(
                ok("""{"files":[{"id":"x"}]}"""), HttpResponse(204, ByteArray(0)),
                ok("""{"files":[{"id":"y"}]}"""), HttpResponse(204, ByteArray(0))
            ))
        }
        assertTrue(store(log, FakeTokens(log)).delete())
        assertEquals("DELETE", log.requests[1].method)
        assertEquals("DELETE", log.requests[3].method)

        val empty = RequestLog().apply {
            responses.addAll(listOf(ok("""{"files":[]}"""), ok("""{"files":[]}""")))
        }
        assertFalse(store(empty, FakeTokens(empty)).delete())
    }

    @Test
    fun `oversized or empty uploads are rejected locally`() {
        val log = RequestLog()
        val store = store(log, FakeTokens(log))
        assertThrows(DriveBackupStore.StoreException::class.java) { store.upload(ByteArray(0), sidecar) }
        assertThrows(DriveBackupStore.StoreException::class.java) { store.upload(ByteArray(97 * 1024), sidecar) }
        assertThrows(DriveBackupStore.StoreException::class.java) { store.upload(blob(), ByteArray(0)) }
    }

    @Test
    fun `drive scope is appDataFolder only`() {
        assertEquals("https://www.googleapis.com/auth/drive.appdata", DriveBackupStore.scope())
    }

    @Test
    fun `json array field extraction handles real drive replies`() {
        val store = DriveBackupStore(FakeTransport(RequestLog()), FakeTokens(RequestLog()))
        assertEquals(
            listOf("file-9"),
            store.extractJsonStringArrayField("""{"files":[{"id":"file-9"}],"incompleteSearch":false}""", "files", "id")
        )
        assertEquals(
            emptyList<String>(),
            store.extractJsonStringArrayField("""{"files":[]}""", "files", "id")
        )
        assertEquals(
            emptyList<String>(),
            store.extractJsonStringArrayField("""not json""", "files", "id")
        )
    }
}
