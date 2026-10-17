package com.stille.messenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import java.util.Locale

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = Color(0xFF34C759),
    background = Color(0xFFEFEFF0),
    surface = Color.White,
    onSurface = Color(0xFF121212),
    onBackground = Color(0xFF121212),
    surfaceVariant = Color(0xFFE0E0E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = Color(0xFF34C759),
    background = Color.Black,
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    onBackground = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1E1E1E)
)

@Composable
fun StilleTheme(
    themeMode: String = "system",
    language: String = "ru",
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    LaunchedEffect(language) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }

    StilleColors.isDark = darkTheme
    StilleColors.darkTheme = themeMode == "dark"
    StilleColors.language = language

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = { Surface(color = if (darkTheme) DarkColors.background else LightColors.background, content = content) }
    )
}
