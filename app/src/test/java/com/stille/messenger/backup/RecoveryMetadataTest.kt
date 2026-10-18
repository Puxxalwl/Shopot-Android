package com.stille.messenger.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryMetadataTest {

    private val salt = ByteArray(32) { (it + 3).toByte() }

    private fun passkeySidecar() = RecoveryMetadata.Metadata(
        keySource = RecoveryMetadata.KEY_SOURCE_PASSKEY,
        prfSalt = salt,
        createdMs = 1_700_000_123_456,
        apiVersion = "2.6.0"
    )

    private fun codeSidecar() = RecoveryMetadata.Metadata(
        keySource = RecoveryMetadata.KEY_SOURCE_RECOVERY_CODE,
        prfSalt = null,
        createdMs = 1_700_000_123_456,
        apiVersion = "2.6.0"
    )

    @Test
    fun `sidecar roundtrips for the passkey source`() {
        val bytes = RecoveryMetadata.encode(passkeySidecar())
        // 7 fixed + 32 salt + 8 created + 2 len + 5 version
        assertEquals(7 + 32 + 8 + 2 + 5, bytes.size)
        val decoded = RecoveryMetadata.decode(bytes)
        assertEquals(RecoveryMetadata.KEY_SOURCE_PASSKEY, decoded.keySource)
        assertArrayEquals(salt, decoded.prfSalt)
        assertEquals(1_700_000_123_456, decoded.createdMs)
        assertEquals("2.6.0", decoded.apiVersion)
    }

    @Test
    fun `sidecar roundtrips for the recovery-code source`() {
        val bytes = RecoveryMetadata.encode(codeSidecar())
        assertEquals(7 + 8 + 2 + 5, bytes.size)
        val decoded = RecoveryMetadata.decode(bytes)
        assertEquals(RecoveryMetadata.KEY_SOURCE_RECOVERY_CODE, decoded.keySource)
        assertNull(decoded.prfSalt)
    }

    @Test
    fun `decode rejects foreign or corrupt data`() {
        assertThrows(RecoveryMetadata.MetadataException::class.java) {
            RecoveryMetadata.decode(ByteArray(40))
        }
        val good = RecoveryMetadata.encode(codeSidecar())
        // Wrong magic.
        val bad = good.copyOf().also { it[0] = 'X'.code.toByte() }
        assertThrows(RecoveryMetadata.MetadataException::class.java) { RecoveryMetadata.decode(bad) }
        // Truncation.
        assertThrows(RecoveryMetadata.MetadataException::class.java) { RecoveryMetadata.decode(good.copyOf(10)) }
        // Trailing junk is not accepted either.
        assertThrows(RecoveryMetadata.MetadataException::class.java) {
            RecoveryMetadata.decode(good + ByteArray(3))
        }
    }

    @Test
    fun `encode enforces the source-salt contract`() {
        assertThrows(RecoveryMetadata.MetadataException::class.java) {
            RecoveryMetadata.encode(passkeySidecar().copy(prfSalt = ByteArray(31)))
        }
        assertThrows(RecoveryMetadata.MetadataException::class.java) {
            RecoveryMetadata.encode(codeSidecar().copy(prfSalt = salt))
        }
    }

    @Test
    fun `random salts are 32 bytes and unique per call`() {
        val first = RecoveryMetadata.randomPrfSalt()
        val second = RecoveryMetadata.randomPrfSalt()
        assertEquals(32, first.size)
        assertFalse(first.contentEquals(second))
    }
}

class RecoveryCodeTest {

    @Test
    fun `code is 32 random bytes and render-parse roundtrips`() {
        val code = RecoveryCode.generate()
        assertEquals(32, code.size)
        val rendered = RecoveryCode.render(code)
        assertEquals(64 + 7, rendered.length) // 64 hex chars + 7 group separators (every 8th)
        assertEquals(" ", rendered[8].toString())
        assertArrayEquals(code, RecoveryCode.parse(rendered))
    }

    @Test
    fun `parse tolerates case and separator variants`() {
        val code = RecoveryCode.generate()
        val loose = RecoveryCode.render(code)
            .replace(" ", "-")
            .lowercase()
        assertArrayEquals(code, RecoveryCode.parse(loose))
    }

    @Test
    fun `parse rejects wrong length or non-hex input`() {
        assertNull(RecoveryCode.parse("ABCDEF"))
        assertNull(RecoveryCode.parse("Z".repeat(64)))
        assertNull(RecoveryCode.parse("0".repeat(63)))
        assertNull(RecoveryCode.parse(""))
    }

    @Test
    fun `rendered codes are distinguishable`() {
        assertNotEquals(RecoveryCode.render(RecoveryCode.generate()), RecoveryCode.render(RecoveryCode.generate()))
        assertTrue(RecoveryCode.render(ByteArray(32) { 0 }).startsWith("00000000"))
    }
}
