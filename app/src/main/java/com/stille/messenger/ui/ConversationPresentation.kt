package com.stille.messenger.ui

import com.stille.coreapi.Contact
import com.stille.coreapi.Message

internal fun filterContacts(contacts: List<Contact>, query: String): List<Contact> =
    contacts.filter { it.name.contains(query.trim(), ignoreCase = true) }

internal fun messageKey(message: Message): String = message.msgId.joinToString("") {
    (it.toInt() and 255).toString(16).padStart(2, '0')
}

/** A defensive UI bound, not a replacement for core validation or service deduplication. */
internal fun visibleMessages(messages: List<Message>): List<Pair<String, Message>> =
    messages.takeLast(200).asReversed().distinctBy { it.msgId.toList() }.map { messageKey(it) to it }
