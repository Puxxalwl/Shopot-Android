package com.stille.messenger.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.stille.coreapi.Contact
import com.stille.coreapi.Entity
import com.stille.coreapi.Message
import com.stille.messenger.ui.components.ChatBubble
import com.stille.messenger.ui.components.GlassSurface
import com.stille.messenger.ui.components.MenuItem
import com.stille.messenger.ui.components.ThreeDotMenu
import com.stille.messenger.ui.components.TopAppBarWithSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Wallpaper background (blur source) ────────────────────────────────────

@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wallpaperBitmap = try {
        val wm = android.app.WallpaperManager.getInstance(context)
        val drawable = wm.drawable
        drawable?.let {
            val bmp = android.graphics.Bitmap.createBitmap(
                it.intrinsicWidth.coerceAtLeast(1),
                it.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            it.setBounds(0, 0, canvas.width, canvas.height)
            it.draw(canvas)
            bmp.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    }

    if (wallpaperBitmap != null) {
        Image(
            bitmap = wallpaperBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFE0E0E0), Color(0xFFBDBDBD))
                    )
                )
        )
    }
}

fun formatTime(tsMs: Long): String {
    if (tsMs == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - tsMs
    return when {
        diff < 60_000 -> "сейчас"
        diff < 24 * 3600_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tsMs))
        diff < 7 * 24 * 3600_000 -> SimpleDateFormat("EEE", Locale("ru")).format(Date(tsMs))
        else -> SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(tsMs))
    }
}

// ─── Chats list screen ─────────────────────────────────────────────────────

@Composable
fun ChatsScreen(
    onChatClick: (Int) -> Unit,
    navController: NavHostController? = null,
    modifier: Modifier = Modifier,
    contacts: List<Contact> = emptyList(),
    lastMessage: Map<Int, Message?> = emptyMap(),
    lastMessageTime: Map<Int, Long> = emptyMap()
) {
    Column(modifier = modifier.fillMaxSize()) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 0.dp
        ) {
            TopAppBarWithSearch(
                title = "Чаты",
                searchHint = "Поиск контактов и сообщений",
                onSearchChange = { },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(
                items = contacts.sortedByDescending { lastMessageTime[it.cid] ?: 0L },
                key = { it.cid }
            ) { contact ->
                ChatItem(
                    contact = contact,
                    lastMessage = lastMessage[contact.cid],
                    timeStr = formatTime(lastMessageTime[contact.cid] ?: 0L),
                    onClick = { onChatClick(contact.cid) }
                )
            }
        }
    }
}

@Composable
fun ChatItem(
    contact: Contact,
    lastMessage: Message?,
    timeStr: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).height(72.dp),
        cornerRadius = 16.dp
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF007AFF))
                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(contact.name, color = Color(0xFF121212), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(timeStr, color = Color(0x80121212), fontSize = 12.sp)
                    }
                    Text(
                        text = lastMessage?.body ?: "Нет сообщений",
                        color = if (lastMessage?.mine == true) Color(0xFF007AFF) else Color(0x80121212),
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ─── Chat screen ───────────────────────────────────────────────────────────
@Composable
fun ChatScreen(
    cid: Int,
    contactName: String,
    contactOnline: Boolean,
    messages: List<Message>,
    modifier: Modifier = Modifier,
    onSendMessage: (String, List<Entity>) -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var messageText by rememberSaveable { mutableStateOf(TextFieldValue("")) }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = Color(0xFF007AFF)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contactName.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = contactName,
                        color = Color(0xFF121212),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (contactOnline) "в сети" else "не в сети",
                        color = if (contactOnline) Color(0xFF34C759) else Color(0x80121212),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { /* Call */ }) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = Color(0xFF007AFF)
                    )
                }
                ThreeDotMenu(
                    items = listOf(
                        MenuItem("Инфо о контакте"),
                        MenuItem("Закрепить чат"),
                        MenuItem("Удалить чат"),
                        MenuItem("Заблокировать")
                    ),
                    onItemSelected = { },
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = messages.reversed(),
                key = { it.msgId.joinToString("") { b -> "%02x".format(b) } }
            ) { message ->
                MessageRow(message = message)
            }
        }

        // Input bar
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* attach */ }) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = null,
                        tint = Color(0x80121212)
                    )
                }
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Сообщение...", color = Color(0x80121212)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x18FFFFFF),
                        unfocusedContainerColor = Color(0x18FFFFFF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color(0xFF121212),
                        unfocusedTextColor = Color(0xFF121212)
                    ),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.text.isNotBlank()) {
                                onSendMessage(messageText.text, emptyList())
                                messageText = TextFieldValue("")
                            }
                            focusManager.clearFocus()
                        }
                    )
                )
                IconButton(
                    onClick = {
                        if (messageText.text.isNotBlank()) {
                            onSendMessage(messageText.text, emptyList())
                            messageText = TextFieldValue("")
                        }
                        focusManager.clearFocus()
                    },
                    enabled = messageText.text.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = if (messageText.text.isNotBlank()) Color(0xFF007AFF) else Color(0x40121212)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageRow(message: Message) {
    ChatBubble(
        isMine = message.mine,
        delivered = message.delivered,
        body = message.body,
        timestamp = formatTime(message.tsMs)
    )
}

// ─── Profile screen ────────────────────────────────────────────────────────
@Composable
fun ProfileScreen(
    name: String = "",
    avatarBytes: ByteArray? = null,
    onSetName: (String) -> Unit,
    onSetAvatar: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        item {
            Spacer(Modifier.height(32.dp))

            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF007AFF), Color(0xFF0055CC))
                        )
                    )
                    .border(2.dp, Color(0x40FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBytes != null) {
                    Image(
                        bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(100.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = name.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Имя", color = Color(0x80121212), fontSize = 12.sp)
                    TextField(
                        value = name,
                        onValueChange = { onSetName(com.stille.messenger.ui.ProfileName.limitInput(it)) },
                        supportingText = {
                            Text("${com.stille.messenger.ui.ProfileName.byteLength(name.trim())} / 32 UTF-8")
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x10FFFFFF),
                            unfocusedContainerColor = Color(0x10FFFFFF),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color(0xFF121212),
                            unfocusedTextColor = Color(0xFF121212)
                        )
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Contacts screen ───────────────────────────────────────────────────────
@Composable
fun ContactsScreen(
    contacts: List<Contact> = emptyList(),
    onContactClick: (Contact) -> Unit = {}
) {
    Column(Modifier.fillMaxSize()) {
        GlassSurface(
            Modifier.fillMaxWidth(),
            cornerRadius = 0.dp
        ) {
            TopAppBarWithSearch(
                title = "Контакты",
                searchHint = "Поиск контактов",
                onSearchChange = { },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(contacts, key = { it.cid }) { contact ->
                ContactItem(contact = contact, onClick = { onContactClick(contact) })
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact, onClick: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(64.dp),
        cornerRadius = 16.dp
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = contact.name,
                    color = Color(0xFF121212),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
