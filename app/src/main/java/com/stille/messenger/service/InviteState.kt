package com.stille.messenger.service

/** Invite secrets are process-memory state only: never save to Bundles, files or logs. */
data class InviteState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val code: String? = null,
    val inviteId: ByteArray? = null,
    val lifecycle: com.stille.coreapi.InviteLifecycleState? = null,
    val acceptedCid: Int? = null,
    val connection: com.stille.coreapi.ConnectionStatus? = null,
    val accepted: Boolean = false,
    val joined: Boolean = false,
    val error: InviteError? = null
)

enum class InviteError {
    CORE_UNAVAILABLE, INVALID_CODE, LIMIT_REACHED, RESYNC_REQUIRED,
    DISCOVERY_DISABLED, TRANSPORT_FAILED, RELAY_UNAVAILABLE, TOR_UNAVAILABLE, REQUEST_FAILED
}

/** Invite code prefixes: the core emits stille:// (v2/v3) and still accepts legacy shopot:// and shopot1. */
internal val INVITE_PREFIXES = listOf("stille://i/", "stille://i3/", "shopot://i/", "shopot://i3/", "shopot1.")

internal fun validInviteInput(value: String): Boolean =
    value.length in 10..4096 && INVITE_PREFIXES.any { value.startsWith(it) } &&
        value.none { it.isWhitespace() || it.isISOControl() }
