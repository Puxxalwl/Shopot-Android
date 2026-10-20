package com.stille.coreapi

/**
 * Response payload decoders — one function per method.
 * Decodes the `payload` from the RESPONSE frame (status already checked).
 */
object Responses {

    // ── 4.1 Service ──────────────────────────────────────────────────────

    fun decodeGetVersion(payload: ByteArray): CoreVersion {
        val r = FrameReader(payload)
        return CoreVersion(
            major = r.readU16(),
            minor = r.readU16(),
            patch = r.readU16()
        )
    }

    fun decodeGetIdentity(payload: ByteArray): Identity {
        val r = FrameReader(payload)
        val bundle = r.readBytes(64)
        val fingerprint = r.readBytes(32)
        return Identity(bundle, fingerprint)
    }

    // ── 4.2 Contacts ────────────────────────────────────────────────────

    fun decodeCreateInvite(payload: ByteArray): String {
        val r = FrameReader(payload)
        return r.readStr16()  // the one-way invite code
    }

    fun decodeAcceptInvite(payload: ByteArray): Int {
        val r = FrameReader(payload)
        return r.readU32().toInt()  // cid
    }

    fun decodeAcceptInviteEx(payload: ByteArray): AcceptInviteResult {
        val r = FrameReader(payload)
        val result = AcceptInviteResult(
            cid = r.readU32().toInt(),
            disposition = AcceptDisposition.fromValue(r.readU8()),
            state = ConnState.fromValue(r.readU8()),
            attempt = r.readU32()
        )
        if (r.remaining != 0) throw ProtocolException("trailing accept invite response bytes")
        return result
    }

    fun decodeCreateInviteEx(payload: ByteArray): InviteCode {
        val r = FrameReader(payload)
        val result = InviteCode(r.readStr16(), r.readBytes(Limits.INVITE_ID))
        if (r.remaining != 0) throw ProtocolException("trailing create invite response bytes")
        return result
    }

    fun decodeInviteState(payload: ByteArray): InviteStateView {
        val r = FrameReader(payload)
        val result = InviteStateView(InviteLifecycleState.fromValue(r.readU8()), r.readU32().toInt(), r.readI64())
        if (r.remaining != 0) throw ProtocolException("trailing invite state response bytes")
        return result
    }

    fun decodeContactState(payload: ByteArray): ConnectionStatus {
        if (payload.size != 11) throw ProtocolException("invalid contact state payload size")
        return decodeConnectionStatus(FrameReader(payload))
    }

    fun decodeBootstrap(payload: ByteArray): ContactsSnapshot {
        val r = FrameReader(payload)
        val version = CoreVersion(r.readU16(), r.readU16(), r.readU16())
        val epoch = r.readBytes(Limits.CONTACT_EPOCH)
        val revision = r.readU64()
        val count = boundedCount(r.readU32(), 10_000)
        val contacts = buildList<ContactView>(count) { repeat(count) { add(decodeContactView(r)) } }
        if (r.remaining != 0) throw ProtocolException("trailing bootstrap bytes")
        return ContactsSnapshot(version, epoch, revision, contacts)
    }

    fun decodeContactsDelta(payload: ByteArray): ContactsDelta {
        val r = FrameReader(payload)
        val epoch = r.readBytes(Limits.CONTACT_EPOCH)
        val revision = r.readU64()
        val count = boundedCount(r.readU32(), 10_000)
        val changes = buildList<ContactDeltaEntry>(count) {
            repeat(count) {
                val itemRevision = r.readU64()
                val change = when (r.readU8()) {
                    0 -> DeltaChange.UPSERT
                    1 -> DeltaChange.REMOVED
                    else -> throw ProtocolException("unknown contact delta kind")
                }
                val cid = r.readU32().toInt()
                val contact = if (change == DeltaChange.UPSERT) decodeContactView(r, cid) else null
                add(ContactDeltaEntry(itemRevision, change, cid, contact))
            }
        }
        if (r.remaining != 0) throw ProtocolException("trailing contacts delta bytes")
        return ContactsDelta(epoch, revision, changes)
    }

    fun decodeReachableEndpoints(payload: ByteArray): List<String> {
        val r = FrameReader(payload)
        val count = r.readU8()
        if (count > Limits.REACHABLE_ENDPOINTS_MAX) throw ProtocolException("too many endpoints")
        val endpoints = List(count) { r.readStr16().also { value ->
            if (value.encodeToByteArray().size > Limits.INVITE_ADDR_MAX) throw ProtocolException("endpoint too long")
        } }
        if (r.remaining != 0) throw ProtocolException("trailing endpoints bytes")
        return endpoints
    }

    private fun decodeContactView(r: FrameReader, expectedCid: Int? = null): ContactView {
        val cid = r.readU32().toInt()
        if (expectedCid != null && cid != expectedCid) throw ProtocolException("contact delta id mismatch")
        val flags = r.readU8()
        val lastSeen = r.readI64()
        val name = r.readStr16()
        val avatarId = r.readBytes(16)
        val connection = decodeConnectionStatus(r)
        return ContactView(cid, flags and 1 != 0, flags and 2 != 0, flags and 4 != 0, lastSeen, name, avatarId, connection)
    }

    private fun decodeConnectionStatus(r: FrameReader): ConnectionStatus = ConnectionStatus(
        ConnState.fromValue(r.readU8()), r.readU32(), ConnErr.fromValue(r.readU16()), r.readU32()
    )

    private fun boundedCount(value: Long, maximum: Int): Int {
        if (value > maximum) throw ProtocolException("response count exceeds limit")
        return value.toInt()
    }

    fun decodeHistoryPage(payload: ByteArray, cid: Int): HistoryPage {
        val r = FrameReader(payload)
        val hasMore = r.readU8() != 0
        val nextTs = r.readU64()
        val nextId = r.readBytes(16)
        val count = boundedCount(r.readU32(), Limits.HISTORY_PAGE_MAX)
        val messages = buildList<Message>(count) {
            repeat(count) {
                val flags = r.readByte()
                val msgId = r.readBytes(16)
                val ts = r.readU64()
                val body = r.readStr16()
                add(Message(cid, msgId, flags, ts, body, decodeEntityTail(r)))
            }
        }
        if (r.remaining != 0) throw ProtocolException("trailing history page bytes")
        val cursor = if (hasMore && count > 0) HistoryCursor(nextTs, nextId) else null
        return HistoryPage(hasMore, cursor, messages)
    }

    fun decodeListContacts(payload: ByteArray): List<Contact> {
        val r = FrameReader(payload)
        val count = boundedCount(r.readU32(), 10_000)
        return buildList {
            repeat(count) {
                val cid = r.readU32().toInt()
                val flags = r.readU8()
                val online = (flags and 0b01) != 0
                val blocked = (flags and 0b10) != 0
                val lastSeen = r.readI64()
                val name = r.readStr16()
                add(Contact(cid, online, blocked, lastSeen, name))
            }
        }
    }

    // ── 4.3 Messages ────────────────────────────────────────────────────

    /** SEND_MESSAGE response: [msg_id 16][flags u8 (bit0 = queued)]. */
    fun decodeSendMessage(payload: ByteArray): Pair<ByteArray, Boolean> {
        val r = FrameReader(payload)
        val msgId = r.readBytes(16)
        val flags = r.readU8()
        val queued = (flags and 0b01) != 0
        return msgId to queued
    }

    fun decodeGetHistory(payload: ByteArray): HistoryBatch {
        val r = FrameReader(payload)
        val total = r.readU32().toInt()
        val count = r.readU32().toInt()
        val messages = buildList {
            repeat(count) {
                val flags = r.readByte()
                val msgId = r.readBytes(16)
                val tsMs = r.readU64()
                val body = r.readStr16()
                val entities = decodeEntityTail(r)
                add(Message(0, msgId, flags, tsMs, body, entities))
            }
        }
        return HistoryBatch(total, messages)
    }

    data class HistoryBatch(
        val total: Int,
        val messages: List<Message>
    )

    fun decodeGetMessage(payload: ByteArray, cid: Int): MessageWithReactions {
        val r = FrameReader(payload)
        val flags = r.readByte()
        val msgId = r.readBytes(16)
        val tsMs = r.readU64()
        val body = r.readStr16()
        val entities = decodeEntityTail(r)
        val reactCount = r.readU32().toInt()
        val reactions = buildList {
            repeat(reactCount) {
                val rflags = r.readU8()
                val mine = (rflags and 0b01) != 0
                val codepoint = r.readU32().toInt()
                val rts = r.readU64()
                add(Reaction(mine, codepoint, rts))
            }
        }
        return MessageWithReactions(
            Message(cid, msgId, flags, tsMs, body, entities, reactions),
            reactCount
        )
    }

    data class MessageWithReactions(
        val message: Message,
        val reactionCount: Int
    )

    fun decodeSearch(payload: ByteArray, isGlobal: Boolean): SearchBatch {
        val r = FrameReader(payload)
        val total = r.readU32().toInt()
        val count = r.readU32().toInt()
        val results = buildList {
            repeat(count) {
                val cid = if (isGlobal) r.readU32().toInt() else 0
                val flags = r.readByte()
                val msgId = r.readBytes(16)
                val tsMs = r.readU64()
                val body = r.readStr16()
                val entities = decodeEntityTail(r)
                add(SearchResult(cid, flags, msgId, tsMs, body, entities))
            }
        }
        return SearchBatch(total, results)
    }

    data class SearchBatch(
        val total: Int,
        val results: List<SearchResult>
    )

    // ── 4.4 Profile ─────────────────────────────────────────────────────

    fun decodeSetAvatar(payload: ByteArray): Pair<ByteArray, Int> {
        val r = FrameReader(payload)
        val avatarId = r.readBytes(16)
        val size = r.readU32().toInt()
        return avatarId to size
    }

    fun decodeGetAvatarId(payload: ByteArray): ByteArray {
        return FrameReader(payload).readBytes(16)  // all zeros = no avatar
    }

    // ── 4.5 Files ───────────────────────────────────────────────────────

    fun decodeSendFile(payload: ByteArray): FileMeta {
        val r = FrameReader(payload)
        val fileId = r.readBytes(16)
        val size = r.readU32().toInt()
        val chunks = r.readU32().toInt()
        return FileMeta(fileId, size, chunks)
    }

    data class FileMeta(
        val fileId: ByteArray,  // 16 bytes
        val size: Int,          // u32
        val chunks: Int         // u32
    )

    // ── 4.6 Calls ───────────────────────────────────────────────────────

    fun decodeStartCall(payload: ByteArray): ByteArray {
        return FrameReader(payload).readBytes(16)  // call_id
    }

    // ── 4.8 Chats ───────────────────────────────────────────────────────

    fun decodeGetPinnedChat(payload: ByteArray): Int {
        return FrameReader(payload).readU32().toInt()  // cid, 0 = none
    }

    fun decodeGetPinnedMessages(payload: ByteArray): List<ByteArray> {
        val r = FrameReader(payload)
        val count = r.readU32().toInt()
        return buildList {
            repeat(count) {
                add(r.readBytes(16))
            }
        }
    }

    // ── 4.4 Own avatar (2.5.0) / 4.10 Account recovery (2.6.0) ──────────

    /** GET_MY_AVATAR (40): [avatar_id 16][data str16 1..16000]. */
    fun decodeGetMyAvatar(payload: ByteArray): Pair<ByteArray, ByteArray> {
        val r = FrameReader(payload)
        val avatarId = r.readBytes(16)
        val data = r.readBytes(r.readU16().toInt())
        return avatarId to data
    }

    /** CREATE_RECOVERY_BACKUP (41): [backup str32] — opaque blob, never parsed. */
    fun decodeCreateRecoveryBackup(payload: ByteArray): ByteArray {
        return FrameReader(payload).readRaw32()
    }

    /**
     * RESTORE_RECOVERY_BACKUP (42): [bundle 64][fingerprint 32][name str16].
     * Same shape as GET_IDENTITY plus the restored profile name.
     */
    fun decodeRestoreRecoveryBackup(payload: ByteArray): RestoredProfile {
        val r = FrameReader(payload)
        val bundle = r.readBytes(64)
        val fingerprint = r.readBytes(32)
        val name = r.readStr16()
        return RestoredProfile(bundle, fingerprint, name)
    }

    data class RestoredProfile(
        val bundle: ByteArray,      // 64 bytes: enc_pk ‖ sign_pk
        val fingerprint: ByteArray, // 32 bytes: BLAKE2s-256(bundle)
        val name: String            // 1..32 UTF-8 bytes
    )
}
