package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "habib_editor_settings")

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class PdfFitMode {
    FIT_WIDTH,
    FIT_PAGE,
    ACTUAL_SIZE
}

enum class AppLanguage {
    ENGLISH,
    BENGALI,
    ARABIC
}

data class UserPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultFitMode: PdfFitMode = PdfFitMode.FIT_WIDTH,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val rememberLastPage: Boolean = true,
    val highQualityRendering: Boolean = true
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FIT_MODE = stringPreferencesKey("fit_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
        val REMEMBER_LAST_PAGE = booleanPreferencesKey("remember_last_page")
        val HIGH_QUALITY_RENDERING = booleanPreferencesKey("high_quality_rendering")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name
        val fitStr = prefs[Keys.FIT_MODE] ?: PdfFitMode.FIT_WIDTH.name
        val langStr = prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.name

        UserPreferences(
            themeMode = try { AppThemeMode.valueOf(themeStr) } catch (_: Exception) { AppThemeMode.SYSTEM },
            defaultFitMode = try { PdfFitMode.valueOf(fitStr) } catch (_: Exception) { PdfFitMode.FIT_WIDTH },
            language = try { AppLanguage.valueOf(langStr) } catch (_: Exception) { AppLanguage.ENGLISH },
            rememberLastPage = prefs[Keys.REMEMBER_LAST_PAGE] ?: true,
            highQualityRendering = prefs[Keys.HIGH_QUALITY_RENDERING] ?: true
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setFitMode(mode: PdfFitMode) {
        context.dataStore.edit { it[Keys.FIT_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setRememberLastPage(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_LAST_PAGE] = enabled }
    }

    suspend fun setHighQualityRendering(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIGH_QUALITY_RENDERING] = enabled }
    }
}
