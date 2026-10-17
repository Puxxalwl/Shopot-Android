package com.stille.messenger.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stille.messenger.R
import com.stille.messenger.ui.ProfileName
import com.stille.messenger.ui.theme.StilleColors

private val WelcomeBlue = Color(0xFF1683FF)
private val WelcomePurple = Color(0xFF8B6DFF)

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
    var themeMode by rememberSaveable { mutableStateOf(initialThemeMode) }
    val isEnglish = language == "en"
    val isDark = StilleColors.isDark
    val foreground = if (isDark) Color.White else Color(0xFF182235)
    val muted = if (isDark) Color(0xB8FFFFFF) else Color(0xFF66738B)
    val card = if (isDark) Color(0x301B2435) else Color(0xB8FFFFFF)
    val border = if (isDark) Color(0x38FFFFFF) else Color(0xB8FFFFFF)

    val nameBytes = ProfileName.byteLength(name.trim())
    val validName = ProfileName.isValid(name)
    val title = if (isEnglish) "Welcome to Stille" else "Добро пожаловать в Stille"
    val subtitle = if (isEnglish) "A quieter place for your conversations" else "Тихое место для ваших разговоров"
    val nameTitle = if (isEnglish) "Your name" else "Ваше имя"
    val nameHint = if (isEnglish) "How should we call you?" else "Как к вам обращаться?"
    val languageTitle = if (isEnglish) "Language" else "Язык интерфейса"
    val themeTitle = if (isEnglish) "Appearance" else "Оформление"
    val continueLabel = if (isEnglish) "Continue" else "Продолжить"

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackground()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            WelcomeBlue.copy(alpha = if (isDark) 0.25f else 0.12f),
                            Color.Transparent,
                            WelcomePurple.copy(alpha = if (isDark) 0.22f else 0.10f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    painter = painterResource(R.drawable.stille_logo),
                    contentDescription = "Stille Messenger",
                    modifier = Modifier.size(86.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = title,
                    color = foreground,
                    fontSize = 29.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(text = subtitle, color = muted, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(24.dp))

                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = card,
                    borderColor = border
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        SectionLabel(icon = Icons.Default.Person, text = nameTitle, color = foreground)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { value -> name = ProfileName.limitInput(value) },
                            isError = nameBytes > ProfileName.MAX_BYTES,
                            supportingText = {
                                Text(if (isEnglish) "$nameBytes / 32 UTF-8 bytes" else "$nameBytes / 32 байта UTF-8")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(17.dp),
                            placeholder = { Text(nameHint, color = muted) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = WelcomeBlue) },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = if (isDark) 0.08f else 0.52f),
                                unfocusedContainerColor = Color.White.copy(alpha = if (isDark) 0.05f else 0.36f),
                                focusedBorderColor = WelcomeBlue,
                                unfocusedBorderColor = border,
                                focusedTextColor = foreground,
                                unfocusedTextColor = foreground,
                                cursorColor = WelcomeBlue,
                                focusedLeadingIconColor = WelcomeBlue,
                                unfocusedLeadingIconColor = WelcomeBlue
                            )
                        )

                        SectionLabel(icon = Icons.Default.Language, text = languageTitle, color = foreground)
                        SegmentedChoice(
                            options = if (isEnglish) listOf("ru" to "Russian", "en" to "English")
                            else listOf("ru" to "Русский", "en" to "English"),
                            selected = language,
                            onSelect = {
                                language = it
                                onLanguagePreview(it)
                            },
                            foreground = foreground,
                            muted = muted,
                            selectedColor = WelcomeBlue
                        )

                        SectionLabel(
                            icon = when (themeMode) {
                                "dark" -> Icons.Default.DarkMode
                                "light" -> Icons.Default.LightMode
                                else -> Icons.Default.SettingsBrightness
                            },
                            text = themeTitle,
                            color = foreground
                        )
                        SegmentedChoice(
                            options = if (isEnglish) {
                                listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                            } else {
                                listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная")
                            },
                            selected = themeMode,
                            onSelect = {
                                themeMode = it
                                onThemePreview(it)
                            },
                            foreground = foreground,
                            muted = muted,
                            selectedColor = WelcomePurple
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            GlassActionButton(
                label = continueLabel,
                enabled = validName,
                onClick = { onComplete(name.trim(), language, themeMode) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = WelcomeBlue, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Text(text = text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SegmentedChoice(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    foreground: Color,
    muted: Color,
    selectedColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = if (StilleColors.isDark) 0.07f else 0.34f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (isSelected) selectedColor else Color.Transparent)
                    .clickable(role = Role.Tab, onClick = { onSelect(value) })
                    .semantics { role = Role.Tab },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else if (foreground.alpha > 0.9f) foreground else muted,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(WelcomeBlue, WelcomePurple))
                else Brush.horizontalGradient(listOf(Color(0x668AA4C2), Color(0x668AA4C2)))
            )
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.38f else 0.15f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.55f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier,
    shape: Shape,
    color: Color,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = (color.alpha + 0.08f).coerceAtMost(1f)), color)
                )
            )
            .border(1.dp, borderColor, shape)
    ) {
        content()
    }
}
