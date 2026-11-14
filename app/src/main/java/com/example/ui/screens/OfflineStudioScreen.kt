package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AudioPreviewPlayerCard
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.StudioTopBar
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.viewmodel.OfflineGenUiState
import com.example.ui.viewmodel.OfflineStudioViewModel
import java.util.Locale

@Composable
fun OfflineStudioScreen(
    viewModel: OfflineStudioViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ttsStatus by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedLocale by viewModel.selectedLocale.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoiceName.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val genState by viewModel.genState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val isBanglaUi = settings.appLanguage == AppLanguage.BANGLA

    LaunchedEffect(exportMessage) {
        val msg = exportMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissExportMessage()
        }
    }

    StudioBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                StudioTopBar(
                    title = strings.offlineStudioTitle,
                    subtitle = ttsStatus.enginePackage ?: "Android TextToSpeech",
                    onBack = onBack,
                    actions = {
                        IconButton(
                            onClick = viewModel::refreshVoices,
                            modifier = Modifier
                                .testTag("offline_refresh_voices_btn")
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh)
                            )
                        }
                    }
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
                    // Honest Offline Voice Disclaimer
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = palette.amberWarning.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, palette.amberWarning.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = palette.amberWarning
                                )
                                Text(
                                    text = strings.offlineDisclaimer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Installed Bangla & English Language Packs + Settings Shortcuts
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = strings.installedVoicesTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(10.dp))

                            if (ttsStatus.initError != null) {
                                Text(
                                    text = ttsStatus.initError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.roseError
                                )
                            } else {
                                ttsStatus.languageAvailability.forEach { avail ->
                                    val label = if (isBanglaUi) avail.labelBn else avail.labelEn
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$label (${avail.localeTag})",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Icon(
                                            imageVector = if (avail.isAvailableOffline) {
                                                Icons.Rounded.CheckCircle
                                            } else {
                                                Icons.Rounded.WarningAmber
                                            },
                                            contentDescription = null,
                                            tint = if (avail.isAvailableOffline) {
                                                palette.emeraldStatus
                                            } else {
                                                palette.amberWarning
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(viewModel.getInstallVoiceDataIntent())
                                        }.onFailure {
                                            runCatching { context.startActivity(viewModel.getTtsSettingsIntent()) }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("install_tts_data_btn")
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.installLangDataBtn, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching { context.startActivity(viewModel.getTtsSettingsIntent()) }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_tts_settings_btn")
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(strings.openTtsSettingsBtn, maxLines = 1)
                                }
                            }
                        }
                    }

                    // Script Input Card
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.textInputLabel, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = viewModel::updateText,
                                placeholder = { Text(strings.textInputPlaceholder) },
                                minLines = 4,
                                maxLines = 8,
                                trailingIcon = {
                                    if (inputText.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateText("") }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = strings.clearText)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("offline_text_input")
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.insertSample(isBangla = true) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.sampleBanglaBtn)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.insertSample(isBangla = false) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.sampleEnglishBtn)
                                }
                            }
                        }
                    }

                    // Locale & Installed Voice Selector + Speed/Pitch
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("bn-BD", "bn-IN", "en-US", "en-GB", "en-IN").forEach { tag ->
                                    FilterChip(
                                        selected = selectedLocale == tag,
                                        onClick = { viewModel.selectLocale(tag) },
                                        label = { Text(tag) }
                                    )
                                }
                            }

                            val matchingVoices = ttsStatus.installedVoices.filter {
                                it.localeTag.startsWith(selectedLocale.substringBefore("-"), ignoreCase = true)
                            }
                            if (matchingVoices.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text(strings.voiceSelectionLabel, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    matchingVoices.take(12).forEach { voice ->
                                        val shortName = voice.name.substringAfterLast("-").take(18)
                                        FilterChip(
                                            selected = selectedVoice == voice.name,
                                            onClick = { viewModel.selectVoice(voice.name) },
                                            label = {
                                                Text(
                                                    if (voice.isNetworkRequired) "$shortName (Net)" else shortName
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.speakingSpeedLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    String.format(Locale.US, "%.2fx", speed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(value = speed, onValueChange = viewModel::updateSpeed, valueRange = 0.5f..2.0f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.pitchControlLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    String.format(Locale.US, "%.2fx", pitch),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Slider(value = pitch, onValueChange = viewModel::updatePitch, valueRange = 0.5f..2.0f)

                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.generateOfflineSpeech(strings.errorEmptyText) },
                                enabled = genState !is OfflineGenUiState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("generate_offline_audio_btn")
                            ) {
                                if (genState is OfflineGenUiState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Rounded.OfflineBolt, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(strings.generateOfflineBtn)
                            }
                        }
                    }

                    // Error / Preview Card
                    when (val state = genState) {
                        is OfflineGenUiState.Error -> {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = palette.roseError.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, palette.roseError.copy(alpha = 0.55f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = palette.roseError)
                                            Text(
                                                text = if (state.isLanguageMissing) strings.languageMissingMsg else state.message,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        if (state.isLanguageMissing) {
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    runCatching { context.startActivity(viewModel.getInstallVoiceDataIntent()) }
                                                }
                                            ) {
                                                Text(strings.installLangDataBtn)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is OfflineGenUiState.Success -> {
                            val clip = state.clip
                            item {
                                AudioPreviewPlayerCard(
                                    title = clip.title,
                                    subtitle = "${clip.voiceLabel} · ${clip.languageCode} · ${clip.durationMs / 1000.0}s",
                                    playbackState = playbackState,
                                    isCurrentClip = playbackState.activeClipId == clip.id,
                                    isVerifiedPlayable = clip.isVerifiedPlayable,
                                    onPlay = { viewModel.playOrResumeClip(clip) },
                                    onPause = viewModel::pausePlayback,
                                    onStop = viewModel::stopPlayback,
                                    onSeek = viewModel::seekPlayback,
                                    onDownloadWav = { viewModel.exportWav(strings.exportSuccessPrefix) },
                                    onConvertMp3 = { viewModel.exportMp3(strings.exportSuccessPrefix) },
                                    isExporting = isExporting,
                                    modifier = Modifier.testTag("offline_preview_player")
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
