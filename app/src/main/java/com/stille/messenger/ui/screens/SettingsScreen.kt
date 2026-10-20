package com.stille.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SystemUpdate
import com.stille.messenger.update.DownloadState
import com.stille.messenger.update.UpdateResult
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stille.messenger.BuildConfig
import com.stille.messenger.diag.RapidTapCounter
import com.stille.messenger.ui.components.GlassSurface
import com.stille.messenger.ui.components.ScreenTitle
import com.stille.messenger.ui.components.SegmentedChoice

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    language: String = "ru",
    notificationsEnabled: Boolean = true,
    onToggleDarkTheme: (Boolean) -> Unit = {},
    onToggleLanguage: () -> Unit = {},
    onToggleNotifications: (Boolean) -> Unit = {},
    themeMode: String = if (darkTheme) "dark" else "light",
    onThemeModeChange: (String) -> Unit = { onToggleDarkTheme(it == "dark") },
    recoverySection: (@Composable () -> Unit)? = null,
    checkUpdatesOnLaunch: Boolean = true,
    onCheckUpdatesOnLaunch: (Boolean) -> Unit = {},
    updateResult: UpdateResult? = null,
    updateChecking: Boolean = false,
    downloadState: DownloadState = DownloadState.Idle,
    onCheckUpdates: () -> Unit = {},
    onUpdateDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onAboutOpened: () -> Unit = {},
    hasUpdate: Boolean = updateResult is UpdateResult.Available
) {
    val english = language == "en"
    var about by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    // Hidden diagnostics entry: five rapid taps on "About Stille" open the
    // redacted log file instead of the About dialog.
    val aboutTaps = remember { RapidTapCounter() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle(if (english) "Settings" else "Настройки") }
        item {
            GlassSurface(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (english) "Appearance" else "Оформление", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(12.dp))
                    SegmentedChoice(
                        if (english) listOf("system" to "Auto", "light" to "Light", "dark" to "Dark") else listOf("system" to "Авто", "light" to "Светлое", "dark" to "Тёмное"),
                        themeMode, onThemeModeChange
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(if (english) "Language" else "Язык", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(12.dp))
                    SegmentedChoice(listOf("ru" to "Русский", "en" to "English"), language, { if (it != language) onToggleLanguage() })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
        recoverySection?.let { item { it() } }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            GlassSurface(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().toggleable(notificationsEnabled, role = Role.Switch, onValueChange = onToggleNotifications).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.NotificationsNone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (english) "Notifications" else "Уведомления", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        Switch(notificationsEnabled, onCheckedChange = null)
                    }
                    HorizontalDivider(Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().toggleable(checkUpdatesOnLaunch, role = Role.Switch, onValueChange = onCheckUpdatesOnLaunch).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.SystemUpdate, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (english) "Check updates on launch" else "Проверять обновления при запуске", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        Switch(checkUpdatesOnLaunch, onCheckedChange = null)
                    }
                    HorizontalDivider(Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (aboutTaps.tap()) {
                                about = false
                                diagnostics = true
                            } else {
                                about = true
                                onAboutOpened()
                            }
                        }.heightIn(min = 64.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (english) "About Stille" else "О Stille", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        if (hasUpdate) Box(Modifier.padding(end = 6.dp).width(8.dp).height(8.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                        Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("Stille") },
        text = {
            Column {
                Text(if (english) "An open-source messenger. No ads or tracking." else "Мессенджер с открытым кодом. Без рекламы и слежки.")
                Spacer(Modifier.height(16.dp))
                when {
                    updateChecking -> Text(if (english) "Checking for updates…" else "Проверяем обновления…")
                    updateResult is UpdateResult.Current -> Text(if (english) "The latest version of Stille is installed" else "Установлена последняя версия Stille")
                    updateResult is UpdateResult.Available -> Text(if (english) "A new Stille update is available: ${updateResult.release.version}" else "Доступно новое обновление Stille: ${updateResult.release.version}")
                    updateResult is UpdateResult.Error -> Text(if (english) "Update check failed: ${updateResult.message}" else "Не удалось проверить обновление: ${updateResult.message}", color = MaterialTheme.colorScheme.error)
                }
                if (downloadState is DownloadState.Running) {
                    val progress = downloadState.received.toFloat() / (downloadState.total ?: 1L).toFloat()
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    TextButton(onClick = onCancelDownload) { Text(if (english) "Cancel" else "Отменить") }
                } else if (downloadState is DownloadState.Ready) {
                    Text(if (english) "Download complete. Tap Update again to install." else "Загрузка завершена. Нажмите «Обновить» ещё раз для установки.")
                } else if (updateResult is UpdateResult.Available) {
                    Button(onClick = onUpdateDownload) { Text(if (english) "Update" else "Обновить") }
                }
                if (!updateChecking && updateResult is UpdateResult.Error) TextButton(onClick = onCheckUpdates) { Text(if (english) "Retry" else "Повторить") }
            }
        },
        confirmButton = { TextButton(onClick = { about = false }) { Text(if (english) "Done" else "Готово") } }
    )
    if (diagnostics) DiagnosticsDialog(english = english, onDismiss = { diagnostics = false })
}
