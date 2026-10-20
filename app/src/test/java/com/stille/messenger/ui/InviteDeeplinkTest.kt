package com.stille.messenger.ui

import com.stille.messenger.service.validInviteTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invite deeplink parsing: stille.im links carry the full invite code with
 * the `stille://` scheme stripped — no short codes, no site mapping. The
 * parser is pure string logic (JVM-testable, no android.net.Uri).
 */
class InviteDeeplinkTest {

    @Test
    fun parsesFullCodesVerbatim() {
        assertEquals("stille://i/abc123", InviteDeeplinks.parse("stille://i/abc123"))
        assertEquals("stille://i3/abc123", InviteDeeplinks.parse("stille://i3/abc123"))
        assertEquals("shopot://i/abc123", InviteDeeplinks.parse("shopot://i/abc123"))
        assertEquals("shopot1.abc123", InviteDeeplinks.parse("shopot1.abc123"))
        assertEquals("stille://i/abc123", InviteDeeplinks.parse("  stille://i/abc123  "))
    }

    @Test
    fun profileLinksCarryTheFullCode() {
        // The link path is the code without the stille:// scheme.
        assertEquals("stille://i/abc123", InviteDeeplinks.parse("https://stille.im/invite/i/abc123"))
        assertEquals("stille://i3/xyz456", InviteDeeplinks.parse("https://stille.im/invite/i3/xyz456"))
        // Scheme-less form, case-insensitive host.
        assertEquals("stille://i/abc123", InviteDeeplinks.parse("stille.im/invite/i/abc123"))
        assertEquals("stille://i3/xyz456", InviteDeeplinks.parse("STILLE.IM/invite/i3/xyz456"))
        assertEquals("stille://i/abc123", InviteDeeplinks.parse("HTTPS://STILLE.IM/invite/i/abc123"))
        // Legacy v1 codes keep their own scheme-less form.
        assertEquals("shopot1.abc123", InviteDeeplinks.parse("https://stille.im/invite/shopot1.abc123"))
    }

    @Test
    fun rejectsBrokenLinks() {
        assertNull(InviteDeeplinks.parse("https://stille.im/invite/"))            // empty code body
        assertNull(InviteDeeplinks.parse("https://stille.im/invite/i/"))           // empty payload
        assertNull(InviteDeeplinks.parse("https://stille.im/invite/i/abc?x=1"))    // query
        assertNull(InviteDeeplinks.parse("https://stille.im/invite/i/abc#frag"))   // fragment
        assertNull(InviteDeeplinks.parse("https://example.com/invite/i/abc123"))   // wrong host
        assertNull(InviteDeeplinks.parse("http://stille.im/invite/i/abc123"))      // plain http stays rejected
        assertNull(InviteDeeplinks.parse("https://stille.im/other/i/abc123"))      // wrong path
        assertNull(InviteDeeplinks.parse("https://stille.im/invite/abc123"))       // not a known code form
        assertNull(InviteDeeplinks.parse("https://stille.im/invite/N9S5WMD9D"))    // short codes are not links anymore
        assertNull(InviteDeeplinks.parse("stille.im/invite/N9S5WMD9D"))            // scheme-less short code too
        assertNull(InviteDeeplinks.parse(""))
        assertNull(InviteDeeplinks.parse("hello world"))
    }

    @Test
    fun rejectsWhitespaceAndControlCharacters() {
        assertNull(InviteDeeplinks.parse("stille://i/abc 123"))
        assertNull(InviteDeeplinks.parse("https://stille.im/invite/i/ab\tc"))
    }

    @Test
    fun buildsProfileLinksByStrippingTheScheme() {
        assertEquals("https://stille.im/invite/i3/xyz456", InviteDeeplinks.profileLink("stille://i3/xyz456"))
        assertEquals("https://stille.im/invite/i/abc123", InviteDeeplinks.profileLink("stille://i/abc123"))
        // Legacy v1 forms pass through unchanged.
        assertEquals("https://stille.im/invite/shopot1.abc123", InviteDeeplinks.profileLink("shopot1.abc123"))
    }

    @Test
    fun profileLinkRoundTripsThroughParse() {
        for (code in listOf("stille://i/abc123", "stille://i3/xyz456789", "shopot1.abc123")) {
            assertEquals(code, InviteDeeplinks.parse(InviteDeeplinks.profileLink(code)))
            assertEquals(code, InviteDeeplinks.parse(InviteDeeplinks.profileLink(code).removePrefix("https://")))
        }
    }

    @Test
    fun inviteTargetsAcceptCodesAndLinks() {
        assertTrue(validInviteTarget("stille://i/abc123"))
        assertTrue(validInviteTarget("shopot1.abc123"))
        assertTrue(validInviteTarget("https://stille.im/invite/i/abc123"))
        assertTrue(validInviteTarget("stille.im/invite/i3/xyz456"))
        assertFalse(validInviteTarget("N9S5WMD9D"))
        assertFalse(validInviteTarget("https://example.com/invite/i/abc123"))
        assertFalse(validInviteTarget("random text"))
    }
}
