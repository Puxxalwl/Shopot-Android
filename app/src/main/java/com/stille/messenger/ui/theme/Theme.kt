package com.stille.messenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// A restrained, semantic palette: system blue is used only for actions and state.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0067DB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF5E6675),
    onSecondary = Color.White,
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF17181C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17181C),
    surfaceVariant = Color(0xFFEDEEF2),
    onSurfaceVariant = Color(0xFF626874),
    outline = Color(0xFFD7D9DF),
    outlineVariant = Color(0xFFE7E8EC),
    error = Color(0xFFD92D20)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64A9FF),
    onPrimary = Color(0xFF002E62),
    primaryContainer = Color(0xFF004684),
    onPrimaryContainer = Color(0xFFDCEBFF),
    secondary = Color(0xFFB8C1D0),
    onSecondary = Color(0xFF27303D),
    // Dark — Midnight: deep navy rather than neutral charcoal.
    background = Color(0xFF080F20),
    onBackground = Color(0xFFF0F4FF),
    surface = Color(0xFF14213B),
    onSurface = Color(0xFFF0F4FF),
    surfaceVariant = Color(0xFF1D2D4A),
    onSurfaceVariant = Color(0xFFB1BED5),
    outline = Color(0xFF485B7B),
    outlineVariant = Color(0xFF2C3D5B),
    surfaceDim = Color(0xFF080F20),
    surfaceBright = Color(0xFF2B3D5B),
    surfaceContainerLowest = Color(0xFF060C19),
    surfaceContainerLow = Color(0xFF101B30),
    surfaceContainer = Color(0xFF14213B),
    surfaceContainerHigh = Color(0xFF1D2D4A),
    surfaceContainerHighest = Color(0xFF263957),
    secondaryContainer = Color(0xFF263957),
    onSecondaryContainer = Color(0xFFDCE7FC),
    surfaceTint = Color(0xFF64A9FF),
    error = Color(0xFFFF8A80)
)

private val StilleTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun StilleTheme(
    themeMode: String = "system",
    language: String = "ru",
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    // Install semantic colors once per selection, not once per animation frame.
    // The wallpaper animates in its draw phase without recomposing every screen.
    val colors = if (darkTheme) DarkColors else LightColors

    // Keep the existing locale behavior while the UI remains fully Compose-driven.
    androidx.compose.runtime.LaunchedEffect(language) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.createConfigurationContext(config)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalStilleAppearance provides StilleAppearance(darkTheme, themeMode, language)
    ) {
    MaterialTheme(
        colorScheme = colors,
        typography = StilleTypography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        ),
        content = { Surface(color = MaterialTheme.colorScheme.background, content = content) }
    )
    }
}
