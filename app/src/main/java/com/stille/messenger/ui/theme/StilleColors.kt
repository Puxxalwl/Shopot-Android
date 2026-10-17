package com.stille.messenger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal data class StilleAppearance(val isDark: Boolean, val themeMode: String, val language: String)
internal val LocalStilleAppearance = staticCompositionLocalOf { StilleAppearance(false, "system", "ru") }

/** Composition-scoped values: language/theme changes invalidate every reader. */
object StilleColors {
    val isDark: Boolean
        @Composable get() = LocalStilleAppearance.current.isDark
    val language: String
        @Composable get() = LocalStilleAppearance.current.language
}
