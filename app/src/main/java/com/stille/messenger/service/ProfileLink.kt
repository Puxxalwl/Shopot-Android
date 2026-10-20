package com.stille.messenger.service

/**
 * State of the permanent invite link (CORE-API 2.8.0): the stille.im URL
 * that carries the persistent profile code (CREATE_INVITE_EX mode 4) with
 * the `stille://` scheme stripped — the link alone is the complete invite,
 * so it works for anyone who receives it and the site holds no mapping.
 * Memory-only: the code is deterministic, so nothing needs persisting —
 * re-issuing the calls after a daemon restart returns the same values.
 */
data class ProfileLinkState(
    val busy: Boolean = false,
    /** https://stille.im/invite/{code without the stille:// scheme}. */
    val link: String? = null,
    /** Full persistent profile code backing the link (stille://i/… or i3/…). */
    val profileCode: String? = null,
    /** True after a successful REGENERATE_PROFILE_CODE + re-issue. */
    val rotated: Boolean = false,
    /** Epoch persistence flag from the last rotation; null before any. */
    val persisted: Boolean? = null,
    val error: ProfileLinkError? = null
)

enum class ProfileLinkError { CORE_UNAVAILABLE, CORE_TOO_OLD, REQUEST_FAILED }
