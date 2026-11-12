package com.example.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AppLanguage
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.PdfFitMode
import com.example.feature.filemanager.formatFileSize

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFitModeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Info Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Habib Advance Editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Phase 1 Foundation • v1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Appearance Section
        SettingsSectionHeader(title = "Appearance")
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsClickableItem(
                    icon = when (prefs.themeMode) {
                        AppThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                        AppThemeMode.LIGHT -> Icons.Default.LightMode
                        AppThemeMode.DARK -> Icons.Default.DarkMode
                    },
                    title = "App Theme",
                    subtitle = when (prefs.themeMode) {
                        AppThemeMode.SYSTEM -> "System Default"
                        AppThemeMode.LIGHT -> "Light Theme"
                        AppThemeMode.DARK -> "Dark Theme"
                    },
                    onClick = { showThemeDialog = true },
                    testTag = "setting_theme_item"
                )
            }
        }

        // PDF Reader Preferences
        SettingsSectionHeader(title = "PDF Reader Preferences")
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsClickableItem(
                    icon = Icons.Default.PictureAsPdf,
                    title = "Default Page Fit Mode",
                    subtitle = when (prefs.defaultFitMode) {
                        PdfFitMode.FIT_WIDTH -> "Fit to Width (Recommended)"
                        PdfFitMode.FIT_PAGE -> "Fit to Entire Page"
                        PdfFitMode.ACTUAL_SIZE -> "Actual Size"
                    },
                    onClick = { showFitModeDialog = true },
                    testTag = "setting_fit_mode_item"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleItem(
                    title = "Remember Last Read Page",
                    subtitle = "Resume document from the last visited page",
                    checked = prefs.rememberLastPage,
                    onCheckedChange = viewModel::setRememberLastPage
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsToggleItem(
                    title = "High Quality Page Tile Caching",
                    subtitle = "Render crisp vector text using Android Native PdfRenderer",
                    checked = prefs.highQualityRendering,
                    onCheckedChange = viewModel::setHighQualityRendering
                )
            }
        }

        // Localization
        SettingsSectionHeader(title = "Language & Region")
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            SettingsClickableItem(
                icon = Icons.Default.Language,
                title = "Language",
                subtitle = when (prefs.language) {
                    AppLanguage.ENGLISH -> "English"
                    AppLanguage.BENGALI -> "Bengali (বাংলা) - Prepared"
                    AppLanguage.ARABIC -> "Arabic (العربية) - Prepared"
                },
                onClick = { showLanguageDialog = true },
                testTag = "setting_language_item"
            )
        }

        // Storage & Cache
        SettingsSectionHeader(title = "Storage & Cache")
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsClickableItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear Temporary Render Cache",
                    subtitle = "Free temporary cached PDF render tiles",
                    onClick = {
                        val freed = viewModel.clearTemporaryCache()
                        Toast.makeText(context, "Render cache cleared", Toast.LENGTH_SHORT).show()
                    },
                    testTag = "setting_clear_cache_item"
                )
            }
        }

        // About & Support
        SettingsSectionHeader(title = "About Habib Advance Editor")
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                SettingsClickableItem(
                    icon = Icons.Default.Info,
                    title = "About Application",
                    subtitle = "Learn about Phase 1 foundation and architecture",
                    onClick = { showAboutDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsClickableItem(
                    icon = Icons.Default.Policy,
                    title = "Privacy Policy",
                    subtitle = "Offline-first security & zero-tracking policy",
                    onClick = { showPrivacyDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsClickableItem(
                    icon = Icons.Default.Feedback,
                    title = "Send Feedback",
                    subtitle = "Submit feedback for Habib Advance Editor",
                    onClick = {
                        Toast.makeText(context, "Thank you for using Habib Advance Editor!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Dialogs
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    ThemeOptionRow(
                        title = "System Default",
                        selected = prefs.themeMode == AppThemeMode.SYSTEM,
                        onClick = {
                            viewModel.setThemeMode(AppThemeMode.SYSTEM)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionRow(
                        title = "Light Theme",
                        selected = prefs.themeMode == AppThemeMode.LIGHT,
                        onClick = {
                            viewModel.setThemeMode(AppThemeMode.LIGHT)
                            showThemeDialog = false
                        }
                    )
                    ThemeOptionRow(
                        title = "Dark Theme",
                        selected = prefs.themeMode == AppThemeMode.DARK,
                        onClick = {
                            viewModel.setThemeMode(AppThemeMode.DARK)
                            showThemeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Done") }
            }
        )
    }

    if (showFitModeDialog) {
        AlertDialog(
            onDismissRequest = { showFitModeDialog = false },
            title = { Text("Default Page Fit Mode") },
            text = {
                Column {
                    ThemeOptionRow(
                        title = "Fit to Width",
                        selected = prefs.defaultFitMode == PdfFitMode.FIT_WIDTH,
                        onClick = {
                            viewModel.setFitMode(PdfFitMode.FIT_WIDTH)
                            showFitModeDialog = false
                        }
                    )
                    ThemeOptionRow(
                        title = "Fit to Page",
                        selected = prefs.defaultFitMode == PdfFitMode.FIT_PAGE,
                        onClick = {
                            viewModel.setFitMode(PdfFitMode.FIT_PAGE)
                            showFitModeDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFitModeDialog = false }) { Text("Done") }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = {
                Column {
                    ThemeOptionRow(
                        title = "English (Active)",
                        selected = prefs.language == AppLanguage.ENGLISH,
                        onClick = {
                            viewModel.setLanguage(AppLanguage.ENGLISH)
                            showLanguageDialog = false
                        }
                    )
                    ThemeOptionRow(
                        title = "Bengali (বাংলা) - Prepared",
                        selected = prefs.language == AppLanguage.BENGALI,
                        onClick = {
                            viewModel.setLanguage(AppLanguage.BENGALI)
                            showLanguageDialog = false
                        }
                    )
                    ThemeOptionRow(
                        title = "Arabic (العربية) - Prepared",
                        selected = prefs.language == AppLanguage.ARABIC,
                        onClick = {
                            viewModel.setLanguage(AppLanguage.ARABIC)
                            showLanguageDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Done") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Habib Advance Editor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Version: 1.0.0 (Phase 1 Foundation)",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Built with Kotlin, Jetpack Compose, Android Native PdfRenderer, Room Database, and Storage Access Framework.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Designed for professional offline-first PDF viewing, document organization, and modular expansion into Phase 2 advanced object editing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = { Icon(Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Privacy Policy") },
            text = {
                Text(
                    text = "Habib Advance Editor is strictly offline-first. Your documents, PDFs, and edits remain exclusively on your device and are never uploaded to any remote server or third-party service.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Got it") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    val modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 14.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemeOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
