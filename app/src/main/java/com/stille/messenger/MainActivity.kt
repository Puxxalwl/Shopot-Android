package com.stille.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stille.coreapi.Contact
import com.stille.coreapi.Message
import com.stille.messenger.service.StilleService
import com.stille.messenger.ui.SetupPreferences
import com.stille.messenger.ui.rememberStilleService
import com.stille.messenger.ui.collectStarted
import com.stille.messenger.ui.screens.InviteSheet
import com.stille.messenger.ui.components.BottomNavItem
import com.stille.messenger.ui.components.BottomBar
import com.stille.messenger.ui.screens.ChatScreen
import com.stille.messenger.ui.screens.ChatsScreen
import com.stille.messenger.ui.screens.ContactsScreen
import com.stille.messenger.ui.screens.ProfileScreen
import com.stille.messenger.ui.screens.SettingsScreen
import com.stille.messenger.ui.screens.WallpaperBackground
import com.stille.messenger.ui.screens.WelcomeScreen
import com.stille.messenger.ui.theme.StilleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setupPreferences = SetupPreferences(this)
        if (setupPreferences.completed) {
            StilleService.start(this)
        }
        setContent {
            val preferences = remember { setupPreferences }
            var setupCompleted by rememberSaveable { mutableStateOf(preferences.completed) }
            var name by rememberSaveable { mutableStateOf(preferences.name) }
            var language by rememberSaveable { mutableStateOf(preferences.language) }
            var themeMode by rememberSaveable { mutableStateOf(preferences.themeMode) }

            StilleTheme(themeMode = themeMode, language = language) {
                if (!setupCompleted) {
                    WelcomeScreen(
                        initialName = name,
                        initialLanguage = language,
                        initialThemeMode = themeMode,
                        onLanguagePreview = { language = it },
                        onThemePreview = { themeMode = it },
                        onComplete = { selectedName, selectedLanguage, selectedTheme ->
                            preferences.save(selectedName, selectedLanguage, selectedTheme)
                            name = selectedName
                            language = selectedLanguage
                            themeMode = selectedTheme
                            setupCompleted = true
                            StilleService.start(this@MainActivity)
                        }
                    )
                } else {
                    StilleApp(
                        name = name,
                        language = language,
                        themeMode = themeMode,
                        onNameChange = {
                            name = it
                            preferences.updateName(it)
                        },
                        onLanguageChange = {
                            language = it
                            preferences.updateLanguage(it)
                        },
                        onThemeModeChange = {
                            themeMode = it
                            preferences.updateThemeMode(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StilleApp(
    name: String,
    language: String,
    themeMode: String,
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit
) {
    var currentRoute by rememberSaveable { mutableStateOf("chats") }
    val coreService = rememberStilleService()
    val contacts = coreService?.confirmedContacts?.collectStarted()?.value ?: emptyList()
    val inviteState = coreService?.inviteState?.collectStarted()?.value ?: com.stille.messenger.service.InviteState()
    var showInvite by rememberSaveable { mutableStateOf(false) }
    var currentChatCid by rememberSaveable { mutableStateOf<Int?>(null) }
    var messages by rememberSaveable { mutableStateOf(emptyList<Message>()) }
    var notificationsEnabled by rememberSaveable { mutableStateOf(true) }
    androidx.activity.compose.BackHandler(enabled = currentChatCid != null) { currentChatCid = null }
    val darkTheme = com.stille.messenger.ui.theme.StilleColors.isDark

    val isEnglish = language == "en"
    val bottomNavItems = listOf(
        BottomNavItem(if (isEnglish) "Profile" else "Профиль", Icons.Default.Person, "profile"),
        BottomNavItem(if (isEnglish) "Chats" else "Чаты", Icons.Default.ChatBubble, "chats"),
        BottomNavItem(if (isEnglish) "People" else "Контакты", Icons.Default.Group, "contacts"),
        BottomNavItem(if (isEnglish) "Settings" else "Настройки", Icons.Default.Settings, "settings")
    )

    if (showInvite) InviteSheet(
        state = inviteState,
        onCreate = { coreService?.createInvite(name) },
        onAccept = { coreService?.acceptInvite(it, name) },
        onClose = { showInvite = false }
    )
    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (currentChatCid == null) BottomBar(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onItemSelected = {
                        currentRoute = it
                        currentChatCid = null
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                Crossfade(targetState = currentRoute, animationSpec = tween(durationMillis = 180), label = "main-route") { route ->
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
                                    lastMessageTime = emptyMap(),
                                    onInvite = { showInvite = true }
                                )
                            }
                        }
                        "contacts" -> ContactsScreen(contacts = contacts, onInvite = { showInvite = true }, onContactClick = {
                            currentChatCid = it.cid
                            currentRoute = "chats"
                        })
                        "profile" -> ProfileScreen(
                            name = name,
                            avatarBytes = null,
                            onSetName = onNameChange,
                            onSetAvatar = { }
                        )
                        "settings" -> SettingsScreen(
                            darkTheme = darkTheme,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            language = language,
                            notificationsEnabled = notificationsEnabled,
                            onToggleDarkTheme = { onThemeModeChange(if (it) "dark" else "light") },
                            onToggleLanguage = { onLanguageChange(if (language == "ru") "en" else "ru") },
                            onToggleNotifications = { notificationsEnabled = it }
                        )
                    }
                }
            }
        }
    }
}
