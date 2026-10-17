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
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFF3F4F7),
    surface = Color(0xFF15161A),
    onSurface = Color(0xFFF3F4F7),
    surfaceVariant = Color(0xFF202228),
    onSurfaceVariant = Color(0xFFA9AFBA),
    outline = Color(0xFF3D414A),
    outlineVariant = Color(0xFF2A2D34),
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
        colorScheme = if (darkTheme) DarkColors else LightColors,
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
