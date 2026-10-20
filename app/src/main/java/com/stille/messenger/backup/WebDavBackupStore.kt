package com.stille.messenger.backup

/** Login for a WebDAV-compatible storage (Yandex Disk, Nextcloud, …). */
data class WebDavCredentials(
    /** HTTPS base address, e.g. `https://webdav.yandex.ru`. */
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    class ValidationException(message: String) : Exception(message)

    fun validated(): WebDavCredentials {
        val url = baseUrl.trim()
        if (!url.startsWith("https://")) throw ValidationException("WebDAV address must use https")
        if (url.length > 2048) throw ValidationException("WebDAV address is too long")
        if (url.contains("..") || url.contains('@')) throw ValidationException("WebDAV address is not allowed")
        if (username.isEmpty() || username.length > 256) throw ValidationException("WebDAV username is invalid")
        if (password.isEmpty() || password.length > 256) throw ValidationException("WebDAV password is invalid")
        return WebDavCredentials(
            baseUrl = url.trimEnd('/'),
            username = username,
            password = password,
        )
    }

    /** Authorization header value: `Authorization: Basic base64(user:password)`. */
    internal fun authorization(): String {
        val raw = "$username:$password"
        return "Basic " + java.util.Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
    }
}

/**
 * WebDAV backup storage for providers without an Android account manager —
 * Yandex Disk (`https://webdav.yandex.ru`), Nextcloud/ownCloud and other
 * free servers. Plain HTTPS + Basic auth; no SDK, no telemetry.
 *
 * Same two opaque files as the Drive store:
 * ```
 * /stille-backup/stille-recovery-v1.bin   — the core-sealed recovery blob
 * /stille-backup/stille-recovery-v1.meta  — the public metadata sidecar
 * ```
 * PUT replaces in place, so a failed upload never destroys the last good copy.
 */
class WebDavBackupStore(
    private val transport: HttpTransport,
    private val credentials: WebDavCredentials,
) : BackupStore {
    class StoreException(message: String, val authentication: Boolean = false) : Exception(message)

    override fun upload(blob: ByteArray, sidecar: ByteArray): Boolean {
        if (blob.isEmpty() || blob.size > MAX_BLOB_BYTES) {
            throw StoreException("backup blob size out of range")
        }
        if (sidecar.isEmpty() || sidecar.size > MAX_SIDECAR_BYTES) {
            throw StoreException("sidecar size out of range")
        }
        val replaced = hasBackup()
        ensureDirectory()
        put(FILE_NAME, blob)
        put(SIDECAR_NAME, sidecar)
        return replaced
    }

    override fun download(): BackupStore.Downloaded? {
        val blob = get(FILE_NAME, MAX_BLOB_BYTES) ?: return null
        val sidecar = get(SIDECAR_NAME, MAX_SIDECAR_BYTES)
        return BackupStore.Downloaded(blob, sidecar)
    }

    override fun delete(): Boolean {
        val existed = hasBackup()
        if (existed) transport.execute(HttpRequest(method = "DELETE", url = urlFor(FILE_NAME), headers = authHeaders()))
        return existed
    }

    override fun hasBackup(): Boolean = get(FILE_NAME, MAX_BLOB_BYTES) != null

    // ── internals ────────────────────────────────────────────────────────

    private fun urlFor(name: String): String = credentials.baseUrl + DIRECTORY_PATH + name

    private fun directoryUrl(): String = credentials.baseUrl + DIRECTORY_PATH.trimEnd('/')

    /** MKCOL once; 405 (exists) and 403 (root-only servers) are tolerated. */
    private fun ensureDirectory() {
        val response = transport.execute(
            HttpRequest(method = "MKCOL", url = directoryUrl(), headers = authHeaders())
        )
        when (response.statusCode) {
            in 200..299, 405, 403, 404 -> Unit
            401 -> throw StoreException("WebDAV authentication failed", authentication = true)
            else -> throw StoreException("WebDAV directory unavailable: HTTP ${response.statusCode}")
        }
    }

    private fun put(name: String, bytes: ByteArray) {
        val response = transport.execute(
            HttpRequest(
                method = "PUT",
                url = urlFor(name),
                headers = authHeaders() + mapOf("Content-Type" to "application/octet-stream"),
                body = bytes,
            )
        )
        if (response.statusCode == 401) throw StoreException("WebDAV authentication failed", authentication = true)
        if (response.statusCode !in 200..299) {
            throw StoreException("WebDAV upload failed: HTTP ${response.statusCode}")
        }
    }

    private fun get(name: String, maxSize: Int): ByteArray? {
        val response = transport.execute(
            HttpRequest(method = "GET", url = urlFor(name), headers = authHeaders())
        )
        when (response.statusCode) {
            404 -> return null
            401 -> throw StoreException("WebDAV authentication failed", authentication = true)
        }
        if (response.statusCode != 200) {
            throw StoreException("WebDAV download failed: HTTP ${response.statusCode}")
        }
        if (response.body.size > maxSize) {
            throw StoreException("downloaded file has invalid size")
        }
        return response.body.takeIf { it.isNotEmpty() }
    }

    private fun authHeaders(): Map<String, String> = mapOf(
        "Authorization" to credentials.authorization(),
    )

    companion object {
        const val FILE_NAME = "stille-recovery-v1.bin"
        const val SIDECAR_NAME = "stille-recovery-v1.meta"
        const val DIRECTORY_PATH = "/stille-backup/"
        const val YANDEX_DISK_URL = "https://webdav.yandex.ru"

        private const val MAX_BLOB_BYTES = 96 * 1024      // core cap: 92786 (§4.10)
        private const val MAX_SIDECAR_BYTES = 4 * 1024
    }
}
