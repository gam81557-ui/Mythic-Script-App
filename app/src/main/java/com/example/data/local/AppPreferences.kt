package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.companionDataStore by preferencesDataStore(name = "mythic_script_prefs")

class AppPreferences(private val context: Context) {

    val targetPackageFlow: Flow<String> = context.companionDataStore.data.map { prefs ->
        prefs[KEY_TARGET_PACKAGE]?.takeIf { isValidPackageName(it) } ?: DEFAULT_TARGET_PACKAGE
    }

    val disclaimerDismissedFlow: Flow<Boolean> = context.companionDataStore.data.map { prefs ->
        prefs[KEY_DISCLAIMER_DISMISSED] ?: false
    }

    val selectedHeroIdFlow: Flow<String?> = context.companionDataStore.data.map { prefs ->
        prefs[KEY_SELECTED_HERO_ID]
    }

    suspend fun setTargetPackage(packageName: String): Result<String> {
        val trimmed = packageName.trim()
        if (!isValidPackageName(trimmed)) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid Android package name. Example: com.mobile.legends"
                )
            )
        }
        context.companionDataStore.edit { prefs ->
            prefs[KEY_TARGET_PACKAGE] = trimmed
        }
        return Result.success(trimmed)
    }

    suspend fun setDisclaimerDismissed(dismissed: Boolean) {
        context.companionDataStore.edit { prefs ->
            prefs[KEY_DISCLAIMER_DISMISSED] = dismissed
        }
    }

    suspend fun setSelectedHeroId(heroId: String) {
        context.companionDataStore.edit { prefs ->
            prefs[KEY_SELECTED_HERO_ID] = heroId
        }
    }

    companion object {
        const val DEFAULT_TARGET_PACKAGE = "com.mobile.legends"
        private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

        private val KEY_TARGET_PACKAGE = stringPreferencesKey("target_package")
        private val KEY_DISCLAIMER_DISMISSED = booleanPreferencesKey("disclaimer_dismissed")
        private val KEY_SELECTED_HERO_ID = stringPreferencesKey("selected_hero_id")

        fun isValidPackageName(pkg: String): Boolean {
            return pkg.length in 5..120 && PACKAGE_REGEX.matches(pkg)
        }
    }
}
