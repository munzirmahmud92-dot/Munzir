package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.ConnectionMode
import com.example.ui.components.AudioPreviewPlayerCard
import com.example.ui.components.ConnectivityStatusPill
import com.example.ui.components.LiveWaveformView
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.StudioTopBar
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.viewmodel.OnlineGenUiState
import com.example.ui.viewmodel.OnlineStudioViewModel
import java.io.File
import java.util.Locale

@Composable
fun OnlineStudioScreen(
    viewModel: OnlineStudioViewModel,
    onBack: () -> Unit,
    onNavigateToOfflineStudio: () -> Unit
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoice.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val genState by viewModel.genState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val personalVoices by viewModel.personalVoices.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val personalNotice by viewModel.personalVoiceNotice.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val isForcedOffline = settings.connectionMode == ConnectionMode.OFFLINE_ONLY
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
                    title = strings.onlineStudioTitle,
                    subtitle = "Gemini 3.1 Flash TTS",
                    onBack = onBack,
                    actions = {
                        ConnectivityStatusPill(
                            isOnline = isOnline,
                            isForcedOffline = isForcedOffline,
                            modifier = Modifier.padding(end = 12.dp)
                        )
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
                    // Offline / Airplane mode alert card
                    if (!isOnline || isForcedOffline) {
                        item {
                            StudioGlassCard(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OfflineBolt,
                                        contentDescription = null,
                                        tint = palette.amberWarning
                                    )
                                    Text(
                                        text = strings.offlineWarningTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = onNavigateToOfflineStudio,
                                    modifier = Modifier
                                        .testTag("switch_to_offline_studio_btn")
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.switchOfflineBtn)
                                }
                            }
                        }
                    }

                    // Script Input Card
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.textInputLabel,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${inputText.length} chars",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = viewModel::updateText,
                                placeholder = { Text(strings.textInputPlaceholder) },
                                minLines = 5,
                                maxLines = 10,
                                trailingIcon = {
                                    if (inputText.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateText("") }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = strings.clearText)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("online_text_input")
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.insertSampleText(isBangla = true) },
                                    modifier = Modifier
                                        .testTag("sample_bangla_btn")
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.sampleBanglaBtn)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.insertSampleText(isBangla = false) },
                                    modifier = Modifier
                                        .testTag("sample_english_btn")
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.sampleEnglishBtn)
                                }
                            }
                        }
                    }

                    // Language & Voice Selection Card
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = strings.languageLabel,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                viewModel.languages.forEach { lang ->
                                    FilterChip(
                                        selected = selectedLang.code == lang.code,
                                        onClick = { viewModel.selectLanguage(lang) },
                                        label = { Text(if (isBanglaUi) lang.labelBn else lang.labelEn) },
                                        modifier = Modifier.testTag("lang_chip_${lang.code}")
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = strings.voiceSelectionLabel,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                viewModel.presetVoices.forEach { voice ->
                                    val desc = if (isBanglaUi) voice.toneBn else voice.toneEn
                                    FilterChip(
                                        selected = selectedVoice.voiceName == voice.voiceName,
                                        onClick = { viewModel.selectPresetVoice(voice) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.RecordVoiceOver,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("${voice.voiceName} · $desc") },
                                        modifier = Modifier.testTag("voice_chip_${voice.voiceName}")
                                    )
                                }
                            }

                            if (personalVoices.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = strings.tabSavedVoices,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    personalVoices.forEach { profile ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { viewModel.notifyPersonalVoiceNotCloned(profile) },
                                            label = { Text("${profile.name} (Local Sample)") }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = personalNotice != null) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = palette.amberWarning.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, palette.amberWarning.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = palette.amberWarning,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = personalNotice.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Speed & Pitch Controls
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.speakingSpeedLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = String.format(Locale.US, "%.2fx", speed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = speed,
                                onValueChange = viewModel::updateSpeed,
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.testTag("online_speed_slider")
                            )

                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.pitchControlLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = String.format(Locale.US, "%.2fx", pitch),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Slider(
                                value = pitch,
                                onValueChange = viewModel::updatePitch,
                                valueRange = 0.7f..1.4f,
                                modifier = Modifier.testTag("online_pitch_slider")
                            )
                            Text(
                                text = strings.pitchGeminiNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Honest Quota Counter + Generate Button
                    item {
                        StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${strings.apiUsageLabel} ${settings.geminiRequestsToday}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.cyanGlow
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = strings.quotaHonestyNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.generateSpeech(strings.errorEmptyText) },
                                enabled = genState !is OnlineGenUiState.Loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("generate_online_audio_btn")
                            ) {
                                if (genState is OnlineGenUiState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(strings.generatingAudio)
                                } else {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.generateAudioBtn)
                                }
                            }
                        }
                    }

                    // Loading / Error / Preview Player States
                    when (val state = genState) {
                        is OnlineGenUiState.Loading -> {
                            item {
                                StudioGlassCard(highlighted = true, modifier = Modifier.fillMaxWidth()) {
                                    Text(strings.generatingAudio, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(10.dp))
                                    LiveWaveformView(
                                        bars = List(36) { idx -> if (idx % 2 == 0) 0.7f else 0.35f },
                                        progressFraction = 1f,
                                        isAnimating = true
                                    )
                                }
                            }
                        }
                        is OnlineGenUiState.Error -> {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = palette.roseError.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, palette.roseError.copy(alpha = 0.55f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.ErrorOutline,
                                                contentDescription = null,
                                                tint = palette.roseError
                                            )
                                            Text(
                                                text = state.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (!isOnline || state.isQuotaOrAuth) {
                                            Spacer(Modifier.height(8.dp))
                                            TextButton(onClick = onNavigateToOfflineStudio) {
                                                Text(strings.switchOfflineBtn)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        is OnlineGenUiState.Success -> {
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
                                    onDownloadWav = { viewModel.exportCurrentAsWav(strings.exportSuccessPrefix) },
                                    onConvertMp3 = { viewModel.exportCurrentAsMp3(strings.exportSuccessPrefix) },
                                    onShare = {
                                        val file = (clip.mp3FilePath?.let { File(it) }?.takeIf { it.exists() })
                                            ?: (clip.wavFilePath?.let { File(it) }?.takeIf { it.exists() })
                                        if (file != null) {
                                            val mime = if (file.extension.equals("mp3", true)) "audio/mpeg" else "audio/wav"
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = mime
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, clip.title))
                                        }
                                    },
                                    isExporting = isExporting,
                                    modifier = Modifier.testTag("online_preview_player")
                                )
                            }
                        }
                        OnlineGenUiState.Idle -> Unit
                    }
                }
            }
        }
    }
}
