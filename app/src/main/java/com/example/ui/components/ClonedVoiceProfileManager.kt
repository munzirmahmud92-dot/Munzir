package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackState
import com.example.data.local.ConsentOwnerType
import com.example.data.local.ReplicationStatus
import com.example.data.local.VoiceProfileEntity
import com.example.data.local.VoiceProviderType
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Reusable Compose UI component to search, list, inspect, play, edit,
 * attach/replace audio files, share, and delete cloned voice profiles
 * persisted in the local Room database.
 */
@Composable
fun ClonedVoiceProfileManager(
    personalVoices: List<VoiceProfileEntity>,
    presetVoices: List<VoiceProfileEntity>,
    searchQuery: String,
    languageFilter: String?,
    playbackState: PlaybackState,
    statusMessage: String?,
    isErrorStatus: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onLanguageFilterChange: (String?) -> Unit,
    onCreateNewVoice: () -> Unit,
    onPlayOrPauseSample: (VoiceProfileEntity) -> Unit,
    onStopPlayback: () -> Unit,
    onReplaceAudio: (VoiceProfileEntity) -> Unit,
    onShareAudio: (VoiceProfileEntity) -> Unit,
    onRetryReplication: (VoiceProfileEntity) -> Unit,
    onUpdateProfileDetails: (VoiceProfileEntity, String, String, String) -> Unit,
    onDeleteProfile: (VoiceProfileEntity) -> Unit,
    onUsePresetInOnlineStudio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current

    var profileToEdit by remember { mutableStateOf<VoiceProfileEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editLang by remember { mutableStateOf("bn-BD") }
    var editStyle by remember { mutableStateOf("") }
    var profileToDelete by remember { mutableStateOf<VoiceProfileEntity?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(strings.searchVoicesPlaceholder) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.clearText)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_cloned_voices_input")
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = languageFilter == null,
                    onClick = { onLanguageFilterChange(null) },
                    label = { Text(strings.filterAll) },
                    modifier = Modifier.testTag("voice_filter_all")
                )
                listOf("bn-BD", "bn-IN", "en-US", "en-GB").forEach { code ->
                    FilterChip(
                        selected = languageFilter == code,
                        onClick = {
                            onLanguageFilterChange(if (languageFilter == code) null else code)
                        },
                        label = { Text(code) },
                        modifier = Modifier.testTag("voice_filter_$code")
                    )
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
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        if (personalVoices.isEmpty()) {
            item {
                val hasActiveFilter = searchQuery.isNotBlank() || languageFilter != null
                EmptyStudioState(
                    icon = Icons.Rounded.Mic,
                    title = if (hasActiveFilter) strings.noMatchingVoices else strings.emptyVoicesTitle,
                    description = if (hasActiveFilter) strings.searchVoicesPlaceholder else strings.emptyVoicesDesc,
                    actionLabel = if (hasActiveFilter) strings.clearText else strings.tabCreateMyVoice,
                    onAction = {
                        if (hasActiveFilter) {
                            onSearchQueryChange("")
                            onLanguageFilterChange(null)
                        } else {
                            onCreateNewVoice()
                        }
                    }
                )
            }
        } else {
            items(personalVoices, key = { it.id }) { profile ->
                val isCurrentPlaying = playbackState.activeClipId == profile.id && playbackState.isPlaying
                val isCurrentPaused = playbackState.activeClipId == profile.id && playbackState.isPaused
                ClonedVoiceProfileCard(
                    profile = profile,
                    isPlaying = isCurrentPlaying,
                    isPaused = isCurrentPaused,
                    playbackState = playbackState,
                    onPlayOrPause = { onPlayOrPauseSample(profile) },
                    onStop = onStopPlayback,
                    onReplaceAudio = { onReplaceAudio(profile) },
                    onShareAudio = { onShareAudio(profile) },
                    onRetryReplication = { onRetryReplication(profile) },
                    onEdit = {
                        profileToEdit = profile
                        editName = profile.name
                        editLang = profile.languageCode
                        editStyle = profile.styleDescriptor
                    },
                    onDelete = { profileToDelete = profile },
                    onUseInStudio = null
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Gemini Studio Voices",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(presetVoices, key = { it.id }) { preset ->
            ClonedVoiceProfileCard(
                profile = preset,
                isPlaying = false,
                isPaused = false,
                playbackState = playbackState,
                onPlayOrPause = null,
                onStop = null,
                onReplaceAudio = null,
                onShareAudio = null,
                onRetryReplication = null,
                onEdit = {
                    profileToEdit = preset
                    editName = preset.name
                    editLang = preset.languageCode
                    editStyle = preset.styleDescriptor
                },
                onDelete = null,
                onUseInStudio = {
                    preset.geminiVoiceName?.let(onUsePresetInOnlineStudio)
                }
            )
        }
    }

    if (profileToEdit != null) {
        val editingPreset = profileToEdit?.providerType == VoiceProviderType.GEMINI_PRESET.name
        AlertDialog(
            onDismissRequest = { profileToEdit = null },
            title = { Text(if (editingPreset) strings.renameTitle else strings.editProfileTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(strings.voiceNameLabel) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_profile_name_input")
                    )
                    if (!editingPreset) {
                        OutlinedTextField(
                            value = editStyle,
                            onValueChange = { editStyle = it },
                            label = { Text(strings.voiceStyleLabel) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("bn-BD", "bn-IN", "en-US", "en-GB").forEach { code ->
                                FilterChip(
                                    selected = editLang == code,
                                    onClick = { editLang = code },
                                    label = { Text(code) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        profileToEdit?.let { target ->
                            onUpdateProfileDetails(target, editName, editLang, editStyle)
                        }
                        profileToEdit = null
                    },
                    modifier = Modifier.testTag("confirm_edit_profile_btn")
                ) {
                    Text(strings.confirmBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToEdit = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text(strings.deleteConfirmTitle) },
            text = { Text(strings.deleteConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        profileToDelete?.let(onDeleteProfile)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.roseError),
                    modifier = Modifier.testTag("confirm_delete_profile_btn")
                ) {
                    Text(strings.deleteBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }
}

@Composable
fun ClonedVoiceProfileCard(
    profile: VoiceProfileEntity,
    isPlaying: Boolean,
    isPaused: Boolean,
    playbackState: PlaybackState,
    onPlayOrPause: (() -> Unit)?,
    onStop: (() -> Unit)?,
    onReplaceAudio: (() -> Unit)?,
    onShareAudio: (() -> Unit)?,
    onRetryReplication: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onUseInStudio: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val isPreset = profile.providerType == VoiceProviderType.GEMINI_PRESET.name
    val dateStr = remember(profile.createdAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.createdAt))
    }

    val audioFile = remember(profile.sampleFilePath) {
        profile.sampleFilePath?.takeIf { it.isNotBlank() }?.let { File(it) }
    }
    val audioExists = remember(profile.sampleFilePath) {
        audioFile?.exists() == true && audioFile.length() > 0L
    }
    val audioSizeBytes = remember(profile.sampleFilePath) {
        if (audioExists) audioFile?.length() ?: 0L else 0L
    }

    StudioGlassCard(
        highlighted = isPlaying || isPaused,
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloned_voice_card_${profile.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isPreset) Icons.Rounded.RecordVoiceOver else Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile.languageCode} · ${profile.styleDescriptor} · $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .testTag("edit_profile_${profile.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_rename))
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .testTag("delete_profile_${profile.id}")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.cd_delete),
                            tint = palette.roseError
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profile.consentConfirmed) {
                Surface(
                    shape = CircleShape,
                    color = palette.emeraldStatus.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = palette.emeraldStatus,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = when {
                                profile.consentOwnerType == ConsentOwnerType.DOCUMENTED_PERMISSION.name -> "Permission Verified"
                                isPreset -> "Studio Preset"
                                else -> "Self Consent"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.emeraldStatus
                        )
                    }
                }
            }

            if (!isPreset) {
                val badgeTone = if (audioExists) palette.cyanGlow else palette.roseError
                Surface(
                    shape = CircleShape,
                    color = badgeTone.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (audioExists) Icons.Rounded.AudioFile else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = badgeTone,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (audioExists) strings.audioAttachedBadge else strings.audioMissingBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeTone
                        )
                    }
                }

                if (profile.hasEncryptedVoiceId) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                            Text("Encrypted ID", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    val repLabel = when (profile.replicationStatus) {
                        ReplicationStatus.UNAVAILABLE.name -> "API Unavailable"
                        ReplicationStatus.REPLICATED.name -> "Replicated"
                        else -> "Local Room DB"
                    }
                    Surface(
                        shape = CircleShape,
                        color = palette.amberWarning.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = repLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.amberWarning,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (!isPreset && audioFile != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "${strings.audioFileLabel}: ${audioFile.name}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (audioExists) {
                            "${formatTimeMs(profile.sampleDurationMs)} · ${formatBytesReadable(audioSizeBytes)}"
                        } else {
                            strings.audioMissingBadge
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (audioExists) MaterialTheme.colorScheme.onSurfaceVariant else palette.roseError
                    )
                    if (isPlaying || isPaused) {
                        Spacer(Modifier.height(8.dp))
                        val duration = playbackState.durationMs.coerceAtLeast(1L)
                        val progress = (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        LiveWaveformView(
                            bars = playbackState.waveformBars,
                            progressFraction = progress,
                            isAnimating = isPlaying,
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }
            }
        }

        if (profile.replicationMessage.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = profile.replicationMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onPlayOrPause != null && !profile.sampleFilePath.isNullOrBlank()) {
                OutlinedButton(
                    onClick = onPlayOrPause,
                    modifier = Modifier
                        .testTag("play_profile_audio_${profile.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) strings.pauseBtn else strings.playBtn,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPlaying) strings.pauseBtn else strings.playBtn)
                }
            }
            if ((isPlaying || isPaused) && onStop != null) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = strings.stopBtn, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.stopBtn)
                }
            }
            if (onReplaceAudio != null) {
                OutlinedButton(
                    onClick = onReplaceAudio,
                    modifier = Modifier
                        .testTag("replace_profile_audio_${profile.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (audioExists) strings.replaceAudioBtn else strings.attachAudioBtn)
                }
            }
            if (onShareAudio != null && audioExists) {
                OutlinedButton(
                    onClick = onShareAudio,
                    modifier = Modifier
                        .testTag("share_profile_audio_${profile.id}")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = strings.shareBtn, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.shareBtn)
                }
            }
            if (onRetryReplication != null) {
                OutlinedButton(
                    onClick = onRetryReplication,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Connect API")
                }
            }
            if (onUseInStudio != null) {
                Button(
                    onClick = onUseInStudio,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Use in Studio")
                }
            }
        }
    }
}
