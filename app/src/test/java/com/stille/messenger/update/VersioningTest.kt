package com.stille.messenger.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersioningTest {
    @Test fun comparesNumericComponents() {
        assertTrue(Versioning.compare("0.2.10", "0.2.9") > 0)
        assertEquals(0, Versioning.compare("v1.2", "1.2.0"))
        assertTrue(Versioning.compare("1.2.0", "1.2.1") < 0)
    }

    @Test fun rejectsMalformedVersions() {
        assertTrue(Versioning.isValid("v0.2.10"))
        assertFalse(Versioning.isValid("v1.x"))
        assertFalse(Versioning.isValid("1..2"))
    }
}
