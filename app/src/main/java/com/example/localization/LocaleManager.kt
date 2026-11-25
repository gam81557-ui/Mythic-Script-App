package com.example.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleManager {
    const val ENGLISH = "en"
    const val BURMESE = "my"

    private const val PREFS_NAME = "mythic_script_locale"
    private const val KEY_LANGUAGE = "language"

    fun currentLanguage(context: Context): String {
        val stored = preferences(context).getString(KEY_LANGUAGE, ENGLISH)
        return if (stored == BURMESE) BURMESE else ENGLISH
    }

    fun isBurmese(context: Context): Boolean = currentLanguage(context) == BURMESE

    fun setLanguage(context: Context, language: String) {
        val normalized = if (language == BURMESE) BURMESE else ENGLISH
        preferences(context).edit().putString(KEY_LANGUAGE, normalized).apply()
        Locale.setDefault(localeFor(normalized))
    }

    fun toggleLanguage(context: Context): String {
        val next = if (isBurmese(context)) ENGLISH else BURMESE
        setLanguage(context, next)
        return next
    }

    fun wrap(context: Context): Context = localizedContext(context)

    fun localizedContext(context: Context): Context {
        val locale = localeFor(currentLanguage(context))
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun localeFor(language: String): Locale =
        if (language == BURMESE) Locale("my", "MM") else Locale.US
}

fun Context.localizedString(resId: Int, vararg args: Any): String =
    LocaleManager.localizedContext(this).getString(resId, *args)
