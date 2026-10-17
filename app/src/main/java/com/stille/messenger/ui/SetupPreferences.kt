package com.stille.messenger.ui

import android.content.Context

/** Local, non-sensitive first-run preferences. */
class SetupPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val completed: Boolean
        get() = preferences.getBoolean(KEY_COMPLETED, false)

    val name: String
        get() = preferences.getString(KEY_NAME, "") ?: ""

    val language: String
        get() = preferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    val themeMode: String
        get() = preferences.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE

    fun save(name: String, language: String, themeMode: String) {
        require(ProfileName.isValid(name)) { "Name must contain 1..32 UTF-8 bytes" }
        preferences.edit()
            .putBoolean(KEY_COMPLETED, true)
            .putString(KEY_NAME, name.trim())
            .putString(KEY_LANGUAGE, language)
            .putString(KEY_THEME_MODE, themeMode)
            .apply()
    }

    fun updateName(name: String) {
        if (!ProfileName.isValid(name)) return
        preferences.edit().putString(KEY_NAME, name.trim()).apply()
    }

    fun updateThemeMode(themeMode: String) {
        preferences.edit().putString(KEY_THEME_MODE, themeMode).apply()
    }

    fun updateLanguage(language: String) {
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private companion object {
        const val FILE_NAME = "stille_prefs"
        const val KEY_COMPLETED = "onboarding_complete"
        const val KEY_NAME = "profile_name"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_LANGUAGE = "ru"
        const val DEFAULT_THEME_MODE = "system"
    }
}
