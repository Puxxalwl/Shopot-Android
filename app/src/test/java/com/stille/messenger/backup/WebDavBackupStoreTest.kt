package com.stille.messenger.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavBackupStoreTest {

    /** Scripted transport: replies in order, records every request. */
    private class FakeTransport : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        val responses = ArrayDeque<HttpResponse>()

        fun reply(vararg responses: HttpResponse) {
            this.responses.addAll(responses)
        }

        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            if (responses.isEmpty()) throw AssertionError("unexpected request: ${request.method} ${request.url}")
            return responses.removeFirst()
        }
    }

    private fun ok(body: ByteArray = ByteArray(0)) = HttpResponse(200, body)
    private fun notFound() = HttpResponse(404, ByteArray(0))
    private fun unauthorized() = HttpResponse(401, ByteArray(0))

    private fun blob(bytes: Int = 800) = ByteArray(bytes) { (it % 251).toByte() }

    private val credentials = WebDavCredentials("https://webdav.example.test/", "user", "secret").validated()

    private val sidecar = RecoveryMetadata.encode(
        RecoveryMetadata.Metadata(
            keySource = RecoveryMetadata.KEY_SOURCE_PASSKEY,
            prfSalt = ByteArray(32) { 7 },
            createdMs = 1_700_000_000_000,
            apiVersion = "2.6.0"
        )
    )

    private fun store(transport: FakeTransport) = WebDavBackupStore(transport, credentials)

    @Test
    fun `upload probes the directory then puts both files`() {
        val transport = FakeTransport().apply {
            reply(
                notFound(),          // hasBackup() probe for the blob
                ok(),                // MKCOL directory
                ok(),                // PUT blob
                ok(),                // PUT sidecar
            )
        }
        assertFalse(store(transport).upload(blob(), sidecar))
        val methods = transport.requests.map { it.method }
        assertEquals(listOf("GET", "MKCOL", "PUT", "PUT"), methods)
        assertEquals("https://webdav.example.test/stille-backup/stille-recovery-v1.bin", transport.requests[2].url)
        assertArrayEquals(blob(), transport.requests[2].body)
    }

    @Test
    fun `upload reports replacement when a copy already exists`() {
        val transport = FakeTransport().apply {
            reply(
                ok(blob()),          // existing blob probe
                ok(), ok(), ok(),
            )
        }
        assertTrue(store(transport).upload(blob(), sidecar))
    }

    @Test
    fun `upload tolerates an existing or forbidden directory`() {
        listOf(405, 403, 404, 201).forEach { directoryStatus ->
            val transport = FakeTransport().apply {
                reply(notFound(), HttpResponse(directoryStatus, ByteArray(0)), ok(), ok())
            }
            store(transport).upload(blob(), sidecar)
        }
    }

    @Test
    fun `upload fails cleanly on bad credentials`() {
        val transport = FakeTransport().apply {
            reply(notFound(), unauthorized())
        }
        val error = assertThrows(WebDavBackupStore.StoreException::class.java) {
            store(transport).upload(blob(), sidecar)
        }
        assertTrue(error.authentication)
    }

    @Test
    fun `download returns blob and sidecar`() {
        val data = blob()
        val transport = FakeTransport().apply {
            reply(ok(data), ok(sidecar))
        }
        val downloaded = store(transport).download()!!
        assertArrayEquals(data, downloaded.blob)
        assertArrayEquals(sidecar, downloaded.sidecar)
    }

    @Test
    fun `download returns null when no copy exists`() {
        val transport = FakeTransport().apply { reply(notFound()) }
        assertNull(store(transport).download())
    }

    @Test
    fun `delete removes the blob and reports existence`() {
        val transport = FakeTransport().apply {
            reply(ok(blob()), ok())
        }
        assertTrue(store(transport).delete())
        assertEquals("DELETE", transport.requests.last().method)
    }

    @Test
    fun `delete reports false when nothing was stored`() {
        val transport = FakeTransport().apply { reply(notFound()) }
        assertFalse(store(transport).delete())
        assertEquals(1, transport.requests.size) // no DELETE request
    }

    @Test
    fun `requests carry basic authorization`() {
        val transport = FakeTransport().apply { reply(notFound()) }
        store(transport).hasBackup()
        val expected = "Basic " + java.util.Base64.getEncoder().encodeToString("user:secret".toByteArray())
        assertEquals(expected, transport.requests.single().headers["Authorization"])
    }

    @Test
    fun `upload rejects out-of-range blobs`() {
        val transport = FakeTransport()
        assertThrows(WebDavBackupStore.StoreException::class.java) {
            store(transport).upload(ByteArray(0), sidecar)
        }
        assertThrows(WebDavBackupStore.StoreException::class.java) {
            store(transport).upload(ByteArray(97 * 1024), sidecar)
        }
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun `credentials validation rejects unsafe values`() {
        assertThrows(WebDavCredentials.ValidationException::class.java) {
            WebDavCredentials("http://webdav.example.test", "user", "secret").validated()
        }
        assertThrows(WebDavCredentials.ValidationException::class.java) {
            WebDavCredentials("https://user:pass@webdav.example.test", "user", "secret").validated()
        }
        assertThrows(WebDavCredentials.ValidationException::class.java) {
            WebDavCredentials("https://webdav.example.test/../etc", "user", "secret").validated()
        }
        assertThrows(WebDavCredentials.ValidationException::class.java) {
            WebDavCredentials("https://webdav.example.test", "", "secret").validated()
        }
        assertThrows(WebDavCredentials.ValidationException::class.java) {
            WebDavCredentials("https://webdav.example.test", "user", "").validated()
        }
    }

    @Test
    fun `credentials normalization drops the trailing slash`() {
        val normalized = WebDavCredentials("https://webdav.yandex.ru/", "u", "p").validated()
        assertEquals("https://webdav.yandex.ru", normalized.baseUrl)
    }
}
