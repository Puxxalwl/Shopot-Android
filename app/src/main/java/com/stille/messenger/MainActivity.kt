package com.stille.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import com.stille.messenger.ui.viewmodel.ChatsViewModel
import com.stille.coreapi.Method
import com.stille.messenger.ui.theme.StilleTheme
import com.stille.messenger.update.UpdateController
import com.stille.messenger.update.UpdateResult
import com.stille.messenger.update.DownloadState
import com.stille.messenger.update.installUpdate
import com.stille.messenger.diag.StilleLog
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class MainActivity : ComponentActivity() {
    private val updates: UpdateController by viewModels()

    /** Full invite code extracted from an ACTION_VIEW deeplink, awaiting UI processing. */
    private val deeplink = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deeplink.value = parseInviteDeeplink(intent)
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
            var checkUpdatesOnLaunch by rememberSaveable { mutableStateOf(preferences.checkUpdatesOnLaunch) }
            val updateState = updates.state.collectAsState().value
            androidx.compose.runtime.LaunchedEffect(Unit) { updates.launchCheck(checkUpdatesOnLaunch) }
            androidx.compose.runtime.LaunchedEffect(checkUpdatesOnLaunch) { updates.setAutoEnabled(checkUpdatesOnLaunch) }
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
                            updates.markAutoCheckDeferred()
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
                                updates.markAutoCheckDeferred()
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
                        deeplink = deeplink.value,
                        onDeeplinkConsumed = { deeplink.value = null },
                        recovery = recovery,
                        updates = updates,
                        updateState = updateState,
                        checkUpdatesOnLaunch = checkUpdatesOnLaunch,
                        onToggleAutoUpdates = { enabled -> checkUpdatesOnLaunch = enabled; preferences.updateCheckUpdatesOnLaunch(enabled); updates.setAutoEnabled(enabled) },
                        onManualUpdateCheck = { updates.check(manual = true) },
                        onStartUpdateDownload = { host -> updates.startDownload(host) },
                        onCancelUpdateDownload = { updates.cancelDownload() },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deeplink.value = parseInviteDeeplink(intent)
    }

    /**
     * Extracts the invite code from an ACTION_VIEW intent — a stille:// code
     * or a stille.im/invite link (which carries the code itself). Never logs
     * the code, only that a recognized deeplink arrived.
     */
    private fun parseInviteDeeplink(intent: Intent): String? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val data = intent.dataString ?: return null
        val code = com.stille.messenger.ui.InviteDeeplinks.parse(data) ?: return null
        StilleLog.i("ui", "invite-deeplink", "code-present")
        return code
    }
}

@Composable
fun StilleApp(
    activity: android.app.Activity? = null,
    name: String,
    language: String,
    themeMode: String,
    deeplink: String? = null,
    onDeeplinkConsumed: () -> Unit = {},
    recovery: RecoveryController? = null,
    updates: UpdateController? = null,
    updateState: com.stille.messenger.update.UpdateUiState = com.stille.messenger.update.UpdateUiState(),
    checkUpdatesOnLaunch: Boolean = true,
    onToggleAutoUpdates: (Boolean) -> Unit = {},
    onManualUpdateCheck: () -> Unit = {},
    onStartUpdateDownload: (android.app.Activity) -> Unit = {},
    onCancelUpdateDownload: () -> Unit = {},
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit
) {
    val coreService = rememberStilleService()
    val contacts = coreService?.confirmedContacts?.collectStarted()?.value ?: emptyList()
    val chatsVm: ChatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val chatsState = chatsVm.uiState.collectAsState().value
    androidx.compose.runtime.LaunchedEffect(coreService, contacts) { chatsVm.attach(coreService, contacts) }
    val contactViews = coreService?.contactViews?.collectStarted()?.value ?: emptyList()
    val avatarMap = coreService?.avatars?.collectStarted()?.value ?: emptyMap()
    val inviteState = coreService?.inviteState?.collectStarted()?.value ?: com.stille.messenger.service.InviteState()
    val profileLinkState = coreService?.profileLink?.collectStarted()?.value ?: com.stille.messenger.service.ProfileLinkState()
    var showUpdateSheet by rememberSaveable { mutableStateOf(false) }
    var showInvite by rememberSaveable { mutableStateOf(false) }
    // Deeplink routing state: prefilled accept input and sheet mode.
    var pendingInviteInput by rememberSaveable { mutableStateOf("") }
    var pendingInviteMode by rememberSaveable { mutableStateOf("share") }
    val avatarState = coreService?.avatarState?.collectStarted()?.value ?: com.stille.messenger.service.AvatarState()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) coreService?.setAvatar(uri)
    }
    var currentChatCid by rememberSaveable { mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(updateState.showSheet, showInvite, currentChatCid, showUpdateSheet) {
        if (updateState.showSheet && !showInvite && currentChatCid == null && !showUpdateSheet) showUpdateSheet = true
    }

    // The update flow drives itself: permission screen when needed, and the
    // Android installer as soon as the verified download is ready. The user
    // only confirms the install (a platform requirement, not a design choice).
    androidx.compose.runtime.LaunchedEffect(updateState.download) {
        val host = activity ?: return@LaunchedEffect
        when (val download = updateState.download) {
            is DownloadState.Ready -> {
                StilleLog.i("update", "installer-launched")
                try { host.startActivity(installUpdate(host, download.file)) }
                catch (error: Exception) { StilleLog.e("update", "installer-launch-failed", error) }
            }
            DownloadState.NeedInstallPermission -> {
                host.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${host.packageName}"))
                )
            }
            else -> Unit
        }
    }

    // Returning from the install-permission screen resumes the download once
    // the permission is actually granted; nothing loops while it is not.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, updates) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val host = activity
                if (host != null && updates != null &&
                    updates.state.value.download is DownloadState.NeedInstallPermission &&
                    host.packageManager.canRequestPackageInstalls()
                ) {
                    updates.startDownload(host)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val currentMessages = currentChatCid?.let { chatsState.chatMessages[it] ?: emptyList() } ?: emptyList()
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

    if (showUpdateSheet) {
        val release = (updateState.result as? UpdateResult.Available)?.release
        if (release != null) com.stille.messenger.ui.screens.UpdateSheet(
            release = release, english = isEnglish,
            downloadState = updateState.download,
            onUpdate = {
                val host = activity
                if (host != null) {
                    when (updateState.download) {
                        is DownloadState.Ready -> {
                            try { host.startActivity(installUpdate(host, (updateState.download as DownloadState.Ready).file)) }
                            catch (_: Exception) { }
                        }
                        DownloadState.NeedInstallPermission -> {
                            host.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${host.packageName}"))
                            )
                        }
                        else -> updates?.startDownload(host)
                    }
                }
            },
            onCancelDownload = onCancelUpdateDownload,
            onLater = { showUpdateSheet = false; updates?.dismissSheet() }
        )
    }
    // A tapped invite link or code prefills the accept tab: the link carries
    // the complete invite itself, so no core round-trip is needed to route it.
    androidx.compose.runtime.LaunchedEffect(deeplink) {
        val code = deeplink ?: return@LaunchedEffect
        pendingInviteInput = code
        pendingInviteMode = "accept"
        showInvite = true
        onDeeplinkConsumed()
    }

    // The profile link is deterministic — (re)issuing the mode-4 invite on
    // every sheet open is idempotent and restores relay presence.
    androidx.compose.runtime.LaunchedEffect(showInvite, inviteState.ready, coreService) {
        if (showInvite && coreService != null && inviteState.ready) coreService.loadProfileLink(name)
    }

    if (showInvite) InviteSheet(
        state = inviteState,
        profileLink = profileLinkState,
        onCreate = { coreService?.createInvite(name) },
        onAccept = { raw ->
            // Links carrying the full code (stille.im/invite/…) resolve to the
            // code itself; anything else is passed through for core validation.
            val code = com.stille.messenger.ui.InviteDeeplinks.parse(raw) ?: raw.trim()
            coreService?.acceptInvite(code, name)
        },
        onRetryCore = { coreService?.retryCore() },
        onLoadProfileLink = { coreService?.loadProfileLink(name) },
        onRotateProfileLink = { coreService?.rotateProfileLink(name) },
        initialMode = pendingInviteMode,
        initialInput = pendingInviteInput,
        onClose = {
            showInvite = false
            pendingInviteInput = ""
            pendingInviteMode = "share"
        }
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
                    lastMessage = chatsState.lastMessages,
                    lastMessageTime = chatsState.lastMessageTime,
                    avatarBytes = avatarMap,
                    pinnedCid = chatsState.pinnedCid,
                    onChatAction = { cid, action ->
                        val method = when (action) {
                            "pin" -> Method.PIN_CHAT
                            "unpin" -> Method.UNPIN_CHAT
                            "delete" -> Method.DELETE_CHAT
                            "block" -> Method.BLOCK_CONTACT
                            "unblock" -> Method.UNBLOCK_CONTACT
                            else -> null
                        }
                        if (method != null) chatsVm.runChatAction(cid, method, coreService) {
                            if (method == Method.DELETE_CHAT && currentChatCid == cid) currentChatCid = null
                        }
                    },
                    onInvite = { showInvite = true }
                )
                "contacts" -> ContactsScreen(
                    contacts = contacts, contactViews = contactViews, avatarBytes = avatarMap,
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
                    checkUpdatesOnLaunch = checkUpdatesOnLaunch,
                    onCheckUpdatesOnLaunch = onToggleAutoUpdates,
                    updateResult = updateState.result,
                    updateChecking = updateState.checking,
                    downloadState = updateState.download,
                    onCheckUpdates = onManualUpdateCheck,
                    onAboutOpened = onManualUpdateCheck,
                    hasUpdate = updateState.result is com.stille.messenger.update.UpdateResult.Available && checkUpdatesOnLaunch,
                    onUpdateDownload = {
                        val host = activity
                        if (host != null) {
                            when (updateState.download) {
                                is DownloadState.Ready -> {
                                    try { host.startActivity(installUpdate(host, (updateState.download as DownloadState.Ready).file)) }
                                    catch (_: Exception) { }
                                }
                                DownloadState.NeedInstallPermission -> {
                                    host.startActivity(
                                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${host.packageName}"))
                                    )
                                }
                                else -> onStartUpdateDownload(host)
                            }
                        }
                    },
                    onCancelDownload = onCancelUpdateDownload,
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
                    messages = currentMessages, avatarBytes = avatarMap[contact.cid],
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                    onSendMessage = { body, entities, done -> chatsVm.sendMessage(contact.cid, body, entities, coreService, done) }, onBack = { currentChatCid = null }
                )
            }
        }
    }
}
