package com.stille.messenger.ui.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveQrScannerTest {
    @Test
    fun acceptsSupportedInvitePrefixes() {
        assertTrue(isStilleInvite("shopot://i/abc123"))
        assertTrue(isStilleInvite("shopot1.abc123"))
    }

    @Test
    fun rejectsNonInviteAndMalformedValues() {
        assertFalse(isStilleInvite("https://example.test/invite"))
        assertFalse(isStilleInvite("shopot://i/abc 123"))
        assertFalse(isStilleInvite("shopot1.abc\n123"))
        assertFalse(isStilleInvite("shopot://i/"))
    }
}
