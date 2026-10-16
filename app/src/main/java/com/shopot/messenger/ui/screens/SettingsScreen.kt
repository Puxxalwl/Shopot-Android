package com.shopot.messenger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.MaterialTheme
import com.shopot.messenger.ui.components.GlassSurface
import com.shopot.messenger.ui.components.TopAppBarWithSearch
import com.shopot.messenger.ui.theme.BlueAccent

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    language: String = "ru",  // "en" or "ru"
    notificationsEnabled: Boolean = true,
    onToggleDarkTheme: (Boolean) -> Unit = {},
    onToggleLanguage: () -> Unit = {},
    onToggleNotifications: (Boolean) -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        WallpaperBackground()

        Column(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                cornerRadius = 0.dp,
            ) {
                TopAppBarWithSearch(
                    title = "Настройки",
                    searchHint = "",
                    onSearchChange = {},
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            LazyColumnContent(
                darkTheme = darkTheme,
                language = language,
                notificationsEnabled = notificationsEnabled,
                onToggleDarkTheme = onToggleDarkTheme,
                onToggleLanguage = onToggleLanguage,
                onToggleNotifications = onToggleNotifications
            )
        }
    }
}

@Composable
private fun LazyColumnContent(
    darkTheme: Boolean,
    language: String,
    notificationsEnabled: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleLanguage: () -> Unit,
    onToggleNotifications: (Boolean) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(60.dp),
                cornerRadius = 16.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onToggleDarkTheme(!darkTheme) }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = BlueAccent
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Тёмная тема", color = Color(0xFF121212), fontSize = 15.sp)
                        Text("Включить тёмную тему", color = Color(0x80121212), fontSize = 12.sp)
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onToggleDarkTheme,
                        colors = SwitchDefaults.colors(checkedThumbColor = BlueAccent)
                    )
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(60.dp),
                cornerRadius = 16.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleLanguage) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = BlueAccent
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Язык интерфейса", color = Color(0xFF121212), fontSize = 15.sp)
                        Text(
                            if (language == "ru") "Русский" else "English",
                            color = Color(0x80121212), fontSize = 12.sp
                        )
                    }
                    Text(
                        text = if (language == "ru") "РУ" else "EN",
                        color = BlueAccent,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(60.dp),
                cornerRadius = 16.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onToggleNotifications(!notificationsEnabled) }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = BlueAccent
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Уведомления", color = Color(0xFF121212), fontSize = 15.sp)
                        Text("Оповещать о новых сообщениях", color = Color(0x80121212), fontSize = 12.sp)
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onToggleNotifications,
                        colors = SwitchDefaults.colors(checkedThumbColor = BlueAccent)
                    )
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .height(60.dp),
                cornerRadius = 16.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = BlueAccent
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("О программе", color = Color(0xFF121212), fontSize = 15.sp)
                        Text("Shopot Messager v0.1.0", color = Color(0x80121212), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
