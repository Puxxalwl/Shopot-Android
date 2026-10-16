package com.shopot.messenger.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.shopot.messenger.ui.theme.ShopotColors

private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = Color(0xFF34C759),
    background = Color(0xFFEFEFF0),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    onBackground = Color(0xFF121212),
    surfaceVariant = Color(0xFFE0E0E0)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = Color(0xFF34C759),
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    onBackground = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1E1E1E)
)

@Composable
fun ShopotTheme(
    content: @Composable () -> Unit
) {
    // Persist theme preference in app-level prefs
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("shopot_prefs", Context.MODE_PRIVATE)

    var darkTheme by rememberSaveable {
        mutableStateOf(prefs.getBoolean("dark_theme", false))
    }
    var language by rememberSaveable {
        mutableStateOf(prefs.getString("language", "ru") ?: "ru")
    }

    // Apply locale
    LaunchedEffect(language) {
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)
        prefs.edit().putString("language", language).apply()
    }

    // Save theme pref
    LaunchedEffect(darkTheme) {
        prefs.edit().putBoolean("dark_theme", darkTheme).apply()
    }

    val colors = if (darkTheme) DarkColors else LightColors
    val isDark = darkTheme

    // Provide colors via static holder
    ShopotColors.isDark = isDark
    ShopotColors.darkTheme = darkTheme
    ShopotColors.language = language

    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = {
            Surface(color = colors.background, content = content)
        }
    )
}
