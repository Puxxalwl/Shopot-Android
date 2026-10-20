package com.stille.messenger.service

import org.junit.Assert.*
import org.junit.Test

class InviteStateTest {
    @Test fun acceptsOnlyBoundedSupportedCodes() {
        assertTrue(validInviteInput("stille://i/abc123"))
        assertTrue(validInviteInput("stille://i3/abc123"))
        assertTrue(validInviteInput("shopot://i/abc123"))
        assertTrue(validInviteInput("shopot://i3/abc123"))
        assertTrue(validInviteInput("shopot1.abc123"))
        assertFalse(validInviteInput("https://example.org/invite"))
        assertFalse(validInviteInput("shopot://i/a\nb"))
        assertFalse(validInviteInput("stille://i/" + "x".repeat(4096)))
        assertFalse(validInviteInput(""))
    }
    @Test fun defaultStateNeverPretendsCoreReady() {
        val state = InviteState()
        assertFalse(state.ready)
        assertFalse(state.joined)
        assertNull(state.code)
    }
}
