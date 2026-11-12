package com.example.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.PreferencesRepository
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val appVersion: String = "1.0.0 (Phase 1)",
    val cacheSizeBytes: Long = 0L,
    val internalDocsCount: Int = 0
)

class SettingsViewModel(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setFitMode(mode: PdfFitMode) {
        viewModelScope.launch {
            preferencesRepository.setFitMode(mode)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(language)
        }
    }

    fun setRememberLastPage(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRememberLastPage(enabled)
        }
    }

    fun setHighQualityRendering(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHighQualityRendering(enabled)
        }
    }

    fun clearTemporaryCache(): Long {
        var freed = 0L
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                freed += file.length()
                file.deleteRecursively()
            }
        } catch (_: Exception) {}
        return freed
    }
}

class SettingsViewModelFactory(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(context, preferencesRepository) as T
    }
}
