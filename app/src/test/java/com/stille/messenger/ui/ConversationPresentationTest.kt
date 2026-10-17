package com.stille.messenger.ui

import com.stille.coreapi.Contact
import com.stille.coreapi.Message
import org.junit.Assert.*
import org.junit.Test

class ConversationPresentationTest {
    private fun message(id: Int, flags: Byte = 1) = Message(
        cid = 1, msgId = ByteArray(16).apply { this[0] = id.toByte(); this[1] = (id ushr 8).toByte() },
        flags = flags, tsMs = id.toLong(), body = "test"
    )

    @Test fun searchTrimsWhitespaceAndIgnoresCase() {
        val contacts = listOf(Contact(1, true, false, 0L, "Алиса"), Contact(2, false, false, 0L, "Bob"))
        assertEquals(listOf(contacts[0]), filterContacts(contacts, "  АЛИ  "))
        assertEquals(contacts, filterContacts(contacts, " "))
        assertTrue(filterContacts(contacts, "nobody").isEmpty())
    }

    @Test fun historyIsBoundedAndNewestFirst() {
        val rows = visibleMessages((0..250).map { message(it) })
        assertEquals(200, rows.size)
        assertEquals(250L, rows.first().second.tsMs)
        assertEquals(51L, rows.last().second.tsMs)
    }

    @Test fun duplicateIdsUseLatestDeliveryState() {
        val rows = visibleMessages(listOf(message(1), message(2), message(1, 3)))
        assertEquals(2, rows.size)
        assertTrue(rows.first().second.delivered)
    }

    @Test fun keyIsStableAcrossDeliveryUpdates() {
        assertEquals(messageKey(message(255)), messageKey(message(255, 3)))
        assertEquals(32, messageKey(message(255)).length)
        assertNotEquals(messageKey(message(255)), messageKey(message(256)))
    }
}
