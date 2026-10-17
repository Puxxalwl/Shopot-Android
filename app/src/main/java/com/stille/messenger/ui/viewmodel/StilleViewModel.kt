package com.stille.messenger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stille.coreapi.Contact
import com.stille.coreapi.Message
import com.stille.coreapi.Method
import com.stille.coreapi.Requests
import com.stille.coreapi.Responses
import com.stille.coreapi.Entity
import com.stille.messenger.service.CoreEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatsUiState(
    val contacts: List<Contact> = emptyList(),
    val lastMessages: Map<Int, Message?> = emptyMap(),
    val lastMessageTime: Map<Int, Long> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class ChatUiState(
    val contact: Contact? = null,
    val messages: List<Message> = emptyList(),
    val isOnline: Boolean = false,
    val error: String? = null
)

class ChatsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: CoreEvent) {
        when (event) {
            is CoreEvent.Ready -> {
                _uiState.value = _uiState.value.copy(
                    contacts = event.contacts,
                    isLoading = false,
                    error = null
                )
            }
            is CoreEvent.MessageReceived -> {
                val cid = event.message.cid
                val current = _uiState.value.contacts.find { it.cid == cid }
                if (current != null) {
                    val contacts = _uiState.value.contacts.map {
                        if (it.cid == cid) current.copy(lastSeenMs = event.message.tsMs) else it
                    }.sortedByDescending { c ->
                        _uiState.value.lastMessageTime[c.cid] ?: 0L
                    }
                    _uiState.value = _uiState.value.copy(
                        contacts = contacts,
                        lastMessages = _uiState.value.lastMessages + (cid to event.message),
                        lastMessageTime = _uiState.value.lastMessageTime + (cid to event.message.tsMs)
                    )
                }
            }
            is CoreEvent.MessageDelivered -> {
                val cid = event.cid
                val msgs = _uiState.value.lastMessages[cid]?.let { msg ->
                    if (msg.msgId.contentEquals(event.msgId))
                        msg.copy(flags = (msg.flags.toInt() or 0b10).toByte())
                    else msg
                }
                _uiState.value = _uiState.value.copy(
                    lastMessages = _uiState.value.lastMessages + (cid to msgs)
                )
            }
            is CoreEvent.ContactJoined -> { /* Re-fetch contacts */ }
            is CoreEvent.Error -> {
                _uiState.value = _uiState.value.copy(error = event.message, isLoading = false)
            }
            else -> { /* ignore */ }
        }
    }
}

class ChatViewModel(private val cid: Int) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private val messageCache = mutableListOf<Message>()

    fun loadHistory(contacts: List<Contact>, service: com.stille.messenger.service.StilleService?) {
        val contact = contacts.find { it.cid == cid }
        _uiState.value = _uiState.value.copy(contact = contact, isOnline = contact?.online ?: false)

        service?.let { core ->
            viewModelScope.launch {
                try {
                    val resp = core.sendRequest(
                        Method.GET_HISTORY,
                        Requests.getHistory(cid, 50)
                    )
                    val batch = Responses.decodeGetHistory(resp.second)
                    messageCache.clear()
                    messageCache.addAll(batch.messages.takeLast(200).map { msg -> msg.copy(cid = cid) })
                    _uiState.value = _uiState.value.copy(
                        messages = messageCache.toList(),
                        error = null
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
            }
        }
    }

    fun onEvent(event: CoreEvent) {
        when (event) {
            is CoreEvent.MessageReceived -> {
                if (event.message.cid == cid) {
                    if (messageCache.none { it.msgId.contentEquals(event.message.msgId) }) {
                        if (messageCache.size == 200) messageCache.removeAt(0)
                        messageCache.add(event.message)
                        _uiState.value = _uiState.value.copy(messages = messageCache.toList())
                    }
                }
            }
            is CoreEvent.MessageDelivered -> {
                if (event.cid == cid) {
                    val idx = messageCache.indexOfFirst { it.msgId.contentEquals(event.msgId) }
                    if (idx >= 0) {
                        val old = messageCache[idx]
                        messageCache[idx] = old.copy(
                            flags = (old.flags.toInt() or 0b10).toByte()
                        )
                        _uiState.value = _uiState.value.copy(messages = messageCache.toList())
                    }
                }
            }
            is CoreEvent.ContactJoined -> {
                if (event.cid == cid) {
                    _uiState.value = _uiState.value.copy(isOnline = true)
                }
            }
            else -> { }
        }
    }

    fun sendMessage(body: String, entities: List<Entity>, service: com.stille.messenger.service.StilleService?) {
        val localMsg = Message(
            cid = cid,
            msgId = ByteArray(16),
            flags = 0b01.toByte(),  // mine
            tsMs = System.currentTimeMillis(),
            body = body,
            entities = entities
        )
        messageCache.add(localMsg)
        _uiState.value = _uiState.value.copy(messages = messageCache.toList())

        service?.let { core ->
            viewModelScope.launch {
                val response = core.sendRequest(
                    Method.SEND_MESSAGE,
                    Requests.sendMessage(cid, body, entities)
                )
                val (serverMsgId, queued) = Responses.decodeSendMessage(response.second)
                val idx = messageCache.indexOfFirst {
                    it.tsMs == localMsg.tsMs && it.body == body
                }
                if (idx >= 0) {
                    messageCache[idx] = messageCache[idx].copy(
                        msgId = serverMsgId,
                        flags = if (queued) 0b01 else (messageCache[idx].flags.toInt() or 0b10).toByte()
                    )
                    _uiState.value = _uiState.value.copy(messages = messageCache.toList())
                }
            }
        }
    }
}
