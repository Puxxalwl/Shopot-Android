package com.stille.messenger.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google Drive backup storage inside the hidden `appDataFolder` space
 * (CLIENT_RULES.md §4.1: the user's own cloud, no project-operated server).
 *
 * Two opaque files are stored:
 * ```
 * stille-recovery-v1.bin   — the core-sealed recovery blob
 * stille-recovery-v1.meta  — the public metadata sidecar (PRF salt, dates)
 * ```
 *
 * Plain REST v3 calls; no Google SDK, no telemetry. The blob is replaced
 * in place (PATCH), so a failed upload never destroys the last good copy.
 */
class DriveBackupStore(
    private val transport: HttpTransport,
    private val tokenProvider: DriveTokenProvider
) {
    class StoreException(message: String) : Exception(message)

    /** The downloaded pair; the sidecar is null when only a legacy blob exists. */
    data class Downloaded(val blob: ByteArray, val sidecar: ByteArray?)

    companion object {
        const val FILE_NAME = "stille-recovery-v1.bin"
        const val SIDECAR_NAME = "stille-recovery-v1.meta"
        private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val MAX_BLOB_BYTES = 96 * 1024      // core cap: 92786 (§4.10)
        private const val MAX_SIDECAR_BYTES = 4 * 1024
        private const val BOUNDARY = "stille-backup-v1"

        fun scope(): String = SCOPE
    }

    /** Upload (create or replace) the sealed blob and its sidecar atomically enough. */
    fun upload(blob: ByteArray, sidecar: ByteArray): Boolean {
        if (blob.isEmpty() || blob.size > MAX_BLOB_BYTES) {
            throw StoreException("backup blob size out of range")
        }
        if (sidecar.isEmpty() || sidecar.size > MAX_SIDECAR_BYTES) {
            throw StoreException("sidecar size out of range")
        }
        val blobReplaced = replace(FILE_NAME, blob)
        replace(SIDECAR_NAME, sidecar)
        return blobReplaced
    }

    /** Download the blob + sidecar, or null when this Google account has none. */
    fun download(): Downloaded? {
        val blob = downloadByName(FILE_NAME, MAX_BLOB_BYTES) ?: return null
        val sidecar = downloadByName(SIDECAR_NAME, MAX_SIDECAR_BYTES)
        return Downloaded(blob, sidecar)
    }

    fun delete(): Boolean {
        val blobDeleted = deleteByName(FILE_NAME)
        deleteByName(SIDECAR_NAME)
        return blobDeleted
    }

    fun hasBackup(): Boolean = findFileId(FILE_NAME) != null

    // ── internals ────────────────────────────────────────────────────────

    private fun replace(name: String, bytes: ByteArray): Boolean {
        val existing = findFileId(name)
        return if (existing == null) {
            create(name, bytes)
            false
        } else {
            patch(existing, bytes)
            true
        }
    }

    private fun downloadByName(name: String, maxSize: Int): ByteArray? {
        val id = findFileId(name) ?: return null
        val response = executeWithAuth { token ->
            transport.execute(
                HttpRequest(
                    method = "GET",
                    url = "$API/files/$id?alt=media",
                    headers = authHeaders(token)
                )
            )
        } ?: return null
        if (response.statusCode != 200) throw StoreException("Drive download failed: HTTP ${response.statusCode}")
        if (response.body.isEmpty() || response.body.size > maxSize) {
            throw StoreException("downloaded file has invalid size")
        }
        return response.body
    }

    private fun deleteByName(name: String): Boolean {
        val id = findFileId(name) ?: return false
        executeWithAuth { token ->
            transport.execute(
                HttpRequest(method = "DELETE", url = "$API/files/$id", headers = authHeaders(token))
            )
        }
        return true
    }

    private fun findFileId(name: String): String? {
        val query = "name='$name' and 'appDataFolder' in parents and trashed=false"
        val url = "$API/files?q=${urlEncode(query)}&spaces=appDataFolder&fields=files(id)&pageSize=2"
        val response = executeWithAuth { token ->
            transport.execute(HttpRequest(method = "GET", url = url, headers = authHeaders(token)))
        } ?: return null
        if (response.statusCode != 200) throw StoreException("Drive lookup failed: HTTP ${response.statusCode}")
        val ids = extractJsonStringArrayField(response.bodyText(), "files", "id")
        if (ids.size > 1) throw StoreException("multiple '$name' files found")
        return ids.firstOrNull()
    }

    private fun create(name: String, bytes: ByteArray) {
        val metadata = """{"name":"$name","parents":["appDataFolder"]}"""
        val body = buildMultipart(metadata, bytes)
        val response = executeWithAuth { token ->
            transport.execute(
                HttpRequest(
                    method = "POST",
                    url = "$UPLOAD_API/files?uploadType=multipart",
                    headers = authHeaders(token) + mapOf(
                        "Content-Type" to "multipart/related; boundary=$BOUNDARY"
                    ),
                    body = body
                )
            )
        } ?: throw StoreException("Drive upload failed: no response")
        if (response.statusCode !in 200..299) {
            throw StoreException("Drive upload failed: HTTP ${response.statusCode}")
        }
    }

    private fun patch(id: String, bytes: ByteArray) {
        val response = executeWithAuth { token ->
            transport.execute(
                HttpRequest(
                    method = "PATCH",
                    url = "$UPLOAD_API/files/$id?uploadType=media",
                    headers = authHeaders(token) + mapOf("Content-Type" to "application/octet-stream"),
                    body = bytes
                )
            )
        } ?: throw StoreException("Drive update failed: no response")
        if (response.statusCode !in 200..299) {
            throw StoreException("Drive update failed: HTTP ${response.statusCode}")
        }
    }

    /** One retry with a refreshed token when Drive answers 401. */
    private fun <T> executeWithAuth(call: (String) -> T): T? {
        val token = tokenProvider.accessToken()
        val result = call(token)
        val status = (result as? HttpResponse)?.statusCode
        if (status == 401) {
            tokenProvider.invalidate(token)
            return call(tokenProvider.accessToken())
        }
        return result
    }

    private fun authHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/json"
    )

    private fun buildMultipart(metadata: String, bytes: ByteArray): ByteArray {
        val preamble = ("--$BOUNDARY\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            "$metadata\r\n" +
            "--$BOUNDARY\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n").toByteArray(Charsets.UTF_8)
        val closing = "\r\n--$BOUNDARY--".toByteArray(Charsets.UTF_8)
        return preamble + bytes + closing
    }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /**
     * Extract string members of one field from a flat Drive v3 JSON reply,
     * e.g. all `id` values inside the top-level `files` array. Hand-rolled:
     * the client intentionally ships no JSON dependency for protocol purity,
     * and this parser only ever sees Google's well-formed field output.
     */
    internal fun extractJsonStringArrayField(json: String, arrayField: String, stringField: String): List<String> {
        val arrayStart = json.indexOf("\"$arrayField\"")
        if (arrayStart < 0) return emptyList()
        val index = json.indexOf('[', arrayStart)
        if (index < 0) return emptyList()
        val arrayEnd = json.indexOf(']', index)
        if (arrayEnd < 0) return emptyList()
        val body = json.substring(index + 1, arrayEnd)
        val results = mutableListOf<String>()
        val member = Regex("\"$stringField\"\\s*:\\s*\"([^\"]*)\"")
        for (match in member.findAll(body)) {
            results.add(match.groupValues[1])
            if (results.size > 16) break
        }
        return results
    }
}

/** Minimal HTTP surface so the Drive client is unit-testable off-device. */
interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
)

data class HttpResponse(val statusCode: Int, val body: ByteArray) {
    fun bodyText(): String = String(body, Charsets.UTF_8)
}

/** Production transport over [HttpURLConnection]; platform only, no SDK. */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            for ((name, value) in request.headers) connection.setRequestProperty(name, value)
            request.body?.let { bytes ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    if (output.size() > 8 * 1024 * 1024) throw IOException("Drive response too large")
                }
                output.toByteArray()
            } ?: ByteArray(0)
            return HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}

/** Supplies a Drive-scoped OAuth bearer token. Blocking; call from Dispatchers.IO. */
interface DriveTokenProvider {
    fun accessToken(): String

    /** Drop a possibly expired token so the next [accessToken] refreshes it. */
    fun invalidate(token: String)
}
