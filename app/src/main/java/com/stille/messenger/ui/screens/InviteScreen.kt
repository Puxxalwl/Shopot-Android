package com.stille.messenger.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stille.messenger.service.InviteError
import com.stille.messenger.service.InviteState
import com.stille.messenger.ui.components.GlassSurface
import com.stille.messenger.ui.components.SegmentedChoice
import com.stille.messenger.ui.qr.InviteQrCode
import com.stille.messenger.ui.qr.LiveQrScanner
import com.stille.messenger.ui.theme.StilleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(
    state: InviteState,
    profileLink: com.stille.messenger.service.ProfileLinkState = com.stille.messenger.service.ProfileLinkState(),
    onCreate: () -> Unit,
    onAccept: (String) -> Unit,
    onRetryCore: () -> Unit = {},
    onLoadProfileLink: () -> Unit = {},
    onRotateProfileLink: () -> Unit = {},
    initialMode: String = "share",
    initialInput: String = "",
    onClose: () -> Unit
) {
    val english = StilleColors.language == "en"
    val context = LocalContext.current
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var input by remember(initialInput) { mutableStateOf(initialInput) }
    var copied by remember { mutableStateOf(false) }
    var linkCopied by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var liveScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            scanning = true
            scanError = false
            val decoded = com.stille.messenger.ui.readInviteImage(context.contentResolver, uri)
            scanning = false
            if (decoded == null) scanError = true else { input = decoded; mode = "accept" }
        }
    }
    if (liveScanning) {
        LiveQrScanner(
            onScanned = { decoded -> input = decoded; mode = "accept"; liveScanning = false },
            onClose = { liveScanning = false },
            language = if (english) "en" else "ru",
            onPhotoImport = { liveScanning = false; picker.launch("image/*") },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (english) "Let's connect" else "Ближе друг к другу", style = MaterialTheme.typography.headlineSmall)
                    Text(if (english) "An invitation, not a phone number" else "Приглашение вместо номера телефона", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, if (english) "Close" else "Закрыть") }
            }
            Spacer(Modifier.height(20.dp))
            SegmentedChoice(listOf("share" to if (english) "My invitation" else "Пригласить", "accept" to if (english) "Join" else "Добавить"), mode, { mode = it })
            Spacer(Modifier.height(20.dp))
            if (!state.ready) {
                Text(if (english) "Connection unavailable. Try again." else "Подключение недоступно. Попробуйте ещё раз.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetryCore, enabled = !state.busy) {
                    Text(if (english) "Try again" else "Повторить")
                }
                Spacer(Modifier.height(12.dp))
            }
            if (mode == "share") {
                val code = state.code
                if (code != null) {
                    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Stille", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(14.dp))
                            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(8.dp)) {
                                InviteQrCode(code, Modifier.fillMaxWidth().aspectRatio(1f), if (english) "Scan to connect on Stille" else "Отсканируйте, чтобы подключиться в Stille")
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(if (english) "One invitation. One connection." else "Одно приглашение. Одно подключение.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            val clip = ClipData.newPlainText("Stille invitation", code)
                            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            copied = true
                        }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (copied) { if (english) "Copied" else "Скопировано" } else { if (english) "Copy" else "Копировать" })
                        }
                        Button(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, code)
                            }, if (english) "Share invitation" else "Поделиться приглашением"))
                        }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (english) "Share" else "Поделиться")
                        }
                    }
                } else {
                    Icon(Icons.Outlined.PersonAdd, null, Modifier.padding(16.dp).size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (english) "Invite someone you trust" else "Пригласите того, кому доверяете", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onCreate, enabled = state.ready && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
                        Text(if (english) "Create invitation" else "Создать приглашение")
                    }
                }
                Spacer(Modifier.height(14.dp))
                val lifecycleText = when (state.lifecycle) {
                    com.stille.coreapi.InviteLifecycleState.ACTIVE -> if (english) "Waiting for the invitation to be accepted. One key is enough; no second code is needed." else "Ожидаем принятия приглашения. Достаточно одного кода — второй не нужен."
                    com.stille.coreapi.InviteLifecycleState.JOINING -> if (english) "The other device is joining…" else "Второе устройство подключается…"
                    com.stille.coreapi.InviteLifecycleState.CONSUMED -> if (english) "Friend request accepted and connection confirmed." else "Заявка принята, подключение подтверждено."
                    else -> if (english) "Use once, before the app restarts. Share only with the person you want to add." else "Одноразовый код действует до перезапуска ядра. Отправляйте только тому, кого хотите добавить."
                }
                Text(lifecycleText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                // ── Permanent invite link (persistent profile code, API 2.8.0) ──
                // The link carries the full code itself (stille:// stripped):
                // anyone who receives it can connect — no site mapping.
                GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (english) "Permanent link" else "Постоянная ссылка", style = MaterialTheme.typography.titleMedium)
                        Text(if (english) "One link for everyone — until you reset it" else "Одна ссылка для всех — пока вы её не смените", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(14.dp))
                        if (profileLink.busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else if (profileLink.error != null) {
                            val errorText = when (profileLink.error) {
                                com.stille.messenger.service.ProfileLinkError.CORE_UNAVAILABLE -> if (english) "Connection unavailable. Try again." else "Подключение недоступно. Повторите попытку."
                                com.stille.messenger.service.ProfileLinkError.CORE_TOO_OLD -> if (english) "The core version does not support the permanent link yet." else "Версия ядра ещё не поддерживает постоянную ссылку."
                                com.stille.messenger.service.ProfileLinkError.REQUEST_FAILED -> if (english) "Could not load the link. Try again." else "Не удалось загрузить ссылку. Повторите попытку."
                            }
                            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onLoadProfileLink, enabled = state.ready && !state.busy) {
                                Text(if (english) "Try again" else "Повторить")
                            }
                        } else if (profileLink.link != null) {
                            val link = profileLink.link
                            // The QR encodes the link itself: it carries the
                            // complete persistent code, so a stranger scanning
                            // it connects directly — no site round-trip.
                            Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(8.dp)) {
                                InviteQrCode(link, Modifier.fillMaxWidth().aspectRatio(1f), if (english) "Scan to add this person on Stille" else "Отсканируйте, чтобы добавить этого человека в Stille")
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(if (english) "The link carries the invitation itself — anyone who receives it can add you. It never burns." else "Ссылка содержит само приглашение — получивший её сможет вас добавить. Она не «сгорает».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text(link, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = {
                                    val clip = ClipData.newPlainText("Stille profile link", link)
                                    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                    linkCopied = true
                                }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(if (linkCopied) { if (english) "Copied" else "Скопировано" } else { if (english) "Copy" else "Копировать" })
                                }
                                Button(onClick = {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link)
                                    }, if (english) "Share permanent link" else "Поделиться постоянной ссылкой"))
                                }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                                    Text(if (english) "Share" else "Поделиться")
                                }
                            }
                            if (profileLink.rotated) {
                                Spacer(Modifier.height(8.dp))
                                Text(if (english) "Link reset — previously shared copies no longer work." else "Ссылка сброшена — ранее отправленные копии больше не работают.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { confirmRotate = true }, enabled = !profileLink.busy) {
                                Text(if (english) "Reset link" else "Сбросить ссылку", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            OutlinedButton(onClick = onLoadProfileLink, enabled = state.ready && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (english) "Show my permanent link" else "Показать постоянную ссылку")
                            }
                        }
                    }
                }
                if (confirmRotate) {
                    AlertDialog(
                        onDismissRequest = { confirmRotate = false },
                        title = { Text(if (english) "Reset the permanent link?" else "Сбросить постоянную ссылку?") },
                        text = {
                            Text(if (english)
                                "Previously shared links stop validating immediately; existing contacts and chats are unaffected. You will get a new link — share that one from now on."
                            else
                                "Ранее отправленные ссылки немедленно перестают действовать; существующие контакты и чаты не затрагиваются. Вы получите новую ссылку — делитесь впредь ею.")
                        },
                        confirmButton = {
                            TextButton(onClick = { confirmRotate = false; onRotateProfileLink() }) {
                                Text(if (english) "Reset" else "Сбросить")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRotate = false }) {
                                Text(if (english) "Cancel" else "Отмена")
                            }
                        }
                    )
                }
            } else {
                Button(onClick = { liveScanning = true }, enabled = !scanning, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (english) "Scan with camera" else "Сканировать камерой")
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !scanning && !liveScanning, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (english) "Choose QR image" else "Выбрать изображение QR")
                }
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (scanError) Text(if (english) "No Stille invitation found in this image" else "На изображении не найдено приглашение Stille", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(input, { if (it.length <= 4096) input = it }, Modifier.fillMaxWidth(), label = { Text(if (english) "Invitation link" else "Ссылка-приглашение") }, placeholder = { Text(if (english) "stille://i/… or stille.im/invite/i/…" else "stille://i/… или stille.im/invite/i/…") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(20.dp))
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onAccept(input); input = "" }, enabled = input.isNotBlank() && state.ready && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(if (english) "Connect" else "Подключиться")
                }
                if (state.accepted) {
                    val status = when (state.connection?.state) {
                        com.stille.coreapi.ConnState.DISCOVERING, com.stille.coreapi.ConnState.PENDING -> if (english) "Request accepted. Looking for the other device…" else "Заявка принята. Ищем второе устройство…"
                        com.stille.coreapi.ConnState.DIALING, com.stille.coreapi.ConnState.HANDSHAKING, com.stille.coreapi.ConnState.JOINING -> if (english) "Connecting securely…" else "Безопасное подключение…"
                        com.stille.coreapi.ConnState.FAILED -> if (english) "Connection failed; the core will retry automatically." else "Не удалось подключиться; ядро попробует снова."
                        else -> if (english) "Request accepted. Waiting for authenticated connection…" else "Заявка принята. Ожидаем подтверждённого подключения…"
                    }
                    Text(status, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            if (state.joined) Text(if (english) "Connection confirmed" else "Подключение подтверждено", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary)
            state.error?.let { error ->
                val text = when (error) {
                    InviteError.CORE_UNAVAILABLE -> if (english) "Connection unavailable. Try again." else "Подключение недоступно. Повторите попытку."
                    InviteError.INVALID_CODE -> if (english) "Invalid invitation. Ask for a new one." else "Приглашение недействительно. Попросите новое."
                    InviteError.LIMIT_REACHED -> if (english) "Four invitations are already active. Use an existing one." else "Уже созданы четыре приглашения. Используйте существующее."
                    InviteError.RESYNC_REQUIRED -> if (english) "Contact data changed. Refreshing the list…" else "Список контактов изменился. Обновляем…"
                    InviteError.DISCOVERY_DISABLED -> if (english) "Local network discovery is disabled." else "Обнаружение устройств в локальной сети отключено."
                    InviteError.TRANSPORT_FAILED -> if (english) "The connection could not be established yet." else "Пока не удалось установить подключение."
                    InviteError.RELAY_UNAVAILABLE -> if (english) "The relay is unavailable." else "Ретранслятор недоступен."
                    InviteError.TOR_UNAVAILABLE -> if (english) "The Tor proxy is unavailable." else "Прокси Tor недоступен."
                    InviteError.REQUEST_FAILED -> if (english) "Could not complete the request. Try again." else "Не удалось выполнить запрос. Попробуйте ещё раз."
                }
                Text(text, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
