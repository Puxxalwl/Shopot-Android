package com.stille.coreapi

import kotlin.math.min

/**
 * Request payload encoders — one function per method.
 * Each builds the exact byte layout from CORE-API.md §4.
 * Throws [ProtocolException] or [IllegalArgumentException] on bound violations.
 */
object Requests {

    // ── 4.1 Service ──────────────────────────────────────────────────────

    /** GET_VERSION (2): payload is empty ∅. */
    fun getVersion(): ByteArray = byteArrayOf()

    /** GET_IDENTITY (3): payload is empty ∅. */
    fun getIdentity(): ByteArray = byteArrayOf()

    /** CLOSE (1): payload is empty ∅. */
    fun close(): ByteArray = byteArrayOf()

    // ── 4.2 Contacts ────────────────────────────────────────────────────

    /** CREATE_INVITE (4): [flags u8][name str16][addr str16]. */
    fun createInvite(
        name: String,
        addr: String,
        relay: Boolean = false
    ): ByteArray {
        require(name.encodeToByteArray().size <= Limits.INVITE_NAME_MAX) {
            "invite name too long (>32 bytes)"
        }
        require(addr.encodeToByteArray().size <= Limits.INVITE_ADDR_MAX) {
            "invite addr too long (>64 bytes)"
        }
        val fb = FrameBuffer()
        fb.writeU8(if (relay) 0b01 else 0b00)
        fb.writeStr16(if (name.isEmpty()) "shopot" else name)
        fb.writeStr16(addr)
        return fb.toByteArray()
    }

    /** ACCEPT_INVITE (5): [code str16][my_name str16]. */
    fun acceptInvite(code: String, myName: String): ByteArray {
        require(code.encodeToByteArray().size <= 65535) { "invite code too long for str16" }
        require(myName.encodeToByteArray().size <= Limits.INVITE_NAME_MAX) {
            "my_name too long (>32 bytes)"
        }
        val fb = FrameBuffer()
        fb.writeStr16(code)
        fb.writeStr16(if (myName.isEmpty()) "друг" else myName)
        return fb.toByteArray()
    }

    /** LIST_CONTACTS (6): ∅. */
    fun listContacts(): ByteArray = byteArrayOf()

    /** GET_CONTACT_STATE (43): [cid u32]. */
    fun getContactState(cid: Int): ByteArray = encodeU32Payload(cid)

    /** ACCEPT_INVITE_EX (44): [code str16][my_name str16]. */
    fun acceptInviteEx(code: String, myName: String): ByteArray = acceptInvite(code, myName)

    /** ACCEPT_INVITE_EX convenience overload with response-oriented naming. */
    fun acceptFriendRequest(code: String, myName: String): ByteArray = acceptInviteEx(code, myName)

    /** SUBSCRIBE_CONTACT_UPDATES (45): ∅. */
    fun subscribeContactUpdates(): ByteArray = byteArrayOf()

    /** GET_BOOTSTRAP (46): ∅. */
    fun getBootstrap(): ByteArray = byteArrayOf()

    /** GET_CONTACTS_DELTA (47): [epoch 16][from_revision u64]. */
    fun getContactsDelta(epoch: ByteArray, fromRevision: Long): ByteArray {
        require(epoch.size == Limits.CONTACT_EPOCH) { "epoch must be 16 bytes" }
        require(fromRevision >= 0) { "revision must not be negative" }
        return FrameBuffer().apply { writeBytes(epoch); writeU64(fromRevision) }.toByteArray()
    }

    /** GET_REACHABLE_ENDPOINTS (48): ∅. */
    fun getReachableEndpoints(): ByteArray = byteArrayOf()

    /** CREATE_INVITE_EX (49): [mode u8][name str16][direct str16][relay str16]. */
    fun createInviteEx(mode: InviteExMode, name: String, direct: String = "", relay: String = ""): ByteArray {
        require(name.encodeToByteArray().size <= Limits.INVITE_NAME_MAX) { "invite name too long (>32 bytes)" }
        require(direct.encodeToByteArray().size <= Limits.INVITE_ADDR_MAX) { "direct address too long (>64 bytes)" }
        require(relay.encodeToByteArray().size <= Limits.INVITE_ADDR_MAX) { "relay address too long (>64 bytes)" }
        return FrameBuffer().apply { writeU8(mode.value); writeStr16(name); writeStr16(direct); writeStr16(relay) }.toByteArray()
    }

    /** GET_INVITE_STATE (50): [invite_id 16]. */
    fun getInviteState(inviteId: ByteArray): ByteArray {
        require(inviteId.size == Limits.INVITE_ID) { "invite id must be 16 bytes" }
        return inviteId.copyOf()
    }

    /** GET_HISTORY_PAGE (51): cursor-based history request. */
    fun getHistoryPage(cid: Int, cursor: HistoryCursor? = null, limit: Int = 0): ByteArray {
        require(limit in 0..Limits.HISTORY_PAGE_MAX) { "limit exceeds ${Limits.HISTORY_PAGE_MAX}" }
        return FrameBuffer().apply {
            writeU32(cid.toLong()); writeU8(if (cursor == null) 0 else 1)
            writeU64(cursor?.beforeTsMs ?: 0L); writeBytes(cursor?.beforeMsgId ?: ByteArray(16))
            writeU16(limit)
        }.toByteArray()
    }

    // ── 4.3 Messages ────────────────────────────────────────────────────

    /** SEND_MESSAGE (7): [cid u32][body str16 ≤4096][entity tail]. */
    fun sendMessage(
        cid: Int,
        body: String,
        entities: List<Entity> = emptyList()
    ): ByteArray {
        val bodyBytes = body.encodeToByteArray()
        require(bodyBytes.size <= Limits.TEXT_BODY) { "body too long (>4096 bytes)" }
        require(body.isNotEmpty()) { "body must not be empty" }

        val entityTail = encodeEntityTail(entities)
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        fb.writeStr16(body)
        fb.writeBytes(entityTail)
        return fb.toByteArray()
    }

    /** SET_REACTION (8) / REMOVE_REACTION (9). */
    fun reaction(cid: Int, msgId: ByteArray, codepoint: Int, add: Boolean): ByteArray {
        require(msgId.size == 16) { "msg_id must be 16 bytes" }
        require(codepoint in 0..0x10FFFF) { "codepoint out of range: U+${codepoint.toString(16)}" }
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        fb.writeBytes(msgId)
        fb.writeU32(codepoint.toLong())
        return fb.toByteArray()
    }

    /** GET_HISTORY (10): [cid u32][limit u32]. 0 → default 50. */
    fun getHistory(cid: Int, limit: Int = 50): ByteArray {
        require(limit == 0 || limit <= Limits.HISTORY_MAX) { "limit exceeds ${Limits.HISTORY_MAX}" }
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        fb.writeU32(limit.toLong())
        return fb.toByteArray()
    }

    /** GET_MESSAGE (11): [msg_id 16]. */
    fun getMessage(msgId: ByteArray): ByteArray {
        require(msgId.size == 16) { "msg_id must be 16 bytes" }
        val fb = FrameBuffer()
        fb.writeBytes(msgId)
        return fb.toByteArray()
    }

    /** SEARCH_MESSAGE (23): [cid u32][query str16][limit u32][offset u32]. */
    fun searchMessage(cid: Int, query: String, limit: Int = 50, offset: Int = 0): ByteArray {
        require(query.isNotEmpty()) { "query must not be empty" }
        require(query.encodeToByteArray().size <= Limits.SEARCH_QUERY_MAX) { "query too long" }
        require(limit == 0 || limit <= Limits.SEARCH_MAX) { "limit exceeds ${Limits.SEARCH_MAX}" }
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        fb.writeStr16(query)
        fb.writeU32(limit.toLong())
        fb.writeU32(offset.toLong())
        return fb.toByteArray()
    }

    /** GLOBAL_SEARCH_MESSAGE (24): [query str16][limit u32][offset u32]. */
    fun globalSearchMessage(query: String, limit: Int = 50, offset: Int = 0): ByteArray {
        require(query.isNotEmpty()) { "query must not be empty" }
        require(query.encodeToByteArray().size <= Limits.SEARCH_QUERY_MAX) { "query too long" }
        require(limit == 0 || limit <= Limits.SEARCH_MAX) { "limit exceeds ${Limits.SEARCH_MAX}" }
        val fb = FrameBuffer()
        fb.writeStr16(query)
        fb.writeU32(limit.toLong())
        fb.writeU32(offset.toLong())
        return fb.toByteArray()
    }

    // ── 4.4 Own profile ─────────────────────────────────────────────────

    /** SET_NAME (12): [name str16 1..32]. */
    fun setName(name: String): ByteArray {
        require(name.isNotEmpty()) { "name must not be empty" }
        require(name.encodeToByteArray().size <= Limits.DISPLAY_NAME) { "name too long (>32 bytes)" }
        val fb = FrameBuffer()
        fb.writeStr16(name)
        return fb.toByteArray()
    }

    /** SET_AVATAR (13): [data str32 1..16000]. */
    fun setAvatar(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "avatar data must not be empty" }
        require(data.size <= Limits.AVATAR_PAYLOAD) { "avatar too large (>16000 bytes)" }
        val fb = FrameBuffer(4 + data.size)
        fb.writeRaw32(data)
        return fb.toByteArray()
    }

    /** GET_AVATAR_ID (14): [cid u32]. */
    fun getAvatarId(cid: Int): ByteArray {
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        return fb.toByteArray()
    }

    // ── 4.5 Files ───────────────────────────────────────────────────────

    /** SEND_FILE (15): [cid u32][path str16]. */
    fun sendFile(cid: Int, path: String): ByteArray {
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        fb.writeStr16(path)
        return fb.toByteArray()
    }

    // ── 4.6 Calls ───────────────────────────────────────────────────────

    /** START_CALL (16): [cid u32]. */
    fun startCall(cid: Int): ByteArray {
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        return fb.toByteArray()
    }
    /** ANSWER_CALL (17): ∅. */
    fun answerCall(): ByteArray = byteArrayOf()
    /** END_CALL (18): ∅. */
    fun endCall(): ByteArray = byteArrayOf()

    // ── 4.7 Blocking & transport ────────────────────────────────────────

    /** BLOCK_CONTACT (19): [cid u32]. */
    fun blockContact(cid: Int): ByteArray = encodeU32Payload(cid)
    /** UNBLOCK_CONTACT (20): [cid u32]. */
    fun unblockContact(cid: Int): ByteArray = encodeU32Payload(cid)
    /** CONNECT (21): [cid u32][addr str16]. */
    fun connect(cid: Int, addr: String): ByteArray {
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        fb.writeStr16(addr)
        return fb.toByteArray()
    }
    /** LISTEN (22): [addr str16][cid u32]. */
    fun listen(addr: String, cid: Int): ByteArray {
        val fb = FrameBuffer()
        fb.writeStr16(addr)
        fb.writeU32(cid.toLong())
        return fb.toByteArray()
    }

    // ── 4.8 Chats ───────────────────────────────────────────────────────

    /** PIN_CHAT (25): [cid u32]. */
    fun pinChat(cid: Int): ByteArray = encodeU32Payload(cid)
    /** GET_PINNED_CHAT (26): ∅. */
    fun getPinnedChat(): ByteArray = byteArrayOf()
    /** UNPIN_CHAT (27): ∅. */
    fun unpinChat(): ByteArray = byteArrayOf()
    /** DELETE_CHAT (28): [cid u32]. */
    fun deleteChat(cid: Int): ByteArray = encodeU32Payload(cid)
    /** SEND_CONTACT (29): [to_cid u32][contact_cid u32]. 0 = own card. */
    fun sendContact(toCid: Int, contactCid: Int): ByteArray {
        val fb = FrameBuffer()
        fb.writeU32(toCid.toLong())
        fb.writeU32(contactCid.toLong())
        return fb.toByteArray()
    }
    /** PIN_MESSAGE (30): [cid u32][msg_id 16]. */
    fun pinMessage(cid: Int, msgId: ByteArray): ByteArray {
        require(msgId.size == 16) { "msg_id must be 16 bytes" }
        val fb = FrameBuffer()
        fb.writeU32(cid.toLong())
        fb.writeBytes(msgId)
        return fb.toByteArray()
    }
    /** UNPIN_MESSAGE (31): [cid u32][msg_id 16]. */
    fun unpinMessage(cid: Int, msgId: ByteArray): ByteArray = pinMessage(cid, msgId)
    /** GET_PINNED_MESSAGES (32): [cid u32]. */
    fun getPinnedMessages(cid: Int): ByteArray = encodeU32Payload(cid)

    // ── 4.4 Own avatar (2.5.0) / 4.10 Account recovery (2.6.0) ──────────

    /** GET_MY_AVATAR (40): ∅. Response: [avatar_id 16][data str32]. */
    fun getMyAvatar(): ByteArray = byteArrayOf()

    /** CREATE_RECOVERY_BACKUP (41): [unlock_secret 32]. */
    fun createRecoveryBackup(unlockSecret: ByteArray): ByteArray {
        require(unlockSecret.size == Limits.RECOVERY_SECRET) { "unlock secret must be 32 bytes" }
        return unlockSecret.copyOf()
    }

    /** RESTORE_RECOVERY_BACKUP (42): [backup str32][unlock_secret 32]. */
    fun restoreRecoveryBackup(backup: ByteArray, unlockSecret: ByteArray): ByteArray {
        require(backup.isNotEmpty()) { "backup blob must not be empty" }
        require(backup.size <= Limits.RECOVERY_BLOB) { "backup blob exceeds the package cap" }
        require(unlockSecret.size == Limits.RECOVERY_SECRET) { "unlock secret must be 32 bytes" }
        val fb = FrameBuffer(4 + backup.size + Limits.RECOVERY_SECRET)
        fb.writeRaw32(backup)
        fb.writeBytes(unlockSecret)
        return fb.toByteArray()
    }

    private fun encodeU32Payload(v: Int): ByteArray {
        val fb = FrameBuffer()
        fb.writeU32(v.toLong())
        return fb.toByteArray()
    }
}
