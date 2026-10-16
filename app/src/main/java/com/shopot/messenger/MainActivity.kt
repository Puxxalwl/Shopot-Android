package com.shopot.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import com.shopot.coreapi.Contact
import com.shopot.coreapi.Entity
import com.shopot.coreapi.Message
import com.shopot.coreapi.Method
import com.shopot.coreapi.Requests
import com.shopot.coreapi.Responses
import com.shopot.messenger.ui.components.GlassSurface
import com.shopot.messenger.ui.components.BottomNavItem
import com.shopot.messenger.ui.components.TopAppBarWithSearch
import com.shopot.messenger.ui.components.ThreeDotMenu
import com.shopot.messenger.ui.components.MenuItem
import com.shopot.messenger.ui.screens.WallpaperBackground
import com.shopot.messenger.ui.theme.ShopotTheme
import com.shopot.messenger.service.ShopotService
import com.shopot.messenger.R
import androidx.navigation.compose.rememberNavController
import java.text.SimpleDateFormat
import java.util.*

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
    var contacts by rememberSaveable { mutableStateOf<List<Contact>>(emptyList()) }
    var currentChatCid by rememberSaveable { mutableStateOf<Int?>(null) }
    var messages by rememberSaveable { mutableStateOf<List<Message>>(emptyList()) }
    var darkTheme by rememberSaveable { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf("ru") }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }

    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem("Профиль", Icons.Default.Person, "profile"),
        BottomNavItem("Чаты", Icons.Default.ChatBubble, "chats"),
        BottomNavItem("Контакты", Icons.Default.Group, "contacts"),
        BottomNavItem("Настройки", Icons.Default.Settings, "settings")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground()

        androidx.compose.material3.Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    cornerRadius = Dp.Value(0f),
                ) {
                    androidx.compose.material3.NavigationBar(containerColor = Color.Transparent) {
                        bottomNavItems.forEach { item ->
                            androidx.compose.material3.NavigationBarItem(
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
                                onClick = { currentRoute = item.route; currentChatCid = null },
                                colors = androidx.compose.material3.NavigationBarItemDefaults.navigationBarItemColors(
                                    containerColor = Color.Transparent,
                                    selectedIconContainerColor = Color(0x10FFFFFF)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
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
                                navController = navController,
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
