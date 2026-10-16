package com.shopot.coreapi

import com.shopot.coreapi.status.StatusCode

/**
 * Binary UDS interface codec for shopot-core (API 2.2.0).
 *
 * Implements the 16-byte LE frame header and all typed payload encoders/decoders
 * for request methods, responses, and events as defined in:
 * https://github.com/Puxxalwl/shopot-core — zig/docs/CORE-API.md
 *
 * Wire contract:
 * - magic: u16 LE = 0x5A50 ("ZP")
 * - version: u8 = 1
 * - kind: u8 = 1 (REQUEST), 2 (RESPONSE), 3 (EVENT)
 * - id: u16 LE (method for request/response, event for event)
 * - request_id: u32 LE (client-set, echoed in response; 0 in events)
 * - status: u16 LE (response only; 0 otherwise)
 * - payload_len: u32 LE (0..16777216 = 16 MiB)
 *
 * Total header: exactly 16 bytes. All integers little-endian.
 */

// ── Frame kind ────────────────────────────────────────────────────────────
enum class FrameKind(val value: Int) {
    REQUEST(1),
    RESPONSE(2),
    EVENT(3);

    companion object {
        fun fromValue(v: Int) = entries.firstOrNull { it.value == v }
            ?: throw ProtocolException("unknown frame kind: $v")
    }
}

// ── Method IDs (request = response id) ────────────────────────────────────
// Names are the contract; values are stable wire IDs.
enum class Method(val id: Int) {
    CLOSE(1),
    GET_VERSION(2),
    GET_IDENTITY(3),
    CREATE_INVITE(4),
    ACCEPT_INVITE(5),
    LIST_CONTACTS(6),
    SEND_MESSAGE(7),
    SET_REACTION(8),
    REMOVE_REACTION(9),
    GET_HISTORY(10),
    GET_MESSAGE(11),
    SET_NAME(12),
    SET_AVATAR(13),
    GET_AVATAR_ID(14),
    SEND_FILE(15),
    START_CALL(16),
    ANSWER_CALL(17),
    END_CALL(18),
    BLOCK_CONTACT(19),
    UNBLOCK_CONTACT(20),
    CONNECT(21),
    LISTEN(22),
    SEARCH_MESSAGE(23),
    GLOBAL_SEARCH_MESSAGE(24),
    PIN_CHAT(25),
    GET_PINNED_CHAT(26),
    UNPIN_CHAT(27),
    DELETE_CHAT(28),
    SEND_CONTACT(29),
    PIN_MESSAGE(30),
    UNPIN_MESSAGE(31),
    GET_PINNED_MESSAGES(32);

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id }
            ?: throw ProtocolException("unknown method id: $id")
    }
}

// ── Event IDs ─────────────────────────────────────────────────────────────
enum class Event(val id: Int) {
    MESSAGE_RECEIVED(1),
    MESSAGE_DELIVERED(2),
    PRESENCE(3),
    REACTION(4),
    CALL_STATE(5),
    AVATAR_RECEIVED(6),
    FILE_RECEIVED(7),
    CONTACT_JOINED(8),
    CONTACT_PROFILE(9),
    BLOCKED_BY_PEER(10),
    FILE_SENT(11),
    CONTACT_CARD(12);

    companion object {
        fun fromId(id: Int) = entries.firstOrNull { it.id == id }
            ?: throw ProtocolException("unknown event id: $id")
    }
}

// ── Status codes (names are the contract) ─────────────────────────────────
object status {
    const val OK = 0
    const val BAD_FRAME = 1
    const val UNKNOWN_METHOD = 2
    const val BAD_ARGS = 3
    const val NO_CONTACT = 4
    const val CONTACT_BLOCKED = 5
    const val NOT_FOUND = 6
    const val TOO_BIG = 7
    const val BAD_INVITE_CODE = 8
    const val TOO_MANY_INVITES = 9
    const val FILE_NOT_FOUND = 10
    const val UNSUPPORTED_REACTION = 11
    const val NO_SESSION = 12
    const val BUSY = 13
    const val NO_CALL = 14
    const val NO_RELAY = 15
    const val TRANSPORT_FAILED = 16
    const val INTERNAL = 255

    fun name(code: Int): String = when (code) {
        OK -> "OK"
        BAD_FRAME -> "BAD_FRAME"
        UNKNOWN_METHOD -> "UNKNOWN_METHOD"
        BAD_ARGS -> "BAD_ARGS"
        NO_CONTACT -> "NO_CONTACT"
        CONTACT_BLOCKED -> "CONTACT_BLOCKED"
        NOT_FOUND -> "NOT_FOUND"
        TOO_BIG -> "TOO_BIG"
        BAD_INVITE_CODE -> "BAD_INVITE_CODE"
        TOO_MANY_INVITES -> "TOO_MANY_INVITES"
        FILE_NOT_FOUND -> "FILE_NOT_FOUND"
        UNSUPPORTED_REACTION -> "UNSUPPORTED_REACTION"
        NO_SESSION -> "NO_SESSION"
        BUSY -> "BUSY"
        NO_CALL -> "NO_CALL"
        NO_RELAY -> "NO_RELAY"
        TRANSPORT_FAILED -> "TRANSPORT_FAILED"
        INTERNAL -> "INTERNAL"
        else -> "UNKNOWN($code)"
    }
}

// ── Constants ─────────────────────────────────────────────────────────────
const val MAGIC: Short = 0x5A50     // "ZP"
const val FRAME_VERSION: Byte = 1
const val HEADER_SIZE: Int = 16
const val MAX_PAYLOAD: Int = 16_777_216  // 16 MiB

// ── Limits from CORE-API.md §6 ────────────────────────────────────────────
object Limits {
    const val TEXT_BODY = 4096
    const val DISPLAY_NAME = 32
    const val AVATAR_PAYLOAD = 16000
    const val FILE_SIZE = 64 * 1024 * 1024  // 64 MB
    const val FILE_CHUNK = 16000
    const val HISTORY_MAX = 200
    const val HISTORY_DEFAULT = 50
    const val SEARCH_MAX = 200
    const val SEARCH_DEFAULT = 50
    const val OUTBOX_MAX = 1000
    const val LIVE_INVITES = 4
    const val PINNED_CHAT_MAX = 1
    const val PINNED_MSG_MAX = 16
    const val INVITE_NAME_MAX = 32
    const val INVITE_ADDR_MAX = 64
    const val SEARCH_QUERY_MAX = 4096
    const val ENTITY_MAX = 32
    const val SPOOFER_MAX = 255  // link value, code-block lang
}

// ── Exceptions ────────────────────────────────────────────────────────────
class ProtocolException(message: String) : Exception(message)
class ProtocolStatusException(val code: Int, message: String) : Exception(message)

// ── Frame I/O helpers (Little-Endian, allocation-conscious ByteArray) ──────
/**
 * Encodes protocol data into a primitive [ByteArray].
 * It avoids `ArrayList<Byte>` boxing and grows geometrically.
 */
class FrameBuffer(initialCapacity: Int = 256) {
    private var buf = ByteArray(initialCapacity.coerceAtLeast(16))
    private var length = 0

    private fun ensure(extra: Int) {
        require(extra >= 0 && length <= Int.MAX_VALUE - extra) { "frame buffer overflow" }
        val needed = length + extra
        if (needed <= buf.size) return
        var capacity = buf.size
        while (capacity < needed) {
            capacity = if (capacity <= Int.MAX_VALUE / 2) capacity * 2 else needed
        }
        buf = buf.copyOf(capacity)
    }

    fun writeU8(v: Int) {
        ensure(1)
        buf[length++] = (v and 0xFF).toByte()
    }

    fun writeU16(v: Int) {
        ensure(2)
        buf[length++] = (v and 0xFF).toByte()
        buf[length++] = ((v ushr 8) and 0xFF).toByte()
    }

    fun writeU32(v: Long) {
        ensure(4)
        val n = v.toInt()
        buf[length++] = (n and 0xFF).toByte()
        buf[length++] = ((n ushr 8) and 0xFF).toByte()
        buf[length++] = ((n ushr 16) and 0xFF).toByte()
        buf[length++] = ((n ushr 24) and 0xFF).toByte()
    }

    fun writeU64(v: Long) {
        ensure(8)
        for (i in 0..7) buf[length++] = ((v ushr (i * 8)) and 0xFF).toByte()
    }

    fun writeI64(v: Long) = writeU64(v)

    fun writeBytes(data: ByteArray) {
        ensure(data.size)
        data.copyInto(buf, length)
        length += data.size
    }

    fun writeStr16(s: String) = writeUtf8(s, 2)
    fun writeStr32(s: String) = writeUtf8(s, 4)

    private fun writeUtf8(s: String, lenSize: Int) {
        val bytes = s.encodeToByteArray()
        if (lenSize == 2) {
            require(bytes.size <= 65535) { "str16 too long: ${bytes.size}" }
            writeU16(bytes.size)
        } else {
            writeU32(bytes.size.toLong())
        }
        writeBytes(bytes)
    }

    /** Raw bytes with a u16 length prefix; used for opaque avatar payloads. */
    fun writeRaw16(data: ByteArray) {
        require(data.size <= 65535) { "raw str16 too long: ${data.size}" }
        writeU16(data.size)
        writeBytes(data)
    }

    fun writeRaw32(data: ByteArray) {
        writeU32(data.size.toLong())
        writeBytes(data)
    }

    fun toByteArray(): ByteArray = buf.copyOf(length)
    val size: Int get() = length
}

// ── Decoder ───────────────────────────────────────────────────────────────
class FrameReader(private val data: ByteArray) {
    var pos: Int = 0
        private set

    private fun requireAvailable(count: Int) {
        check(count >= 0 && pos <= data.size - count) { "read past end at $pos: need $count" }
    }

    fun readU8(): Int {
        requireAvailable(1)
        return data[pos++].toInt() and 0xFF
    }

    fun readByte(): Byte {
        requireAvailable(1)
        return data[pos++]
    }

    fun readU16(): Int {
        requireAvailable(2)
        val b0 = data[pos++].toInt() and 0xFF
        val b1 = data[pos++].toInt() and 0xFF
        return b0 or (b1 shl 8)
    }
    fun readU32(): Long {
        requireAvailable(4)
        var v = 0L
        repeat(4) { v = v or ((data[pos++].toLong() and 0xFF) shl (it * 8)) }
        return v
    }

    fun readU64(): Long {
        requireAvailable(8)
        var v = 0L
        repeat(8) { v = v or ((data[pos++].toLong() and 0xFF) shl (it * 8)) }
        return v
    }

    fun readI64(): Long = readU64()

    fun readBytes(n: Int): ByteArray {
        requireAvailable(n)
        val out = data.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    fun readRaw16(): ByteArray = readBytes(readU16())
    fun readRaw32(): ByteArray = readBytes(readU32().toInt())
    fun readStr16(): String {
        val len = readU16(); val b = readBytes(len); return b.decodeToString()
    }
    fun readStr32(): String {
        val len = readU32().toInt(); val b = readBytes(len); return b.decodeToString()
    }
    val remaining: Int get() = data.size - pos
}

// ── Frame header ──────────────────────────────────────────────────────────
data class FrameHeader(
    val kind: FrameKind,
    val id: Int,          // method id (request/response) or event id
    val requestId: Int,   // set by client, echoed in response; 0 in events
    val status: Int,      // 0 in non-response frames
    val payloadLen: Int
)

// ── Encode / decode a full frame ──────────────────────────────────────────
fun encodeFrame(
    kind: FrameKind,
    id: Int,
    requestId: Int = 0,
    status: Int = 0,
    payload: ByteArray = ByteArray(0)
): ByteArray {
    require(payload.size <= MAX_PAYLOAD) { "payload exceeds 16 MiB" }
    val out = FrameBuffer()
    out.writeU16(MAGIC.toInt())
    out.writeU8(FRAME_VERSION.toInt())
    out.writeU8(kind.value)
    out.writeU16(id)
    out.writeU32(requestId.toLong())
    out.writeU16(status)
    out.writeU32(payload.size.toLong())
    out.writeBytes(payload)
    return out.toByteArray()
}

fun decodeFrame(bytes: ByteArray): Pair<FrameHeader, ByteArray> {
    require(bytes.size >= HEADER_SIZE) { "frame too small: ${bytes.size} bytes" }
    val r = FrameReader(bytes)
    val magic = r.readU16()
    require(magic == MAGIC.toInt()) { "bad magic: 0x${magic.toString(16)}" }
    val version = r.readU8()
    require(version == FRAME_VERSION.toInt()) { "unsupported version: $version" }
    val kind = FrameKind.fromValue(r.readU8())
    val id = r.readU16()
    val requestId = r.readU32().toInt()
    val status = r.readU16()
    val payloadLen = r.readU32().toInt()
    require(payloadLen <= MAX_PAYLOAD) { "payload too big: $payloadLen" }
    require(r.remaining == payloadLen) { "frame length mismatch: header=$payloadLen, actual=${r.remaining}" }
    val payload = r.readBytes(payloadLen)
    return FrameHeader(kind, id, requestId, status, payloadLen) to payload
}

// ── Typed payload encoders ────────────────────────────────────────────────

/** Entity tail: formatting entities for message bodies (CORE-API §4.3). */
data class Entity(
    val kind: Int,      // 1=bold, 2=italic, ..., 8=link
    val offset: Int,    // UTF-8 byte offset into body
    val length: Int,    // UTF-8 byte length
    val value: String = ""  // URL for links; lang tag for code blocks; empty otherwise
)

fun encodeEntityTail(entities: List<Entity>): ByteArray {
    val fb = FrameBuffer()
    require(entities.size <= Limits.ENTITY_MAX) { "too many entities: ${entities.size}" }
    fb.writeU8(entities.size)
    for (e in entities) {
        fb.writeU8(e.kind)
        fb.writeU16(e.offset)
        fb.writeU16(e.length)
        val valueBytes = e.value.encodeToByteArray()
        require(valueBytes.size <= Limits.SPOOFER_MAX) { "entity value too long" }
        fb.writeU8(valueBytes.size)
        fb.writeBytes(valueBytes)
    }
    return fb.toByteArray()
}

fun decodeEntityTail(r: FrameReader): List<Entity> {
    val count = r.readU8()
    val out = mutableListOf<Entity>()
    repeat(count) {
        val kind = r.readU8()
        val off = r.readU16()
        val len = r.readU16()
        val vlen = r.readU8()
        val value = if (vlen > 0) r.readBytes(vlen).decodeToString() else ""
        out.add(Entity(kind, off, len, value))
    }
    return out
}
