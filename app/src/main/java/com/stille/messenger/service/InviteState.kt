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

internal fun validInviteInput(value: String): Boolean =
    value.length in 10..4096 && (value.startsWith("shopot://i/") || value.startsWith("shopot://i3/") || value.startsWith("shopot1.")) &&
        value.none { it.isWhitespace() || it.isISOControl() }
