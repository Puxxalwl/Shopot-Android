package com.shopot.messenger.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shopot.messenger.R
import com.shopot.messenger.ui.components.GlassSurface
import com.shopot.messenger.ui.theme.BlueAccent
import com.shopot.messenger.ui.theme.ShopotColors

@Composable
fun WelcomeScreen(
    initialName: String,
    initialLanguage: String,
    initialThemeMode: String,
    onLanguagePreview: (String) -> Unit = {},
    onThemePreview: (String) -> Unit = {},
    onComplete: (name: String, language: String, themeMode: String) -> Unit
) {
    var name = androidx.compose.runtime.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialName) }
    var language = androidx.compose.runtime.rememberSaveable { androidx.compose.runtime.mutableStateOf(initialLanguage) }
    var themeMode = rememberSaveable { mutableStateOf(initialThemeMode) }
    val canContinue = name.value.trim().isNotEmpty()
    val isEnglish = language.value == "en"
    val isDark = ShopotColors.isDark
    val foreground = if (isDark) Color.White else Color(0xFF182235)
    val muted = if (isDark) Color(0xB3FFFFFF) else Color(0xFF71809B)
    val welcomeTitle = if (isEnglish) "Welcome to Shopot" else "Добро пожаловать в Shopot"
    val welcomeSubtitle = if (isEnglish) "Set up the app before your first chat" else "Настройте приложение под себя перед первым запуском"
    val nameQuestion = if (isEnglish) "What should we call you?" else "Как к вам обращаться?"
    val namePlaceholder = if (isEnglish) "Your name" else "Ваше имя"
    val languageTitle = if (isEnglish) "Interface language" else "Язык интерфейса"
    val themeTitle = if (isEnglish) "Theme" else "Тема"
    val startLabel = if (isEnglish) "Get started" else "Начать"

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Image(
                painter = painterResource(R.drawable.shopot_logo),
                contentDescription = "Shopot",
                modifier = Modifier.size(76.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(welcomeTitle, color = foreground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                welcomeSubtitle,
                color = muted,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(28.dp))

            GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(nameQuestion, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name.value,
                        onValueChange = { name.value = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = BlueAccent) },
                        placeholder = { Text(namePlaceholder, color = muted) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            keyboardType = KeyboardType.Text
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BlueAccent,
                            unfocusedBorderColor = Color(0x55FFFFFF),
                            focusedTextColor = foreground,
                            unfocusedTextColor = foreground,
                            cursorColor = BlueAccent
                        )
                    )

                    Text(languageTitle, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    ChoiceRow(
                        options = listOf("ru" to "Русский", "en" to "English"),
                        selected = language.value,
                        onSelect = {
                            language.value = it
                            onLanguagePreview(it)
                        },
                        icon = { Icon(Icons.Default.Language, contentDescription = null, tint = BlueAccent) }
                    )

                    Text(themeTitle, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    ChoiceRow(
                        options = listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная"),
                        selected = themeMode.value,
                        onSelect = {
                            themeMode.value = it
                            onThemePreview(it)
                        },
                        icon = {
                            Icon(
                                imageVector = when (themeMode.value) {
                                    "dark" -> Icons.Default.DarkMode
                                    "light" -> Icons.Default.LightMode
                                    else -> Icons.Default.SettingsBrightness
                                },
                                contentDescription = null,
                                tint = BlueAccent
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { onComplete(name.value.trim(), language.value, themeMode.value) },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
            ) {
                Text(startLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        options.forEach { (value, label) ->
            val isSelected = value == selected
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.weight(1f),
                leadingIcon = if (isSelected) {
                    { androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}
