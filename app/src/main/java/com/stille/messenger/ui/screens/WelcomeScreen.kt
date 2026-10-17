package com.stille.messenger.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stille.messenger.R
import com.stille.messenger.ui.ProfileName
import com.stille.messenger.ui.components.GlassSurface
import com.stille.messenger.ui.components.SegmentedChoice

@Composable
fun WelcomeScreen(
    initialName: String,
    initialLanguage: String,
    initialThemeMode: String,
    onLanguagePreview: (String) -> Unit = {},
    onThemePreview: (String) -> Unit = {},
    onComplete: (name: String, language: String, themeMode: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var language by rememberSaveable { mutableStateOf(initialLanguage) }
    var theme by rememberSaveable { mutableStateOf(initialThemeMode) }
    var about by rememberSaveable { mutableStateOf(false) }
    val english = language == "en"
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
    WallpaperBackground()
    Column(
        Modifier.fillMaxSize()
            .statusBarsPadding().navigationBarsPadding().imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Image(painterResource(R.drawable.stille_logo), null, Modifier.size(88.dp))
            Spacer(Modifier.height(20.dp))
            Text("Stille", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                if (english) "Just you. And your conversations." else "Только вы. И ваши разговоры.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (english) "Your name" else "Ваше имя", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = ProfileName.limitInput(it) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(if (english) "How should we call you?" else "Как к вам обращаться?") },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(if (english) "Language" else "Язык", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    SegmentedChoice(listOf("ru" to "Русский", "en" to "English"), language, {
                        language = it
                        onLanguagePreview(it)
                    })
                    Spacer(Modifier.height(24.dp))
                    Text(if (english) "Appearance" else "Оформление", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    SegmentedChoice(
                        if (english) listOf("system" to "Auto", "light" to "Light", "dark" to "Dark") else listOf("system" to "Авто", "light" to "Светлое", "dark" to "Тёмное"),
                        theme, { theme = it; onThemePreview(it) }
                    )
                }
            }
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { onComplete(name.trim(), language, theme) },
                enabled = ProfileName.isValid(name),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (english) "Continue" else "Продолжить", style = MaterialTheme.typography.labelLarge) }
            TextButton(onClick = { about = true }) { Text(if (english) "Why Stille?" else "Почему Stille?", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text(if (english) "Why Stille?" else "Почему Stille?") },
        text = { Text(if (english)
            "Stille is an open-source client for shopot-core. No advertising SDKs or analytics. Connections and encryption are handled by the local core, not a Stille server.\n\nOpen source alone does not guarantee security. Your device, core version and connection settings matter too."
        else "Stille — открытый клиент для shopot-core. Без рекламных SDK и аналитики. Соединениями и шифрованием занимается локальное ядро, а не сервер Stille.\n\nОткрытый код сам по себе не гарантирует безопасность: важны защита устройства, версия ядра и настройки соединения.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text(if (english) "Got it" else "Понятно") } }
    )
}
