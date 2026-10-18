package com.stille.messenger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stille.coreapi.Contact
import com.stille.coreapi.Entity
import com.stille.coreapi.Message
import com.stille.coreapi.Method
import com.stille.coreapi.Requests
import com.stille.coreapi.Responses
import com.stille.messenger.service.CoreEvent
import com.stille.messenger.service.StilleService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Conversation index contains only contacts with confirmed message history. */
data class ChatsUiState(
    val contacts: List<Contact> = emptyList(),
    val lastMessages: Map<Int, Message?> = emptyMap(),
    val lastMessageTime: Map<Int, Long> = emptyMap(),
    val historyCids: Set<Int> = emptySet(),
    val chatMessages: Map<Int, List<Message>> = emptyMap(),
    val pinnedCid: Int? = null,
    val error: String? = null
)

data class ChatUiState(
    val contact: Contact? = null,
    val messages: List<Message> = emptyList(),
    val isOnline: Boolean = false,
    val error: String? = null
)

class ChatsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState = _uiState.asStateFlow()
    private var eventService: StilleService? = null
    private var pinnedLoaded = false
    private val historyCache = mutableMapOf<Int, MutableList<Message>>()

    fun attach(service: StilleService?, contacts: List<Contact>) {
        if (service == null) return
        if (_uiState.value.contacts != contacts) _uiState.value = _uiState.value.copy(contacts = contacts)
        if (eventService !== service) {
            eventService = service
            viewModelScope.launch { service.events.collect { event -> onEvent(event) } }
        }
        contacts.forEach { contact -> loadHistory(contact.cid, service) }
        if (!pinnedLoaded) viewModelScope.launch {
            runCatching {
                val response = service.sendRequest(Method.GET_PINNED_CHAT, Requests.getPinnedChat())
                _uiState.value = _uiState.value.copy(pinnedCid = Responses.decodeGetPinnedChat(response.second).takeIf { it > 0 })
                pinnedLoaded = true
            }
        }
    }

    private fun loadHistory(cid: Int, service: StilleService) {
        if (historyCache.containsKey(cid)) return
        historyCache[cid] = mutableListOf()
        viewModelScope.launch {
            try {
                val response = service.sendRequest(Method.GET_HISTORY, Requests.getHistory(cid, 50))
                val batch = Responses.decodeGetHistory(response.second)
                val msgs = batch.messages.takeLast(200).map { it.copy(cid = cid) }
                val eventMessages = historyCache[cid].orEmpty()
                val merged = (msgs + eventMessages).distinctBy { it.msgId.toList() }.takeLast(200)
                historyCache[cid] = merged.toMutableList()
                updateLast(cid, merged.lastOrNull())
            } catch (_: Exception) {
                historyCache.remove(cid)
            }
        }
    }

    private fun updateLast(cid: Int, message: Message?) {
        val last = _uiState.value.lastMessages.toMutableMap()
        val times = _uiState.value.lastMessageTime.toMutableMap()
        val known = _uiState.value.historyCids.toMutableSet()
        if (message != null) {
            last[cid] = message
            times[cid] = message.tsMs
            known.add(cid)
        } else if (historyCache[cid]?.isNotEmpty() != true && last[cid] == null) {
            last.remove(cid)
            times.remove(cid)
            known.remove(cid)
        }
        _uiState.value = _uiState.value.copy(lastMessages = last, lastMessageTime = times, historyCids = known, chatMessages = historyCache.mapValues { it.value.toList() })
    }

    fun onEvent(event: CoreEvent) {
        when (event) {
            is CoreEvent.MessageReceived -> addMessage(event.message)
            is CoreEvent.MessageDelivered -> {
                val messages = historyCache[event.cid] ?: return
                val index = messages.indexOfFirst { it.msgId.contentEquals(event.msgId) }
                if (index >= 0) {
                    messages[index] = messages[index].copy(flags = (messages[index].flags.toInt() or 0b10).toByte())
                    updateLast(event.cid, messages.lastOrNull())
                }
            }
            else -> Unit
        }
    }

    private fun addMessage(message: Message) {
        val cache = historyCache.getOrPut(message.cid) { mutableListOf() }
        val index = cache.indexOfFirst { it.msgId.contentEquals(message.msgId) }
        if (index >= 0) cache[index] = message else {
            if (cache.size == 200) cache.removeAt(0)
            cache.add(message)
        }
        updateLast(message.cid, cache.lastOrNull())
    }

    fun messages(cid: Int): List<Message> = historyCache[cid]?.toList().orEmpty()

    fun sendMessage(cid: Int, body: String, entities: List<Entity>, service: StilleService?, onComplete: (Boolean) -> Unit = {}) {
        val core = service ?: run { _uiState.value = _uiState.value.copy(error = "Core unavailable"); onComplete(false); return }
        val optimisticId = UUID.randomUUID().toString().encodeToByteArray().copyOf(16)
        val local = Message(cid, optimisticId, 0b01, System.currentTimeMillis(), body, entities)
        addMessage(local)
        viewModelScope.launch {
            try {
                val response = core.sendRequest(Method.SEND_MESSAGE, Requests.sendMessage(cid, body, entities))
                val (serverId, _) = Responses.decodeSendMessage(response.second)
                val cache = historyCache[cid] ?: mutableListOf()
                val index = cache.indexOfFirst { it.msgId.contentEquals(optimisticId) }
                if (index >= 0) {
                    cache[index] = cache[index].copy(msgId = serverId, flags = 0b01)
                    updateLast(cid, cache.lastOrNull())
                }
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cache = historyCache[cid]
                cache?.removeAll { it.msgId.contentEquals(optimisticId) }
                updateLast(cid, cache?.lastOrNull())
                _uiState.value = _uiState.value.copy(error = e.message ?: "Message could not be sent")
                onComplete(false)
            }
        }
    }

    fun runChatAction(cid: Int, method: Method, service: StilleService?, onDone: (Boolean) -> Unit = {}) {
        val core = service ?: run { onDone(false); return }
        viewModelScope.launch {
            try {
                val payload = when (method) {
                    Method.PIN_CHAT, Method.DELETE_CHAT, Method.BLOCK_CONTACT, Method.UNBLOCK_CONTACT -> when (method) {
                        Method.PIN_CHAT -> Requests.pinChat(cid)
                        Method.DELETE_CHAT -> Requests.deleteChat(cid)
                        Method.BLOCK_CONTACT -> Requests.blockContact(cid)
                        else -> Requests.unblockContact(cid)
                    }
                    Method.UNPIN_CHAT -> Requests.unpinChat()
                    else -> error("Unsupported chat action")
                }
                core.sendRequest(method, payload)
                if (method == Method.PIN_CHAT) _uiState.value = _uiState.value.copy(pinnedCid = cid)
                if (method == Method.UNPIN_CHAT) _uiState.value = _uiState.value.copy(pinnedCid = null)
                if (method == Method.DELETE_CHAT) {
                    historyCache.remove(cid)
                    val last = _uiState.value.lastMessages - cid
                    _uiState.value = _uiState.value.copy(lastMessages = last, lastMessageTime = _uiState.value.lastMessageTime - cid, historyCids = _uiState.value.historyCids - cid, chatMessages = historyCache.mapValues { it.value.toList() }, pinnedCid = _uiState.value.pinnedCid.takeUnless { it == cid })
                }
                if (method == Method.BLOCK_CONTACT || method == Method.UNBLOCK_CONTACT) {
                    _uiState.value = _uiState.value.copy(contacts = _uiState.value.contacts.map { if (it.cid == cid) it.copy(blocked = method == Method.BLOCK_CONTACT) else it })
                }
                onDone(true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Chat action failed")
                onDone(false)
            }
        }
    }
}

class ChatViewModel(private val cid: Int) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()
    private val messageCache = mutableListOf<Message>()

    fun loadHistory(contacts: List<Contact>, service: StilleService?) {
        val contact = contacts.find { it.cid == cid }
        _uiState.value = _uiState.value.copy(contact = contact, isOnline = contact?.online ?: false)
        service?.let { core -> viewModelScope.launch {
            try {
                val response = core.sendRequest(Method.GET_HISTORY, Requests.getHistory(cid, 50))
                messageCache.clear()
                messageCache.addAll(Responses.decodeGetHistory(response.second).messages.takeLast(200).map { it.copy(cid = cid) })
                _uiState.value = _uiState.value.copy(messages = messageCache.toList(), error = null)
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(error = e.message) }
        } }
    }

    fun onEvent(event: CoreEvent) {
        when (event) {
            is CoreEvent.MessageReceived -> if (event.message.cid == cid && messageCache.none { it.msgId.contentEquals(event.message.msgId) }) {
                if (messageCache.size == 200) messageCache.removeAt(0)
                messageCache.add(event.message)
                _uiState.value = _uiState.value.copy(messages = messageCache.toList())
            }
            is CoreEvent.MessageDelivered -> if (event.cid == cid) {
                val idx = messageCache.indexOfFirst { it.msgId.contentEquals(event.msgId) }
                if (idx >= 0) { messageCache[idx] = messageCache[idx].copy(flags = (messageCache[idx].flags.toInt() or 2).toByte()); _uiState.value = _uiState.value.copy(messages = messageCache.toList()) }
            }
            is CoreEvent.ContactJoined -> if (event.cid == cid) _uiState.value = _uiState.value.copy(isOnline = true)
            else -> Unit
        }
    }
}
