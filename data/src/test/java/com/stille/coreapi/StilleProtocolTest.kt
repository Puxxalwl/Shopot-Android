package com.stille.coreapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StilleProtocolTest {

    @Test
    fun `encodeFrame produces correct 16-byte header plus payload`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val frame = encodeFrame(
            kind = FrameKind.REQUEST,
            id = 7,
            requestId = 42,
            status = 0,
            payload = payload
        )

        assertEquals(20, frame.size, "frame size = header(16) + payload(4)")

        // Verify magic (0x5A50) little-endian
        assertEquals(0x50, frame[0].toInt() and 0xFF, "magic byte 0")
        assertEquals(0x5A, frame[1].toInt() and 0xFF, "magic byte 1")
        assertEquals(1, frame[2].toInt() and 0xFF, "version")
        assertEquals(1, frame[3].toInt() and 0xFF, "kind=REQUEST")
        assertEquals(7, frame[4].toInt() and 0xFF, "id low byte")
        assertEquals(0, frame[5].toInt() and 0xFF, "id high byte")
        assertEquals(42, frame[6].toInt() and 0xFF, "request_id byte 0")
        assertEquals(0, frame[10].toInt() and 0xFF, "status")
        assertEquals(4, frame[12].toInt() and 0xFF, "payload_len byte 0")
        assertEquals(1, frame[16].toInt() and 0xFF)
        assertEquals(2, frame[17].toInt() and 0xFF)
        assertEquals(3, frame[18].toInt() and 0xFF)
        assertEquals(4, frame[19].toInt() and 0xFF)
    }

    @Test
    fun `decodeFrame roundtrips correctly`() {
        val payload = "hello".encodeToByteArray()
        val frame = encodeFrame(FrameKind.REQUEST, Method.SEND_MESSAGE.id, requestId = 99, payload = payload)
        val (header, decodedPayload) = decodeFrame(frame)

        assertEquals(FrameKind.REQUEST, header.kind)
        assertEquals(Method.SEND_MESSAGE.id, header.id)
        assertEquals(99, header.requestId)
        assertEquals(payload.size, header.payloadLen)
        assertEquals(payload.toList(), decodedPayload.toList())
    }

    @Test
    fun `decodeFrame rejects bad magic`() {
        val frame = encodeFrame(FrameKind.REQUEST, 2, requestId = 1, payload = ByteArray(0))
        frame[0] = 0x00
        assertFailsWith<IllegalArgumentException> { decodeFrame(frame) }
    }

    @Test
    fun `decodeFrame rejects version mismatch`() {
        val frame = encodeFrame(FrameKind.REQUEST, 2, requestId = 1, payload = ByteArray(0))
        frame[2] = 2
        assertFailsWith<IllegalArgumentException> { decodeFrame(frame) }
    }

    @Test
    fun `all methods have unique ids`() {
        val ids = Method.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all events have unique ids`() {
        val ids = Event.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `methods and status match CORE-API spec`() {
        assertEquals(2, Method.GET_VERSION.id)
        assertEquals(7, Method.SEND_MESSAGE.id)
        assertEquals(32, Method.GET_PINNED_MESSAGES.id)
        assertEquals("OK", status.name(0))
        assertEquals("BAD_FRAME", status.name(1))
        assertEquals("BAD_ARGS", status.name(3))
        assertEquals("NO_CONTACT", status.name(4))
        assertEquals("TOO_BIG", status.name(7))
        assertEquals("BAD_INVITE_CODE", status.name(8))
        assertEquals("NO_SESSION", status.name(12))
        assertEquals("INTERNAL", status.name(255))
    }

    @Test
    fun `createInvite encodes correct format`() {
        val payload = Requests.createInvite(name = "Alice", addr = "192.168.1.1:7777", relay = false)
        val r = FrameReader(payload)
        assertEquals(0, r.readU8())
        assertEquals("Alice", r.readStr16())
        assertEquals("192.168.1.1:7777", r.readStr16())
    }

    @Test
    fun `createInvite with relay flag`() {
        val payload = Requests.createInvite(name = "Bob", addr = "r:9000", relay = true)
        val r = FrameReader(payload)
        assertEquals(1, r.readU8())
    }

    @Test
    fun `createInvite default name is shopot`() {
        val payload = Requests.createInvite(name = "", addr = "host:7777")
        val r = FrameReader(payload)
        r.readU8()
        assertEquals("shopot", r.readStr16())
    }

    @Test
    fun `sendMessage encodes body and entity tail`() {
        val entities = listOf(Entity(kind = 1, offset = 0, length = 3))
        val payload = Requests.sendMessage(cid = 5, body = "abc hello", entities)
        val r = FrameReader(payload)
        assertEquals(5, r.readU32().toInt())
        assertEquals("abc hello", r.readStr16())
        val tail = decodeEntityTail(r)
        assertEquals(1, tail.size)
        assertEquals(1, tail[0].kind)
        assertEquals(3, tail[0].length)
    }

    @Test
    fun `sendMessage rejects empty body`() {
        assertFailsWith<IllegalArgumentException> {
            Requests.sendMessage(cid = 1, body = "")
        }
    }

    @Test
    fun `decodeGetVersion parses correctly`() {
        val fb = FrameBuffer()
        fb.writeU16(2); fb.writeU16(2); fb.writeU16(0)
        val v = Responses.decodeGetVersion(fb.toByteArray())
        assertEquals(2, v.major)
        assertEquals(2, v.minor)
        assertEquals(0, v.patch)
    }

    @Test
    fun `decodeListContacts parses correctly`() {
        val fb = FrameBuffer()
        fb.writeU32(2L)
        fb.writeU32(1L)
        fb.writeU8(0b01)
        fb.writeI64(1234567890L)
        fb.writeStr16("Alice")
        fb.writeU32(2L)
        fb.writeU8(0b10)
        fb.writeI64(0L)
        fb.writeStr16("Bob")
        val contacts = Responses.decodeListContacts(fb.toByteArray())
        assertEquals(2, contacts.size)
        assertEquals("Alice", contacts[0].name)
        assertTrue(contacts[0].online)
        assertTrue(contacts[1].blocked)
    }

    @Test
    fun `decodeMessageReceived parses correctly`() {
        val fb = FrameBuffer()
        fb.writeU32(3L)
        fb.writeBytes(ByteArray(16) { (it + 1).toByte() })
        fb.writeU64(99999L)
        fb.writeStr16("Alice")
        fb.writeStr16("Hello world")
        fb.writeU8(0)
        val ev = Events.decodeMessageReceived(fb.toByteArray())
        assertEquals(3, ev.message.cid)
        assertEquals("Alice", ev.senderName)
        assertEquals("Hello world", ev.message.body)
    }

    @Test
    fun `decodePresence parses correctly`() {
        val fb = FrameBuffer()
        fb.writeU32(1L)
        fb.writeU8(1)
        fb.writeI64(1700000000000L)
        val ev = Events.decodePresence(fb.toByteArray())
        assertEquals(1, ev.cid)
        assertTrue(ev.online)
        assertEquals(1700000000000L, ev.lastSeen)
    }

    @Test
    fun `entity tail roundtrip`() {
        val entities = listOf(
            Entity(kind = 1, offset = 0, length = 5, value = ""),
            Entity(kind = 8, offset = 6, length = 4, value = "test"),
        )
        val data = encodeEntityTail(entities)
        val decoded = decodeEntityTail(FrameReader(data))
        assertEquals(2, decoded.size)
        assertEquals(1, decoded[0].kind)
        assertEquals(8, decoded[1].kind)
        assertEquals("test", decoded[1].value)
    }

    @Test
    fun `limits match CORE-API spec`() {
        assertEquals(4096, Limits.TEXT_BODY)
        assertEquals(32, Limits.DISPLAY_NAME)
        assertEquals(16000, Limits.AVATAR_PAYLOAD)
        assertEquals(64 * 1024 * 1024, Limits.FILE_SIZE)
        assertEquals(200, Limits.HISTORY_MAX)
        assertEquals(4, Limits.LIVE_INVITES)
        assertEquals(1, Limits.PINNED_CHAT_MAX)
        assertEquals(16, Limits.PINNED_MSG_MAX)
    }
}
