package com.stille.messenger.ui

import com.stille.messenger.service.INVITE_PREFIXES
import com.stille.messenger.service.validInviteInput

/**
 * Invite deeplinks (API 2.8.0 client guidance): a `stille.im` invite link
 * carries the **full invite code itself** — the code with the `stille://`
 * scheme stripped, so the link alone connects people and the site never
 * holds any mapping or bundle:
 * - `https://stille.im/invite/i/…` → code `stille://i/…` (v2)
 * - `https://stille.im/invite/i3/…` → code `stille://i3/…` (v3 hybrid)
 * - `https://stille.im/invite/shopot1.…` → the legacy v1 code as-is
 * The scheme-less `stille.im/invite/…` form is accepted too (the `https://`
 * prefix is optional); the host is matched case-insensitively. Direct
 * `stille://…` / `shopot://…` / `shopot1.…` codes pass through verbatim.
 *
 * Pure string parsing: no android.net.Uri so the logic stays JVM-testable.
 */
object InviteDeeplinks {
    const val PROFILE_HOST = "stille.im"
    private const val INVITE_PATH = "/invite/"
    const val PROFILE_LINK_BASE = "https://$PROFILE_HOST$INVITE_PATH"

    /**
     * The shareable invite link for a full code: the `stille://` scheme is
     * stripped, everything else (including the v1 `shopot1.` form) is kept
     * verbatim so [parse] can reconstruct the exact code.
     */
    fun profileLink(code: String): String =
        PROFILE_LINK_BASE + code.removePrefix("stille://")

    /**
     * Parses deeplink intent data, scanned QR text or pasted input into the
     * full invite code, or null when nothing recognized comes out. Trims
     * surrounding whitespace; rejects inner whitespace, control characters,
     * queries and fragments. The core remains the authority on validity.
     */
    fun parse(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 4096) return null
        if (trimmed.any { it.isWhitespace() || it.isISOControl() }) return null

        // Full invite code: prefix plus a non-empty payload (stille://, legacy
        // shopot:// or shopot1. v1 form).
        if (validInviteInput(trimmed) && INVITE_PREFIXES.any { trimmed.startsWith(it) && trimmed.length > it.length }) {
            return trimmed
        }

        // https://stille.im/invite/… or the scheme-less stille.im/invite/…
        val httpsPrefix = "https://"
        val hostAndPath = "$PROFILE_HOST$INVITE_PATH"
        val rest = when {
            trimmed.regionMatches(0, httpsPrefix, 0, httpsPrefix.length, ignoreCase = true) -> trimmed.substring(httpsPrefix.length)
            trimmed.regionMatches(0, hostAndPath, 0, hostAndPath.length, ignoreCase = true) -> trimmed
            else -> return null
        }
        if (!rest.regionMatches(0, hostAndPath, 0, hostAndPath.length, ignoreCase = true)) return null
        val codeBody = rest.substring(hostAndPath.length)
        if (codeBody.isEmpty() || codeBody.any { it == '?' || it == '#' }) return null

        // Reconstruct the code: v2/v3 bodies restore the stille:// scheme;
        // v1 (shopot1.) and other already-scheme-less forms pass as-is.
        val code = when {
            codeBody.startsWith("i/") || codeBody.startsWith("i3/") -> "stille://$codeBody"
            codeBody.startsWith("shopot://i/") || codeBody.startsWith("shopot://i3/") || codeBody.startsWith("shopot1.") -> codeBody
            else -> return null
        }
        return if (validInviteInput(code) && INVITE_PREFIXES.any { code.startsWith(it) && code.length > it.length }) code else null
    }
}
