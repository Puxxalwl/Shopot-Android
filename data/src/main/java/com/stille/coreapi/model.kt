package com.stille.coreapi

// ─── Data Models ──────────────────────────────────────────────────────────

data class ConnectionStatus(val state: ConnState, val attempt: Long, val lastError: ConnErr, val nextRetryMs: Long)

data class ContactView(
    val cid: Int, val online: Boolean, val blocked: Boolean, val pending: Boolean,
    val lastSeenMs: Long, val name: String, val avatarId: ByteArray,
    val connection: ConnectionStatus
) {
    fun asContact(): Contact = Contact(cid, online, blocked, lastSeenMs, name, avatarId)
}

data class ContactsSnapshot(val apiVersion: CoreVersion, val epoch: ByteArray, val revision: Long, val contacts: List<ContactView>)
data class ContactDeltaEntry(val revision: Long, val change: DeltaChange, val cid: Int, val contact: ContactView?)
data class ContactsDelta(val epoch: ByteArray, val revision: Long, val entries: List<ContactDeltaEntry>)
data class InviteCode(val code: String, val inviteId: ByteArray)
data class InviteStateView(val state: InviteLifecycleState, val acceptedCid: Int, val expiresAtMs: Long)
data class AcceptInviteResult(val cid: Int, val disposition: AcceptDisposition, val state: ConnState, val attempt: Long)

// ── Short identity codes / persistent profile code (CORE-API 2.8.0 §4.2) ──

/** GENERATE_SHORT_CODE result: cid 0 = own identity, code is the 9-char identifier. */
data class ShortCodeView(val cid: Int, val code: String)

/** DECODE_SHORT_CODE result: cid 0 = own identity; full_code is the v1-format code. */
data class DecodedShortCodeView(val cid: Int, val fullCode: String)

/** REGENERATE_PROFILE_CODE result: epoch bump; persisted=false means no encrypted log (rotation lives until restart). */
data class ProfileCodeRotation(val epoch: Long, val persisted: Boolean)

data class HistoryCursor(val beforeTsMs: Long, val beforeMsgId: ByteArray) {
    init { require(beforeMsgId.size == 16) { "cursor message id must be 16 bytes" } }
}
data class HistoryPage(val hasMore: Boolean, val nextCursor: HistoryCursor?, val messages: List<Message>)

/** A contact in the contact list (CORE-API §4.2 LIST_CONTACTS). */
data class Contact(
    val cid: Int,          // u32
    val online: Boolean,   // bit0
    val blocked: Boolean,  // bit1
    val lastSeenMs: Long,  // i64; 0 = unknown
    val name: String,      // str16 ≤ 32
    val avatarId: ByteArray? = null  // 16 bytes, from GET_AVATAR_ID (separate call)
)

/** A decoded message from GET_HISTORY / GET_MESSAGE / events. */
data class Message(
    val cid: Int,
    val msgId: ByteArray,     // 16 bytes
    val flags: Byte,          // bit0=mine, bit1=delivered
    val tsMs: Long,           // u64, local record time (ms epoch)
    val body: String,         // str16 ≤ 4096
    val entities: List<Entity> = emptyList(),
    val reactions: List<Reaction> = emptyList()  // from GET_MESSAGE only
) {
    val delivered: Boolean get() = (flags.toInt() and 0b10) != 0
    val mine: Boolean get() = (flags.toInt() and 0b01) != 0
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return cid == other.cid && msgId.contentEquals(other.msgId) &&
            tsMs == other.tsMs && body == other.body && entities == other.entities
    }
    override fun hashCode(): Int = msgId.contentHashCode()
}

data class Reaction(
    val mine: Boolean,      // bit0
    val codepoint: Int,     // u32 Unicode codepoint
    val tsMs: Long          // u64
)

/** Search result item. */
data class SearchResult(
    val cid: Int,            // 0 in global search
    val flags: Byte,         // bit0=mine, bit1=delivered, bit2=fuzzy
    val msgId: ByteArray,    // 16 bytes
    val tsMs: Long,          // u64
    val body: String,        // str16
    val entities: List<Entity> = emptyList()
)

/** Avatar data from AVATAR_RECEIVED event. */
data class AvatarData(
    val cid: Int,
    val avatarId: ByteArray,  // 16 bytes
    val data: ByteArray       // ≤16000 bytes (raw image bytes)
)

/** File info from FILE_RECEIVED / FILE_SENT events. */
data class FileInfo(
    val cid: Int,
    val fileId: ByteArray,   // 16 bytes (SHA-256[0..16])
    val size: Int,           // u32
    val name: String         // str16 ≤ 64
)

/** Contact card forwarded by SEND_CONTACT. */
data class ContactCard(
    val cid: Int,            // sender's cid
    val name: String,
    val bundle: ByteArray,   // 64 bytes (enc_pk ‖ sign_pk)
    val addr: String         // str16, empty when unknown
)

/** Call state from CALL_STATE event. */
data class CallState(
    val cid: Int,
    val callId: ByteArray,   // 16 bytes
    val state: CallEvent     // 1=offer, 2=accept, 3=reject, 4=end
)

enum class CallEvent(val value: Int) {
    OFFER(1), ACCEPT(2), REJECT(3), END(4);
    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v }
            ?: throw ProtocolException("unknown call state: $v")
    }
}

/** Core version from GET_VERSION. */
data class CoreVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
)

/** Identity from GET_IDENTITY. */
data class Identity(
    val bundle: ByteArray,      // 64 bytes (enc_pk ‖ sign_pk)
    val fingerprint: ByteArray  // 32 bytes (BLAKE2s-256 of bundle)
)