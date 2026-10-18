package com.stille.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.stille.messenger.ui.components.SwipeSections
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stille.coreapi.Contact
import com.stille.coreapi.Message
import com.stille.messenger.backup.PasskeyBackupKeys
import com.stille.messenger.backup.RecoveryController
import com.stille.messenger.service.StilleService
import com.stille.messenger.ui.SetupPreferences
import com.stille.messenger.ui.rememberStilleService
import com.stille.messenger.ui.rememberBoundStilleService
import com.stille.messenger.ui.collectStarted
import com.stille.messenger.ui.screens.InviteSheet
import com.stille.messenger.ui.screens.RestoreBackupDialog
import com.stille.messenger.ui.screens.RecoverySection
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
            var showRestore by rememberSaveable { mutableStateOf(false) }

            val recoveryScope = androidx.compose.runtime.rememberCoroutineScope()
            val recovery = remember {
                RecoveryController(this, PasskeyBackupKeys(BuildConfig.BACKUP_RP_ID), recoveryScope)
            }

            StilleTheme(themeMode = themeMode, language = language) {
                if (!setupCompleted) {
                    WelcomeScreen(
                        initialName = name,
                        initialLanguage = language,
                        initialThemeMode = themeMode,
                        onLanguagePreview = { language = it },
                        onThemePreview = { themeMode = it },
                        onRestore = { showRestore = true },
                        onComplete = { selectedName, selectedLanguage, selectedTheme ->
                            preferences.save(selectedName, selectedLanguage, selectedTheme)
                            name = selectedName
                            language = selectedLanguage
                            themeMode = selectedTheme
                            setupCompleted = true
                            StilleService.start(this@MainActivity)
                        }
                    )
                    if (showRestore) {
                        // Boot the daemon in unprovisioned recovery mode:
                        // no local seed is created, RESTORE_RECOVERY_BACKUP
                        // provisions the profile from the cloud backup.
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            StilleService.startRecovery(this@MainActivity)
                        }
                        val restoreService = rememberBoundStilleService()
                        RestoreBackupDialog(
                            activity = this,
                            controller = recovery,
                            service = restoreService,
                            language = language,
                            onRestored = { restoredName ->
                                // The core has already installed the seed and
                                // profile itself; only local setup completes here.
                                preferences.save(restoredName, language, themeMode)
                                name = restoredName
                                showRestore = false
                                setupCompleted = true
                            },
                            onDismiss = {
                                showRestore = false
                                // Leave recovery mode: stop the unprovisioned
                                // daemon; a normal setup will start it again.
                                StilleService.stop(this@MainActivity)
                            }
                        )
                    }
                } else {
                    StilleApp(
                        activity = this@MainActivity,
                        name = name,
                        language = language,
                        themeMode = themeMode,
                        recovery = recovery,
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
    activity: android.app.Activity? = null,
    name: String,
    language: String,
    themeMode: String,
    recovery: RecoveryController? = null,
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit
) {
    val coreService = rememberStilleService()
    val contacts = coreService?.confirmedContacts?.collectStarted()?.value ?: emptyList()
    val avatarMap = coreService?.avatars?.collectStarted()?.value ?: emptyMap()
    val inviteState = coreService?.inviteState?.collectStarted()?.value ?: com.stille.messenger.service.InviteState()
    var showInvite by rememberSaveable { mutableStateOf(false) }
    val avatarState = coreService?.avatarState?.collectStarted()?.value ?: com.stille.messenger.service.AvatarState()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) coreService?.setAvatar(uri)
    }
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
        onRetryCore = { coreService?.retryCore() },
        onClose = { showInvite = false }
    )
    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground()
        val selectedContact = contacts.find { it.cid == currentChatCid }
        // Keep pager composed behind a conversation to retain each section's scroll/draft state.
        SwipeSections(
            items = bottomNavItems,
            enabled = selectedContact == null && !showInvite,
            layout = { navigation, pages ->
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { alpha = if (selectedContact == null) 1f else 0f }
                        .then(if (selectedContact != null) Modifier.clearAndSetSemantics { } else Modifier),
                    containerColor = Color.Transparent,
                    bottomBar = { if (selectedContact == null) navigation() }
                ) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) { pages() }
                }
            }
        ) { route ->
            when (route) {
                "chats" -> ChatsScreen(
                    onChatClick = { currentChatCid = it },
                    contacts = contacts,
                    lastMessage = emptyMap(),
                    lastMessageTime = emptyMap(),
                    avatarBytes = avatarMap,
                    onInvite = { showInvite = true }
                )
                "contacts" -> ContactsScreen(
                    contacts = contacts, avatarBytes = avatarMap,
                    onInvite = { showInvite = true }, onContactClick = { currentChatCid = it.cid }
                )
                "profile" -> ProfileScreen(
                    name = name, avatarBytes = avatarMap[0], avatarState = avatarState,
                    coreReady = inviteState.ready, onRetryCore = { coreService?.retryCore() }, onSetName = onNameChange,
                    onSetAvatar = { avatarPicker.launch("image/*") }
                )
                "settings" -> SettingsScreen(
                    darkTheme = darkTheme, themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange, language = language,
                    notificationsEnabled = notificationsEnabled,
                    onToggleDarkTheme = { onThemeModeChange(if (it) "dark" else "light") },
                    onToggleLanguage = { onLanguageChange(if (language == "ru") "en" else "ru") },
                    onToggleNotifications = { notificationsEnabled = it },
                    recoverySection = recovery?.let { controller ->
                        activity?.let { host ->
                            {
                                RecoverySection(
                                    activity = host,
                                    controller = controller,
                                    service = coreService,
                                    language = language
                                )
                            }
                        }
                    }
                )
            }
        }
        // Retain outgoing content during the exit animation; back returns to the source section.
        var lastContact by remember { mutableStateOf<Contact?>(null) }
        androidx.compose.runtime.LaunchedEffect(selectedContact) {
            if (selectedContact != null) lastContact = selectedContact
        }
        AnimatedVisibility(
            visible = selectedContact != null,
            enter = slideInHorizontally(tween(200)) { it / 5 } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(180)) { it / 5 } + fadeOut(tween(140))
        ) {
            val contact = selectedContact ?: lastContact
            if (contact != null) Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                ChatScreen(
                    cid = contact.cid, contactName = contact.name, contactOnline = contact.online,
                    messages = messages, avatarBytes = avatarMap[contact.cid],
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                    onSendMessage = { _, _ -> }, onBack = { currentChatCid = null }
                )
            }
        }
    }
}
