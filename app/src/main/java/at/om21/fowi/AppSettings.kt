package at.om21.fowi

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    GERMAN("de")
}

object AppSettings {
    private const val PREFERENCES_NAME = "app_settings"
    private const val FORWARDING_ENABLED_KEY = "forwarding_enabled"
    private const val THEME_MODE_KEY = "theme_mode"
    private const val INCLUDE_SENDER_KEY = "include_sender"

    fun isForwardingEnabled(context: Context): Boolean =
        preferences(context).getBoolean(FORWARDING_ENABLED_KEY, true)

    fun setForwardingEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(FORWARDING_ENABLED_KEY, enabled).apply()
    }

    /** Whether forwarded messages start with a line naming the original sender. */
    fun isSenderIncluded(context: Context): Boolean =
        preferences(context).getBoolean(INCLUDE_SENDER_KEY, true)

    fun setSenderIncluded(context: Context, included: Boolean) {
        preferences(context).edit().putBoolean(INCLUDE_SENDER_KEY, included).apply()
    }

    fun themeMode(context: Context): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == preferences(context).getString(THEME_MODE_KEY, null) }
            ?: ThemeMode.SYSTEM

    /** Stores the mode and applies it; AppCompat recreates open activities to match. */
    fun setThemeMode(context: Context, mode: ThemeMode) {
        preferences(context).edit().putString(THEME_MODE_KEY, mode.name).apply()
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    // AppCompat stores the per-app language itself (in the system on Android 13+).
    fun language(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.SYSTEM
        val language = locales[0]?.language
        return AppLanguage.entries.firstOrNull { it != AppLanguage.SYSTEM && it.tag == language }
            ?: AppLanguage.SYSTEM
    }

    fun setLanguage(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(
            if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(language.tag)
        )
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
