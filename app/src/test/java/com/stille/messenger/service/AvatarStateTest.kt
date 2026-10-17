package com.stille.messenger.service

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class AvatarStateTest {
    @Test fun cacheIsBoundedAndOwnAvatarIsPinned() {
        var cache = cacheAvatar(emptyMap(), 0, byteArrayOf(42))
        for (cid in 1..100) cache = cacheAvatar(cache, cid, byteArrayOf(cid.toByte()))
        assertEquals(20, cache.size)
        assertArrayEquals(byteArrayOf(42), cache[0])
        assertFalse(cache.containsKey(1))
        assertTrue(cache.containsKey(100))
    }
    @Test fun replacementRefreshesOrderAndCopiesInput() {
        val input = byteArrayOf(1)
        val cache = cacheAvatar(emptyMap(), 1, input)
        input[0] = 2
        assertArrayEquals(byteArrayOf(1), cache[1])
        val updated = cacheAvatar(cacheAvatar(cache, 2, byteArrayOf(3)), 1, byteArrayOf(4))
        assertEquals(listOf(2, 1), updated.keys.toList())
        assertArrayEquals(byteArrayOf(1), cache[1])
    }
    @Test fun checksContentAddressAndPayloadBounds() {
        val data = ByteArray(16000) { (it % 255).toByte() }
        val id = MessageDigest.getInstance("SHA-256").digest(data).copyOf(16)
        assertTrue(matchesAvatarId(id, data))
        assertFalse(matchesAvatarId(id, data + 1.toByte()))
        assertFalse(matchesAvatarId(id, byteArrayOf()))
        assertFalse(matchesAvatarId(ByteArray(15), data))
        data[0] = 99
        assertFalse(matchesAvatarId(id, data))
    }
    @Test fun rejectsInvalidCachePayloads() {
        for (data in listOf(ByteArray(0), ByteArray(16001))) {
            try { cacheAvatar(emptyMap(), 1, data); fail("Expected bounded rejection") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
