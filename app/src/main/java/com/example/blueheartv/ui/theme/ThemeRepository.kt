package com.example.blueheartv.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

object ThemeRepository {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "theme_preference"

    private val _preference = MutableStateFlow(ThemePreference.SYSTEM)
    val preference: StateFlow<ThemePreference> = _preference.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_THEME, null)
        _preference.value = when (saved) {
            "LIGHT" -> ThemePreference.LIGHT
            "DARK" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }

    fun setPreference(pref: ThemePreference) {
        _preference.value = pref
        prefs?.edit()?.putString(KEY_THEME, pref.name)?.apply()
    }
}
