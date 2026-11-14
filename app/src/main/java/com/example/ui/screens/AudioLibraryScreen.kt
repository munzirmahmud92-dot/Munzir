package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AudioClipEntity
import com.example.ui.components.AudioPreviewPlayerCard
import com.example.ui.components.EmptyStudioState
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioGlassCard
import com.example.ui.components.StudioTopBar
import com.example.ui.components.formatBytesReadable
import com.example.ui.components.formatTimeMs
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.viewmodel.AudioLibraryViewModel
import com.example.ui.viewmodel.DownloadFormatFilter
import java.text.DateFormat
import java.util.Date

@Composable
fun AudioLibraryScreen(
    viewModel: AudioLibraryViewModel,
    downloadedOnly: Boolean,
    onBack: () -> Unit,
    onOpenOnlineStudio: () -> Unit
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val context = LocalContext.current

    val allClips by viewModel.allClips.collectAsStateWithLifecycle()
    val downloadedClips by viewModel.downloadedClips.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val formatFilter by viewModel.formatFilter.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val busyClipId by viewModel.busyClipId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var clipToRename by remember { mutableStateOf<AudioClipEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var clipToDelete by remember { mutableStateOf<AudioClipEntity?>(null) }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    val displayedClips = remember(downloadedOnly, allClips, downloadedClips, formatFilter) {
        val base = if (downloadedOnly) downloadedClips else allClips
        if (!downloadedOnly) {
            base
        } else {
            when (formatFilter) {
                DownloadFormatFilter.ALL -> base
                DownloadFormatFilter.WAV -> base.filter { !it.wavFilePath.isNullOrBlank() }
                DownloadFormatFilter.MP3 -> base.filter { !it.mp3FilePath.isNullOrBlank() }
            }
        }
    }

    StudioBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                StudioTopBar(
                    title = if (downloadedOnly) strings.downloadedAudioTitle else strings.audioHistoryTitle,
                    subtitle = "${displayedClips.size} items",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (downloadedOnly) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = formatFilter == DownloadFormatFilter.ALL,
                                    onClick = { viewModel.setFormatFilter(DownloadFormatFilter.ALL) },
                                    label = { Text(strings.filterAll) }
                                )
                                FilterChip(
                                    selected = formatFilter == DownloadFormatFilter.WAV,
                                    onClick = { viewModel.setFormatFilter(DownloadFormatFilter.WAV) },
                                    label = { Text(strings.filterWav) }
                                )
                                FilterChip(
                                    selected = formatFilter == DownloadFormatFilter.MP3,
                                    onClick = { viewModel.setFormatFilter(DownloadFormatFilter.MP3) },
                                    label = { Text(strings.filterMp3) }
                                )
                            }
                        }
                    }

                    if (displayedClips.isEmpty()) {
                        item {
                            EmptyStudioState(
                                icon = if (downloadedOnly) Icons.Rounded.DownloadDone else Icons.Rounded.History,
                                title = if (downloadedOnly) strings.emptyDownloadsTitle else strings.emptyHistoryTitle,
                                description = if (downloadedOnly) strings.emptyDownloadsDesc else strings.emptyHistoryDesc,
                                actionLabel = strings.navOnlineStudio,
                                onAction = onOpenOnlineStudio
                            )
                        }
                    } else {
                        items(displayedClips, key = { it.id }) { clip ->
                            val isCurrent = playbackState.activeClipId == clip.id
                            val dateStr = remember(clip.createdAt) {
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(clip.createdAt))
                            }
                            val formatTags = buildList {
                                if (!clip.wavFilePath.isNullOrBlank()) add("WAV")
                                if (!clip.mp3FilePath.isNullOrBlank()) add("MP3")
                            }.joinToString(" + ")

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AudioPreviewPlayerCard(
                                    title = clip.title,
                                    subtitle = "${clip.voiceLabel} · ${clip.languageCode} · ${formatTimeMs(clip.durationMs)} · $formatTags · ${formatBytesReadable(clip.fileSizeBytes)} · $dateStr",
                                    playbackState = playbackState,
                                    isCurrentClip = isCurrent,
                                    isVerifiedPlayable = clip.isVerifiedPlayable,
                                    onPlay = {
                                        viewModel.playClip(
                                            clip = clip,
                                            preferMp3 = formatFilter == DownloadFormatFilter.MP3
                                        )
                                    },
                                    onPause = viewModel::pausePlayback,
                                    onStop = viewModel::stopPlayback,
                                    onSeek = viewModel::seekPlayback,
                                    onDownloadWav = { viewModel.exportWav(clip, strings.exportSuccessPrefix) },
                                    onConvertMp3 = { viewModel.exportMp3(clip, strings.exportSuccessPrefix) },
                                    onShare = {
                                        viewModel.buildShareIntent(clip)?.let { shareIntent ->
                                            context.startActivity(Intent.createChooser(shareIntent, clip.title))
                                        }
                                    },
                                    isExporting = busyClipId == clip.id
                                )

                                StudioGlassCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.verifyClip(clip) },
                                            modifier = Modifier
                                                .testTag("verify_clip_${clip.id}")
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(strings.verifyBtn)
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    viewModel.buildShareIntent(clip)?.let { shareIntent ->
                                                        context.startActivity(Intent.createChooser(shareIntent, clip.title))
                                                    }
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.cd_share))
                                            }
                                            IconButton(
                                                onClick = {
                                                    clipToRename = clip
                                                    renameText = clip.title
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize()
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_rename))
                                            }
                                            IconButton(
                                                onClick = { clipToDelete = clip },
                                                modifier = Modifier.minimumInteractiveComponentSize()
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
                            }
                        }
                    }
                }
            }
        }
    }

    if (clipToRename != null) {
        AlertDialog(
            onDismissRequest = { clipToRename = null },
            title = { Text(strings.renameTitle) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(strings.newNameLabel) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    clipToRename?.let { viewModel.renameClip(it, renameText) }
                    clipToRename = null
                }) {
                    Text(strings.confirmBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToRename = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }

    if (clipToDelete != null) {
        AlertDialog(
            onDismissRequest = { clipToDelete = null },
            title = { Text(strings.deleteConfirmTitle) },
            text = { Text(strings.deleteConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        clipToDelete?.let(viewModel::deleteClip)
                        clipToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.roseError)
                ) {
                    Text(strings.deleteBtn)
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToDelete = null }) {
                    Text(strings.cancelBtn)
                }
            }
        )
    }
}
