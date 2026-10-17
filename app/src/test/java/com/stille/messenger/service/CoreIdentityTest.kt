package com.stille.messenger.service

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class CoreIdentityTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun absentIdentityNeedsInitialization() {
        assertTrue(needsCoreIdentity(File(temp.root, "missing")))
    }

    @Test fun existingIdentityIsPreserved() {
        val seed = temp.newFile("seed")
        val bytes = ByteArray(32) { it.toByte() }
        seed.writeBytes(bytes)
        assertFalse(needsCoreIdentity(seed))
        assertArrayEquals(bytes, seed.readBytes())
    }

    @Test fun malformedIdentityIsNotRegenerated() {
        for (size in listOf(0, 31, 33)) {
            val seed = temp.newFile("seed-$size")
            val bytes = ByteArray(size)
            seed.writeBytes(bytes)
            assertThrows(IOException::class.java) { needsCoreIdentity(seed) }
            assertArrayEquals(bytes, seed.readBytes())
        }
    }

    @Test fun directoryIsNotAnIdentity() {
        assertThrows(IOException::class.java) { needsCoreIdentity(temp.newFolder("seed")) }
    }
}
