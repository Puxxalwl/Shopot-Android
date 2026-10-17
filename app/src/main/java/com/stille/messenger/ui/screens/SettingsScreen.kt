package com.stille.messenger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.stille.messenger.BuildConfig
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
    onThemeModeChange: (String) -> Unit = { onToggleDarkTheme(it == "dark") }
) {
    val english = language == "en"
    var about by rememberSaveable { mutableStateOf(false) }
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
                        Modifier.fillMaxWidth().clickable { about = true }.heightIn(min = 64.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (english) "About Stille" else "О Stille", Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        Text(BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("Stille") },
        text = { Text(if (english) "An open-source messenger client. No analytics or advertising SDKs. Powered by the local shopot-core." else "Открытый клиент мессенджера. Без аналитики и рекламных SDK. Работает с локальным shopot-core.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text(if (english) "Done" else "Готово") } }
    )
}
