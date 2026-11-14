package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AudioExportFormat
import com.example.data.preferences.ConnectionMode
import com.example.data.preferences.Mp3Quality
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.StudioTopBar
import com.example.ui.components.formatBytesReadable
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var endpointDraft by remember(settings.replicationEndpointUrl) {
        mutableStateOf(settings.replicationEndpointUrl)
    }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    StudioBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                StudioTopBar(
                    title = strings.settingsTitle,
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Theme & App Language
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.themeModeLabel, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = settings.themeMode == ThemeMode.DARK,
                                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                    label = { Text(strings.themeDark) },
                                    modifier = Modifier.testTag("theme_dark_chip")
                                )
                                FilterChip(
                                    selected = settings.themeMode == ThemeMode.LIGHT,
                                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                    label = { Text(strings.themeLight) },
                                    modifier = Modifier.testTag("theme_light_chip")
                                )
                                FilterChip(
                                    selected = settings.themeMode == ThemeMode.SYSTEM,
                                    onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                    label = { Text(strings.themeSystem) }
                                )
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(strings.appLanguageLabel, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppLanguage.entries.forEach { lang ->
                                    FilterChip(
                                        selected = settings.appLanguage == lang,
                                        onClick = { viewModel.setAppLanguage(lang) },
                                        label = { Text(lang.displayName) },
                                        modifier = Modifier.testTag("settings_lang_${lang.code}")
                                    )
                                }
                            }
                        }
                    }

                    // Audio Quality, Default Format & Connection Mode
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.mp3QualityLabel, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Mp3Quality.entries.forEach { q ->
                                    val label = if (settings.appLanguage == AppLanguage.BANGLA) q.labelBn else q.labelEn
                                    FilterChip(
                                        selected = settings.mp3Quality == q,
                                        onClick = { viewModel.setMp3Quality(q) },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(strings.defaultFormatLabel, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AudioExportFormat.entries.forEach { fmt ->
                                    FilterChip(
                                        selected = settings.defaultFormat == fmt,
                                        onClick = { viewModel.setDefaultFormat(fmt) },
                                        label = { Text(fmt.name) }
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(strings.connectionModeLabel, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = settings.connectionMode == ConnectionMode.AUTO,
                                    onClick = { viewModel.setConnectionMode(ConnectionMode.AUTO) },
                                    label = { Text(strings.modeAuto) }
                                )
                                FilterChip(
                                    selected = settings.connectionMode == ConnectionMode.ONLINE_ONLY,
                                    onClick = { viewModel.setConnectionMode(ConnectionMode.ONLINE_ONLY) },
                                    label = { Text(strings.modeOnlineOnly) }
                                )
                                FilterChip(
                                    selected = settings.connectionMode == ConnectionMode.OFFLINE_ONLY,
                                    onClick = { viewModel.setConnectionMode(ConnectionMode.OFFLINE_ONLY) },
                                    label = { Text(strings.modeOfflineOnly) }
                                )
                            }
                        }
                    }

                    // Optional Official Voice Replication API Endpoint
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.replicationEndpointLabel, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = endpointDraft,
                                onValueChange = { endpointDraft = it },
                                placeholder = { Text(strings.replicationEndpointHint) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.setReplicationEndpointUrl(endpointDraft) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(strings.confirmBtn)
                            }
                        }
                    }

                    // Storage Management
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.storageManagementTitle, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${strings.storageUsedLabel}: ${formatBytesReadable(storageStats.totalBytes)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.cyanGlow
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "History: ${formatBytesReadable(storageStats.historyBytes)} · Exports: ${formatBytesReadable(storageStats.exportsBytes)} · Voice Samples: ${formatBytesReadable(storageStats.voiceSamplesBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = viewModel::clearUnexportedCache,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.clearCacheBtn, maxLines = 1)
                                }
                                Button(
                                    onClick = { showResetConfirm = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.roseError),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.resetAllBtn, maxLines = 1)
                                }
                            }
                        }
                    }

                    // Privacy & Consent Settings
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.privacyConsentTitle, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Require explicit consent before saving voice samples",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = settings.requireExplicitConsent,
                                    onCheckedChange = viewModel::setRequireExplicitConsent
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Allow Gemini acoustic timbre analysis on demand",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = settings.allowAcousticTimbreAnalysis,
                                    onCheckedChange = viewModel::setAllowTimbreAnalysis
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = strings.privacyPolicyBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(strings.deleteConfirmTitle) },
            text = { Text(strings.deleteConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllLocalData()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.roseError)
                ) {
                    Text(strings.confirmBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }
}
