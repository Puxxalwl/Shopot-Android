package com.stille.messenger.ui

import org.junit.Assert.*
import org.junit.Test

class ProfileNameTest {
    @Test fun validatesByteBoundaries() {
        assertFalse(ProfileName.isValid("   "))
        assertTrue(ProfileName.isValid("a"))
        assertTrue(ProfileName.isValid("a".repeat(32)))
        assertFalse(ProfileName.isValid("a".repeat(33)))
        assertTrue(ProfileName.isValid("я".repeat(16)))
        assertFalse(ProfileName.isValid("я".repeat(17)))
        assertTrue(ProfileName.isValid("😀".repeat(8)))
        assertFalse(ProfileName.isValid("😀".repeat(9)))
    }

    @Test fun truncatesWithoutSplittingCodePoints() {
        assertEquals("я".repeat(16), ProfileName.limitInput("я".repeat(17)))
        assertEquals("😀".repeat(8), ProfileName.limitInput("😀".repeat(9)))
        assertEquals("a".repeat(31), ProfileName.limitInput("a".repeat(31) + "😀"))
        assertEquals("Alice", ProfileName.limitInput("Alice"))
    }
}
