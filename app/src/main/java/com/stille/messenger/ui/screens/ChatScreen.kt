package com.stille.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import com.stille.coreapi.Contact
import com.stille.coreapi.Entity
import com.stille.coreapi.Message
import com.stille.messenger.ui.ProfileName
import com.stille.messenger.ui.components.*
import com.stille.messenger.ui.theme.StilleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun copy(ru: String, en: String) = if (StilleColors.language == "en") en else ru

@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    // Read animation values only during drawing: no per-frame composition/layout.
    val midnight = androidx.compose.animation.core.animateFloatAsState(
        if (StilleColors.isDark) 1f else 0f,
        androidx.compose.animation.core.tween(280), label = "midnight-wallpaper"
    )
    Box(modifier.fillMaxSize().drawWithCache {
        fun glow(color: androidx.compose.ui.graphics.Color, x: Float, y: Float) =
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(color, androidx.compose.ui.graphics.Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * y),
                radius = size.width * 1.25f
            )
        val lightTop = glow(androidx.compose.ui.graphics.Color(0xFFD8E6FF), 0.05f, 0.08f)
        val lightLower = glow(androidx.compose.ui.graphics.Color(0xFFE8DFF7), 1f, 0.7f)
        val darkTop = glow(androidx.compose.ui.graphics.Color(0xFF172F56), 0.05f, 0.08f)
        val darkLower = glow(androidx.compose.ui.graphics.Color(0xFF192742), 1f, 0.7f)
        onDrawBehind {
            val progress = midnight.value
            drawRect(androidx.compose.ui.graphics.lerp(
                androidx.compose.ui.graphics.Color(0xFFF7F7F9),
                androidx.compose.ui.graphics.Color(0xFF080F20), progress
            ))
            if (progress < 1f) {
                drawRect(lightTop, alpha = 1f - progress)
                drawRect(lightLower, alpha = 1f - progress)
            }
            if (progress > 0f) {
                drawRect(darkTop, alpha = progress)
                drawRect(darkLower, alpha = progress)
            }
        }
    })
}

fun formatTime(tsMs: Long, language: String = "ru"): String {
    if (tsMs == 0L) return ""
    val diff = System.currentTimeMillis() - tsMs
    val locale = Locale(language)
    return when {
        diff in 0 until 60_000 -> if (language == "en") "now" else "сейчас"
        diff < 24 * 3600_000 -> SimpleDateFormat("HH:mm", locale).format(Date(tsMs))
        diff < 7 * 24 * 3600_000 -> SimpleDateFormat("EEE", locale).format(Date(tsMs))
        else -> SimpleDateFormat("dd.MM", locale).format(Date(tsMs))
    }
}

@Composable
fun ChatsScreen(
    onChatClick: (Int) -> Unit,
    navController: NavHostController? = null,
    modifier: Modifier = Modifier,
    contacts: List<Contact> = emptyList(),
    lastMessage: Map<Int, Message?> = emptyMap(),
    lastMessageTime: Map<Int, Long> = emptyMap(),
    avatarBytes: Map<Int, ByteArray> = emptyMap(),
    onInvite: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sorted = remember(contacts, lastMessageTime) { contacts.sortedByDescending { lastMessageTime[it.cid] ?: 0L } }
    val visible = remember(sorted, query) { com.stille.messenger.ui.filterContacts(sorted, query) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(copy("Чаты", "Chats"), Modifier.weight(1f))
            FilledTonalIconButton(onClick = onInvite) { Icon(Icons.Outlined.PeopleOutline, copy("Пригласить", "Invite")) }
        }
        TopAppBarWithSearch("", copy("Поиск", "Search"), { query = it }, Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp))
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                QuietEmptyState(
                    if (query.isBlank()) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.SearchOff,
                    if (query.isBlank()) copy("Здесь начнётся разговор", "A conversation starts here") else copy("Ничего не найдено", "No results"),
                    if (query.isBlank()) copy("Ваши разговоры появятся здесь,\nкогда будет подключён контакт.", "Your conversations appear here\nwhen a contact is connected.") else copy("Попробуйте другое имя", "Try another name")
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(visible, key = { it.cid }, contentType = { "chat" }) { contact ->
                    ChatItem(contact, lastMessage[contact.cid], formatTime(lastMessageTime[contact.cid] ?: 0L, StilleColors.language), { onChatClick(contact.cid) }, avatarBytes = avatarBytes[contact.cid])
                }
            }
        }
    }
}

@Composable
fun ChatItem(contact: Contact, lastMessage: Message?, timeStr: String, onClick: () -> Unit, modifier: Modifier = Modifier, avatarBytes: ByteArray? = null) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(contact.name, avatarBytes)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(timeStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(5.dp))
                Text(lastMessage?.body ?: copy("Пока нет сообщений", "No messages yet"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(Modifier.padding(start = 90.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun ChatScreen(
    cid: Int,
    contactName: String,
    contactOnline: Boolean,
    messages: List<Message>,
    avatarBytes: ByteArray? = null,
    modifier: Modifier = Modifier,
    onSendMessage: (String, List<Entity>) -> Unit,
    onBack: () -> Unit
) {
    var draft by rememberSaveable(cid) { mutableStateOf("") }
    // Cache the bounded, deduplicated render list; typing must not reverse/format history.
    // Message.equals currently ignores delivery flags: include them in the cache key.
    val deliveryFlags = messages.map { it.flags }
    val rows = remember(messages, deliveryFlags) { com.stille.messenger.ui.visibleMessages(messages) }
    val send = {
        if (draft.isNotBlank()) {
            onSendMessage(draft, emptyList())
            draft = ""
        }
    }
    Column(modifier.fillMaxSize().imePadding()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, copy("Назад", "Back"), tint = MaterialTheme.colorScheme.primary) }
                AvatarImage(contactName, avatarBytes, size = 40.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 16.dp)) {
                    Text(contactName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (contactOnline) copy("В сети", "Online") else copy("Не в сети", "Offline"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), reverseLayout = true, contentPadding = PaddingValues(vertical = 16.dp)) {
            items(rows, key = { it.first }, contentType = { "message" }) { (_, message) -> MessageRow(message) }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(copy("Сообщение", "Message")) },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = send, enabled = draft.isNotBlank(), modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowUpward, copy("Отправить", "Send"))
                }
            }
        }
    }
}

@Composable
fun MessageRow(message: Message) {
    ChatBubble(message.mine, message.delivered, message.body, timestamp = formatTime(message.tsMs, StilleColors.language), entities = message.entities)
}

@Composable
fun ProfileScreen(name: String = "", avatarBytes: ByteArray? = null,
    avatarState: com.stille.messenger.service.AvatarState = com.stille.messenger.service.AvatarState(),
    coreReady: Boolean = false, onRetryCore: () -> Unit = {}, onSetName: (String) -> Unit, onSetAvatar: () -> Unit) {
    var draft by rememberSaveable(name) { mutableStateOf(name) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle(copy("Профиль", "Profile")) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                AvatarImage(name, avatarBytes, size = 96.dp)
                TextButton(onClick = onSetAvatar, enabled = coreReady && !avatarState.busy) {
                    Text(if (avatarBytes == null) copy("Установить фото", "Set photo") else copy("Изменить фото", "Change photo"))
                }
                if (avatarState.busy) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(copy("Подготовка и сохранение…", "Preparing and saving…"), style = MaterialTheme.typography.bodySmall)
                }
                if (!coreReady) {
                    Text(copy("Фото можно сохранить после подключения", "Connect to save your photo"), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRetryCore) { Text(copy("Повторить", "Try again")) }
                }
                if (avatarState.saved) Text(copy("Фото сохранено", "Photo saved"), color = MaterialTheme.colorScheme.primary)
                avatarState.error?.let { error ->
                    Text(when (error) {
                        com.stille.messenger.service.AvatarError.INVALID_IMAGE -> copy("Не удалось прочитать фото. Выберите другое изображение до 8 МБ.", "Could not read photo. Choose another image up to 8 MB.")
                        com.stille.messenger.service.AvatarError.CORE_UNAVAILABLE -> copy("Нет подключения. Повторите попытку.", "Not connected. Try again.")
                        com.stille.messenger.service.AvatarError.REQUEST_FAILED -> copy("Не удалось сохранить фото. Попробуйте ещё раз.", "Could not save photo. Please try again.")
                    }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(copy("Так вас видят собеседники", "This is how others see you"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text(copy("Имя", "Name"), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = draft, onValueChange = { draft = ProfileName.limitInput(it) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (ProfileName.isValid(draft)) onSetName(draft.trim()) })
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { onSetName(draft.trim()) }, enabled = ProfileName.isValid(draft) && draft.trim() != name, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                            Text(copy("Сохранить", "Save"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(contacts: List<Contact> = emptyList(), avatarBytes: Map<Int, ByteArray> = emptyMap(), onInvite: () -> Unit = {}, onContactClick: (Contact) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(contacts, query) { com.stille.messenger.ui.filterContacts(contacts, query).sortedBy { it.name.lowercase() } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(copy("Контакты", "Contacts"), Modifier.weight(1f))
            FilledTonalIconButton(onClick = onInvite) { Icon(Icons.Outlined.PeopleOutline, copy("Пригласить", "Invite")) }
        }
        GlassSurface(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).fillMaxWidth(), cornerRadius = 24.dp) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onInvite).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PeopleOutline, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(copy("Пригласить в Stille", "Invite to Stille"), style = MaterialTheme.typography.titleMedium)
                    Text(copy("Ссылка или персональный QR", "Link or personal QR code"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TopAppBarWithSearch("", copy("Поиск", "Search"), { query = it }, Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp))
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                QuietEmptyState(Icons.Outlined.PeopleOutline, if (query.isBlank()) copy("Пока никого нет", "No contacts yet") else copy("Ничего не найдено", "No results"), if (query.isBlank()) copy("Подключённые контакты появятся здесь", "Connected contacts will appear here") else copy("Попробуйте другое имя", "Try another name"))
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(visible, key = { it.cid }, contentType = { "contact" }) { ContactItem(it, avatarBytes[it.cid]) { onContactClick(it) } }
        }
    }
}

@Composable
fun ContactItem(contact: Contact, avatarBytes: ByteArray? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(contact.name, avatarBytes, size = 46.dp)
            Text(contact.name, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider(Modifier.padding(start = 84.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
