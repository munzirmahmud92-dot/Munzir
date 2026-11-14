package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.ConsentOwnerType
import com.example.data.local.VoiceProfileEntity
import com.example.data.local.VoiceProviderType
import com.example.ui.components.ClonedVoiceProfileManager
import com.example.ui.components.LiveWaveformView
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.StudioTopBar
import com.example.ui.components.formatTimeMs
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.viewmodel.VoiceLibraryViewModel

@Composable
fun VoiceLibraryScreen(
    viewModel: VoiceLibraryViewModel,
    initialOpenCreateTab: Boolean,
    onBack: () -> Unit,
    onUsePresetInOnlineStudio: (String) -> Unit
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val context = LocalContext.current

    val allVoices by viewModel.allVoices.collectAsStateWithLifecycle()
    val filteredPersonalVoices by viewModel.filteredPersonalVoices.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val languageFilter by viewModel.languageFilter.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val voiceName by viewModel.voiceName.collectAsStateWithLifecycle()
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val ownershipType by viewModel.ownershipType.collectAsStateWithLifecycle()
    val permissionNote by viewModel.permissionNote.collectAsStateWithLifecycle()
    val consentChecked by viewModel.consentChecked.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isErrorStatus by viewModel.isErrorStatus.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val timbreAnalysis by viewModel.timbreAnalysis.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(if (initialOpenCreateTab) 1 else 0) }
    var micPermissionError by remember { mutableStateOf<String?>(null) }
    var profilePendingAudioReplace by remember { mutableStateOf<VoiceProfileEntity?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionError = null
            viewModel.startRecording()
        } else {
            micPermissionError = strings.permissionMicDenied
        }
    }

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importAudioSample(uri)
        }
    }

    val replaceProfileAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = profilePendingAudioReplace
        profilePendingAudioReplace = null
        if (uri != null && target != null) {
            viewModel.replaceProfileAudio(target, uri)
        }
    }

    val personalCount = remember(allVoices) {
        allVoices.count { it.providerType != VoiceProviderType.GEMINI_PRESET.name }
    }
    val presetVoices = remember(allVoices) {
        allVoices.filter { it.providerType == VoiceProviderType.GEMINI_PRESET.name }
    }

    StudioBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                StudioTopBar(
                    title = strings.voiceLibraryTitle,
                    subtitle = "$personalCount personal · ${presetVoices.size} studio",
                    onBack = onBack
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.widthIn(max = 760.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(strings.tabSavedVoices) },
                        modifier = Modifier.testTag("tab_saved_voices")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(strings.tabCreateMyVoice) },
                        modifier = Modifier.testTag("tab_create_my_voice")
                    )
                }

                if (selectedTab == 0) {
                    ClonedVoiceProfileManager(
                        personalVoices = filteredPersonalVoices,
                        presetVoices = presetVoices,
                        searchQuery = searchQuery,
                        languageFilter = languageFilter,
                        playbackState = playbackState,
                        statusMessage = statusMessage,
                        isErrorStatus = isErrorStatus,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onLanguageFilterChange = viewModel::updateLanguageFilter,
                        onCreateNewVoice = { selectedTab = 1 },
                        onPlayOrPauseSample = { profile ->
                            if (playbackState.activeClipId == profile.id && playbackState.isPlaying) {
                                viewModel.pausePlayback()
                            } else {
                                viewModel.playProfileSample(profile)
                            }
                        },
                        onStopPlayback = viewModel::stopPlayback,
                        onReplaceAudio = { profile ->
                            profilePendingAudioReplace = profile
                            replaceProfileAudioLauncher.launch(arrayOf("audio/*"))
                        },
                        onShareAudio = { profile ->
                            viewModel.buildShareProfileAudioIntent(profile)?.let { intent ->
                                context.startActivity(Intent.createChooser(intent, profile.name))
                            }
                        },
                        onRetryReplication = viewModel::retryReplication,
                        onUpdateProfileDetails = { profile, name, lang, style ->
                            if (profile.providerType == VoiceProviderType.GEMINI_PRESET.name) {
                                viewModel.renameVoice(profile, name)
                            } else {
                                viewModel.updateProfileDetails(profile, name, lang, style)
                            }
                        },
                        onDeleteProfile = viewModel::deleteVoice,
                        onUsePresetInOnlineStudio = onUsePresetInOnlineStudio
                    )
                } else {
                    // Create My Voice (Personal Voice Replication) Tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = palette.amberWarning.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, palette.amberWarning.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Rounded.Info, contentDescription = null, tint = palette.amberWarning)
                                    Text(
                                        text = strings.replicationHonestyNote,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        item {
                            StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = voiceName,
                                    onValueChange = viewModel::updateVoiceName,
                                    label = { Text(strings.voiceNameLabel) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("create_voice_name_input")
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("bn-BD", "bn-IN", "en-US", "en-GB").forEach { code ->
                                        FilterChip(
                                            selected = selectedLang == code,
                                            onClick = { viewModel.updateLanguage(code) },
                                            label = { Text(code) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            StudioGlassCard(highlighted = recordingState.isRecording, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Voice Sample (16-bit PCM WAV)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(10.dp))

                                LiveWaveformView(
                                    bars = if (recordingState.isRecording) {
                                        recordingState.liveWaveform
                                    } else {
                                        List(32) { 0.16f }
                                    },
                                    progressFraction = 1f,
                                    isAnimating = recordingState.isRecording
                                )

                                if (recordingState.isRecording) {
                                    Text(
                                        text = "Recording: ${formatTimeMs(recordingState.elapsedMs)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.roseError
                                    )
                                } else if (recordingState.recordedFilePath != null) {
                                    Text(
                                        text = "Sample ready · ${formatTimeMs(recordingState.recordedDurationMs)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.emeraldStatus
                                    )
                                }

                                micPermissionError?.let { err ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = palette.roseError)
                                }
                                recordingState.errorMessage?.let { err ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(err, style = MaterialTheme.typography.bodySmall, color = palette.roseError)
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (recordingState.isRecording) {
                                                viewModel.stopRecording()
                                            } else {
                                                val hasMic = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (hasMic) {
                                                    micPermissionError = null
                                                    viewModel.startRecording()
                                                } else {
                                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (recordingState.isRecording) {
                                                palette.roseError
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            }
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("record_voice_sample_btn")
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = if (recordingState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                            contentDescription = if (recordingState.isRecording) {
                                                stringResource(R.string.cd_stop_record)
                                            } else {
                                                stringResource(R.string.cd_record)
                                            }
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (recordingState.isRecording) strings.stopRecordBtn else strings.recordSampleBtn,
                                            maxLines = 1
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { importAudioLauncher.launch(arrayOf("audio/*")) },
                                        enabled = !recordingState.isRecording,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("import_voice_sample_btn")
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.FileOpen,
                                            contentDescription = stringResource(R.string.cd_import_audio)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.importSampleBtn, maxLines = 1)
                                    }
                                }

                                if (recordingState.recordedFilePath != null && !recordingState.isRecording) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                if (playbackState.isPlaying) viewModel.pausePlayback() else viewModel.previewCurrentSample()
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = null
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (playbackState.isPlaying) strings.pauseBtn else strings.playBtn)
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::analyzeCurrentSampleTimbre,
                                            enabled = !isProcessing,
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text(strings.analyzeTimbreBtn)
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = timbreAnalysis != null) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp),
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    ) {
                                        Text(
                                            text = timbreAnalysis.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                                Text(strings.ownershipTitle, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = ownershipType == ConsentOwnerType.SELF,
                                        onClick = { viewModel.updateOwnershipType(ConsentOwnerType.SELF) }
                                    )
                                    Text(strings.ownershipSelf, style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = ownershipType == ConsentOwnerType.DOCUMENTED_PERMISSION,
                                        onClick = { viewModel.updateOwnershipType(ConsentOwnerType.DOCUMENTED_PERMISSION) }
                                    )
                                    Text(strings.ownershipPermission, style = MaterialTheme.typography.bodyMedium)
                                }

                                AnimatedVisibility(visible = ownershipType == ConsentOwnerType.DOCUMENTED_PERMISSION) {
                                    OutlinedTextField(
                                        value = permissionNote,
                                        onValueChange = viewModel::updatePermissionNote,
                                        label = { Text(strings.permissionNoteLabel) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = consentChecked,
                                        onCheckedChange = viewModel::updateConsentChecked,
                                        modifier = Modifier.testTag("consent_checkbox")
                                    )
                                    Text(
                                        text = strings.consentCheckboxText,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.saveVoiceProfileLocally(submitToReplicationApi = false) },
                                        enabled = !isProcessing,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("save_local_voice_btn")
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Text(strings.saveLocalVoiceBtn, maxLines = 1)
                                    }
                                    Button(
                                        onClick = { viewModel.saveVoiceProfileLocally(submitToReplicationApi = true) },
                                        enabled = !isProcessing,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("submit_replication_btn")
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        if (isProcessing) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                            Spacer(Modifier.width(6.dp))
                                        } else {
                                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(strings.submitReplicationBtn, maxLines = 1)
                                    }
                                }
                            }
                        }

                        if (!statusMessage.isNullOrBlank()) {
                            item {
                                val tone = if (isErrorStatus) palette.roseError else palette.emeraldStatus
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = tone.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, tone.copy(alpha = 0.55f))
                                ) {
                                    Text(
                                        text = statusMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
