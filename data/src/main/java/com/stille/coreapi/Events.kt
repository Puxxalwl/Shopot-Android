package com.stille.coreapi

/**
 * Event payload decoders — one function per event type.
 * Events arrive as EVENT frames (kind=3, request_id=0).
 */
object Events {

    fun decodeMessageReceived(payload: ByteArray): MessageReceivedEvent {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val msgId = r.readBytes(16)
        val tsMs = r.readU64()
        val name = r.readStr16()
        val body = r.readStr16()
        val entities = decodeEntityTail(r)
        val flags = 0.toByte()  // incoming — not mine, not delivered
        return MessageReceivedEvent(
            message = Message(cid, msgId, flags, tsMs, body, entities),
            senderName = name
        )
    }

    fun decodeMessageDelivered(payload: ByteArray): MessageDeliveredEvent {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val msgId = r.readBytes(16)
        return MessageDeliveredEvent(cid, msgId)
    }

    fun decodePresence(payload: ByteArray): PresenceEvent {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val online = r.readU8() != 0
        val lastSeen = r.readI64()
        return PresenceEvent(cid, online, lastSeen)
    }

    fun decodeReaction(payload: ByteArray): ReactionEvent {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val msgId = r.readBytes(16)
        val op = r.readU8()  // 1 = add, 2 = remove
        val codepoint = r.readU32().toInt()
        val tsMs = r.readU64()
        return ReactionEvent(cid, msgId, op, codepoint, tsMs)
    }

    fun decodeCallState(payload: ByteArray): CallState {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val callId = r.readBytes(16)
        val state = CallEvent.fromValue(r.readU8())
        return CallState(cid, callId, state)
    }

    /** AVATAR_RECEIVED: [cid u32][avatar_id 16][data str16 ≤16000].
     *  data is str16 = [len u16][raw bytes]. Read length, then raw bytes directly. */
    fun decodeAvatarReceived(payload: ByteArray): AvatarData {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val avatarId = r.readBytes(16)
        // str16: read length prefix then raw bytes (do not UTF-8 decode image data)
        val data = r.readRaw16()
        return AvatarData(cid, avatarId, data)
    }

    fun decodeFileReceived(payload: ByteArray): FileInfo {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val fileId = r.readBytes(16)
        val size = r.readU32().toInt()
        val name = r.readStr16()
        return FileInfo(cid, fileId, size, name)
    }

    fun decodeContactJoined(payload: ByteArray): ContactJoinedEvent {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val name = r.readStr16()
        return ContactJoinedEvent(cid, name)
    }

    fun decodeContactProfile(payload: ByteArray): ContactProfileEvent {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val name = r.readStr16()
        val avatarId = r.readBytes(16)
        return ContactProfileEvent(cid, name, avatarId)
    }

    fun decodeBlockedByPeer(payload: ByteArray): Int {
        val r = FrameReader(payload)
        return r.readU32().toInt()
    }

    fun decodeFileSent(payload: ByteArray): FileInfo {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val fileId = r.readBytes(16)
        val size = r.readU32().toInt()
        val name = r.readStr16()
        return FileInfo(cid, fileId, size, name)
    }

    fun decodeContactCard(payload: ByteArray): ContactCard {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val name = r.readStr16()
        val bundle = r.readBytes(64)
        val addr = r.readStr16()
        return ContactCard(cid, name, bundle, addr)
    }

    /**
     * REPLY_RECEIVED (13, API 2.6.0):
     * `[cid u32][msg_id 16][reply_to 16][ts_ms u64][name str16][body str16][entity tail]`.
     */
    fun decodeReplyReceived(payload: ByteArray): MessageReceivedEvent {
        val r = FrameReader(payload)
        val cid = r.readU32().toInt()
        val msgId = r.readBytes(16)
        val replyTo = r.readBytes(16)
        val tsMs = r.readU64()
        val name = r.readStr16()
        val body = r.readStr16()
        val entities = decodeEntityTail(r)
        val flags = 0.toByte()  // incoming — not mine, not delivered
        return MessageReceivedEvent(
            message = Message(cid, msgId, flags, tsMs, body, entities),
            senderName = name,
            replyTo = replyTo
        )
    }

    /**
     * CONFIG_CHANGED (14, API 2.6.0) payload — the new config blob
     * (§4.9: `[ver u8][flags u32][server_addr str16][tor_addr str16]`).
     */
    fun decodeConfigChanged(payload: ByteArray): ConfigBlob {
        val r = FrameReader(payload)
        val ver = r.readU8()
        val flags = r.readU32().toInt()
        val serverAddr = r.readStr16()
        val torAddr = r.readStr16()
        return ConfigBlob(ver, flags, serverAddr, torAddr)
    }

    // ── Event wrappers ──────────────────────────────────────────────────

    data class MessageReceivedEvent(
        val message: Message,
        val senderName: String,
        /** REPLY_RECEIVED only: the quoted message id (16 bytes); null otherwise. */
        val replyTo: ByteArray? = null
    )

    data class ConfigBlob(
        val ver: Int,
        val flags: Int,
        val serverAddr: String,  // str16, empty = none
        val torAddr: String      // str16, empty = none
    )

    data class MessageDeliveredEvent(
        val cid: Int,
        val msgId: ByteArray
    )

    data class PresenceEvent(
        val cid: Int,
        val online: Boolean,
        val lastSeen: Long
    )

    data class ReactionEvent(
        val cid: Int,
        val msgId: ByteArray,
        val op: Int,          // 1 = add, 2 = remove
        val codepoint: Int,   // Unicode codepoint
        val tsMs: Long
    )

    data class ContactJoinedEvent(
        val cid: Int,
        val name: String
    )

    data class ContactProfileEvent(
        val cid: Int,
        val name: String,
        val avatarId: ByteArray
    )
}
