package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.companionDataStore by preferencesDataStore(name = "mythic_script_prefs")

class AppPreferences(private val context: Context) {

    private val safeData = context.companionDataStore.data.catch { error ->
        Log.e("AppPreferences", "Unable to read preferences", error)
        emit(emptyPreferences())
    }

    val targetPackageFlow: Flow<String> = safeData.map { prefs ->
        prefs[KEY_TARGET_PACKAGE]?.takeIf { isValidPackageName(it) } ?: DEFAULT_TARGET_PACKAGE
    }

    val disclaimerDismissedFlow: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_DISCLAIMER_DISMISSED] ?: false
    }

    val selectedHeroIdFlow: Flow<String?> = safeData.map { prefs ->
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
        return runCatching {
            context.companionDataStore.edit { prefs ->
                prefs[KEY_TARGET_PACKAGE] = trimmed
            }
            trimmed
        }
    }

    suspend fun setDisclaimerDismissed(dismissed: Boolean) {
        runCatching {
            context.companionDataStore.edit { prefs ->
                prefs[KEY_DISCLAIMER_DISMISSED] = dismissed
            }
        }.onFailure { Log.e("AppPreferences", "Unable to save disclaimer state", it) }
    }

    suspend fun setSelectedHeroId(heroId: String) {
        runCatching {
            context.companionDataStore.edit { prefs ->
                prefs[KEY_SELECTED_HERO_ID] = heroId
            }
        }.onFailure { Log.e("AppPreferences", "Unable to save selected hero", it) }
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
