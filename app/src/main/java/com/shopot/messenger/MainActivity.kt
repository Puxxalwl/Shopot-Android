package com.shopot.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shopot.coreapi.Contact
import com.shopot.coreapi.Message
import com.shopot.messenger.ui.components.BottomNavItem
import com.shopot.messenger.ui.components.GlassSurface
import com.shopot.messenger.ui.screens.ChatScreen
import com.shopot.messenger.ui.screens.ChatsScreen
import com.shopot.messenger.ui.screens.ContactsScreen
import com.shopot.messenger.ui.screens.ProfileScreen
import com.shopot.messenger.ui.screens.SettingsScreen
import com.shopot.messenger.ui.screens.WallpaperBackground
import com.shopot.messenger.ui.theme.ShopotTheme
import com.shopot.messenger.service.ShopotService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShopotService.start(this)
        setContent {
            ShopotTheme {
                ShopotApp()
            }
        }
    }
}

@Composable
fun ShopotApp() {
    var currentRoute by rememberSaveable { mutableStateOf("chats") }
    var contacts by rememberSaveable { mutableStateOf(emptyList<Contact>()) }
    var currentChatCid by rememberSaveable { mutableStateOf<Int?>(null) }
    var messages by rememberSaveable { mutableStateOf(emptyList<Message>()) }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf("ru") }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }

    val bottomNavItems = listOf(
        BottomNavItem("Профиль", Icons.Default.Person, "profile"),
        BottomNavItem("Чаты", Icons.Default.ChatBubble, "chats"),
        BottomNavItem("Контакты", Icons.Default.Group, "contacts"),
        BottomNavItem("Настройки", Icons.Default.Settings, "settings")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground()

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    cornerRadius = 0.dp
                ) {
                    NavigationBar(containerColor = Color.Transparent) {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = if (currentRoute == item.route) Color(0xFF007AFF) else Color(0x80FFFFFF)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        color = if (currentRoute == item.route) Color(0xFF007AFF) else Color(0x80FFFFFF)
                                    )
                                },
                                selected = currentRoute == item.route,
                                onClick = {
                                    currentRoute = item.route
                                    currentChatCid = null
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color(0x10FFFFFF)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                Crossfade(targetState = currentRoute) { route ->
                    when (route) {
                        "chats" -> {
                            if (currentChatCid != null) {
                                val contact = contacts.find { it.cid == currentChatCid }
                                if (contact != null) {
                                    ChatScreen(
                                        cid = contact.cid,
                                        contactName = contact.name,
                                        contactOnline = contact.online,
                                        messages = messages,
                                        onSendMessage = { _, _ -> },
                                        onBack = { currentChatCid = null }
                                    )
                                }
                            } else {
                                ChatsScreen(
                                    onChatClick = { cid -> currentChatCid = cid },
                                    contacts = contacts,
                                    lastMessage = emptyMap(),
                                    lastMessageTime = emptyMap()
                                )
                            }
                        }
                        "contacts" -> ContactsScreen(contacts = contacts)
                        "profile" -> ProfileScreen(
                            name = "User",
                            avatarBytes = null,
                            onSetName = { },
                            onSetAvatar = { }
                        )
                        "settings" -> SettingsScreen(
                            darkTheme = darkTheme,
                            language = language,
                            notificationsEnabled = notificationsEnabled,
                            onToggleDarkTheme = { darkTheme = it },
                            onToggleLanguage = { language = if (language == "ru") "en" else "ru" },
                            onToggleNotifications = { notificationsEnabled = it }
                        )
                    }
                }
            }
        }
    }
}
