package com.stille.messenger.backup

import java.security.SecureRandom

/**
 * Public metadata sidecar stored next to the opaque recovery blob in the
 * user's cloud (CLIENT_RULES.md §4.2: PRF salt, creation date, API version).
 *
 * The sidecar is NOT secret: the blob itself is sealed by the core
 * (ChaCha20-Poly1305, key derived from the unlock secret) and is never
 * parsed by this client.
 *
 * Layout (all integers little-endian):
 * ```
 * 0   4   magic "STME"
 * 4   1   version = 1
 * 5   1   key_source: 1 = passkey PRF, 2 = recovery code
 * 6   1   prf_salt_len (32 when key_source = 1, otherwise 0)
 * 7   ..  prf_salt
 * ..  8   created_ms
 * ..  str16 api_version (e.g. "2.6.0")
 * ```
 */
object RecoveryMetadata {
    val MAGIC = byteArrayOf('S'.code.toByte(), 'T'.code.toByte(), 'M'.code.toByte(), 'E'.code.toByte())
    const val VERSION: Int = 1
    const val PRF_SALT_SIZE = 32

    const val KEY_SOURCE_PASSKEY: Int = 1
    const val KEY_SOURCE_RECOVERY_CODE: Int = 2

    data class Metadata(
        val keySource: Int,
        val prfSalt: ByteArray?,     // 32 bytes, only for the passkey source
        val createdMs: Long,
        val apiVersion: String
    ) {
        override fun equals(other: Any?): Boolean =
            other is Metadata && other.keySource == keySource && other.createdMs == createdMs &&
                other.apiVersion == apiVersion &&
                (other.prfSalt == null) == (prfSalt == null) &&
                (other.prfSalt == null || other.prfSalt.contentEquals(prfSalt!!))

        override fun hashCode(): Int = (keySource * 31 + createdMs).toInt()
    }

    class MetadataException(message: String) : Exception(message)

    fun randomPrfSalt(): ByteArray = ByteArray(PRF_SALT_SIZE).also { SecureRandom().nextBytes(it) }

    fun encode(metadata: Metadata): ByteArray {
        val salt = metadata.prfSalt
        val version = metadata.apiVersion.toByteArray(Charsets.UTF_8)
        if (version.size > 65535) throw MetadataException("api version too long")
        val saltLen = salt?.size ?: 0
        if (metadata.keySource == KEY_SOURCE_PASSKEY && saltLen != PRF_SALT_SIZE) {
            throw MetadataException("passkey source requires a 32-byte salt")
        }
        if (metadata.keySource != KEY_SOURCE_PASSKEY && saltLen != 0) {
            throw MetadataException("recovery-code source must not carry a salt")
        }
        val out = ByteArray(7 + saltLen + 8 + 2 + version.size)
        MAGIC.copyInto(out, 0)
        out[4] = VERSION.toByte()
        out[5] = metadata.keySource.toByte()
        out[6] = saltLen.toByte()
        salt?.copyInto(out, 7)
        writeInt64LE(out, 7 + saltLen, metadata.createdMs)
        out[7 + saltLen + 8] = (version.size and 0xFF).toByte()
        out[7 + saltLen + 9] = ((version.size shr 8) and 0xFF).toByte()
        version.copyInto(out, 7 + saltLen + 10)
        return out
    }

    fun decode(bytes: ByteArray): Metadata {
        if (bytes.size < 17) throw MetadataException("metadata too short")
        if (!MAGIC.contentEquals(bytes.copyOfRange(0, 4))) throw MetadataException("not a Stille recovery sidecar")
        val version = bytes[4].toInt() and 0xFF
        if (version != VERSION) throw MetadataException("unsupported sidecar version $version")
        val keySource = bytes[5].toInt() and 0xFF
        if (keySource != KEY_SOURCE_PASSKEY && keySource != KEY_SOURCE_RECOVERY_CODE) {
            throw MetadataException("unknown key source $keySource")
        }
        val saltLen = bytes[6].toInt() and 0xFF
        if (saltLen != 0 && saltLen != PRF_SALT_SIZE) throw MetadataException("invalid salt length")
        if (keySource == KEY_SOURCE_PASSKEY && saltLen != PRF_SALT_SIZE) {
            throw MetadataException("passkey sidecar without a salt")
        }
        if (keySource == KEY_SOURCE_RECOVERY_CODE && saltLen != 0) {
            throw MetadataException("recovery-code sidecar must not carry a salt")
        }
        val versionLen = (bytes[7 + saltLen + 8].toInt() and 0xFF) or
            ((bytes[7 + saltLen + 9].toInt() and 0xFF) shl 8)
        val end = 7 + saltLen + 10 + versionLen
        if (end != bytes.size) throw MetadataException("truncated sidecar")
        return Metadata(
            keySource = keySource,
            prfSalt = if (saltLen == 0) null else bytes.copyOfRange(7, 7 + PRF_SALT_SIZE),
            createdMs = readInt64LE(bytes, 7 + saltLen),
            apiVersion = String(bytes, 7 + saltLen + 10, versionLen, Charsets.UTF_8)
        )
    }

    private fun writeInt64LE(target: ByteArray, offset: Int, value: Long) {
        var v = value
        for (i in 0 until 8) {
            target[offset + i] = (v and 0xFF).toByte()
            v = v shr 8
        }
    }

    private fun readInt64LE(source: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 7 downTo 0) {
            value = (value shl 8) or (source[offset + i].toLong() and 0xFF)
        }
        return value
    }
}

/**
 * Recovery code: the fallback unlock secret (CLIENT_RULES.md §4.1). Random
 * 32 bytes generated at enrollment, shown to the user exactly once as
 * grouped hex, never stored by the client.
 */
object RecoveryCode {
    const val SIZE = 32

    fun generate(): ByteArray = ByteArray(SIZE).also { SecureRandom().nextBytes(it) }

    /** 64 hex chars in 8 groups of 8, e.g. `A1B2C3D4 E5F6...`. */
    fun render(code: ByteArray): String {
        require(code.size == SIZE) { "recovery code must be 32 bytes" }
        return buildString {
            code.forEachIndexed { index, byte ->
                if (index > 0 && index % 4 == 0) append(' ')
                append("0123456789ABCDEF"[(byte.toInt() shr 4) and 0xF])
                append("0123456789ABCDEF"[byte.toInt() and 0xF])
            }
        }
    }

    /** Parse the rendered form back to bytes; tolerant of case and spacing. */
    fun parse(text: String): ByteArray? {
        val hex = text.filterNot { it.isWhitespace() || it == '-' }
        if (hex.length != SIZE * 2) return null
        val out = ByteArray(SIZE)
        for (i in 0 until SIZE) {
            val hi = hex[i * 2].digitToIntOrNull(16) ?: return null
            val lo = hex[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
