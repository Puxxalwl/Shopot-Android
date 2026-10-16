package com.shopot.messenger.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Static holders so @Composable functions can access theme state
 * without needing to thread parameters through every call.
 */
object ShopotColors {
    var isDark: Boolean = false
    var darkTheme: Boolean = false
    var language: String = "ru"
    val blueAccent = Color(0xFF007AFF)
}
