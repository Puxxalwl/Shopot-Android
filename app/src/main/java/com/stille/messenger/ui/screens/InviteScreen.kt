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
import com.stille.messenger.ui.theme.StilleColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(state: InviteState, onCreate: () -> Unit, onAccept: (String) -> Unit, onRetryCore: () -> Unit = {}, onClose: () -> Unit) {
    val english = StilleColors.language == "en"
    val context = LocalContext.current
    var mode by remember { mutableStateOf("share") }
    var input by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
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
                Text(if (english) "Local core is unavailable. Invitations need a running shopot core." else "Локальное ядро недоступно. Для приглашений нужен запущенный shopot.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetryCore, enabled = !state.busy) {
                    Text(if (english) "Retry core" else "Повторить запуск ядра")
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
                Text(if (english) "Single-use. Valid until the core restarts. Without a direct address, connect on the same local network. Share only with the intended person." else "Одноразовое. Действует до перезапуска ядра. Без прямого адреса подключение работает в одной локальной сети. Передавайте только нужному человеку.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !scanning, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (english) "Read QR from an image" else "Считать QR с изображения")
                }
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (scanError) Text(if (english) "No Stille invitation found in this image" else "На изображении не найдено приглашение Stille", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(input, { if (it.length <= 4096) input = it }, Modifier.fillMaxWidth(), label = { Text(if (english) "Invitation link" else "Ссылка-приглашение") }, placeholder = { Text("shopot://i/…") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(20.dp))
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onAccept(input); input = "" }, enabled = input.isNotBlank() && state.ready && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
                    Text(if (english) "Connect" else "Подключиться")
                }
                if (state.accepted) Text(if (english) "Invitation accepted. Waiting for an authenticated connection…" else "Приглашение принято. Ждём подтверждённого подключения…", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
            if (state.joined) Text(if (english) "Connection confirmed" else "Подключение подтверждено", Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary)
            state.error?.let { error ->
                val text = when (error) {
                    InviteError.CORE_UNAVAILABLE -> if (english) "Core is unavailable" else "Ядро недоступно"
                    InviteError.INVALID_CODE -> if (english) "Invalid invitation. Ask for a new one." else "Приглашение недействительно. Попросите новое."
                    InviteError.LIMIT_REACHED -> if (english) "Four invitations are already active. Use an existing one." else "Уже созданы четыре приглашения. Используйте существующее."
                    InviteError.REQUEST_FAILED -> if (english) "Could not complete the request. Try again." else "Не удалось выполнить запрос. Попробуйте ещё раз."
                }
                Text(text, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
