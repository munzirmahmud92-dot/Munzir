package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PlaybackState
import com.example.data.preferences.ConnectionMode
import com.example.data.preferences.StudioSettings
import com.example.ui.components.ConnectivityStatusPill
import com.example.ui.components.LiveWaveformView
import com.example.ui.components.MiniNowPlayingBar
import com.example.ui.components.StudioBackground
import com.example.ui.components.StudioGlassCard
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette

private data class DashboardTile(
    val tag: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColors: List<Color>,
    val badge: String? = null,
    val onClick: () -> Unit
)

@Composable
fun DashboardScreen(
    settings: StudioSettings,
    isOnline: Boolean,
    voiceCount: Int,
    clipCount: Int,
    downloadedCount: Int,
    playbackState: PlaybackState,
    onToggleMiniPlay: () -> Unit,
    onStopMiniPlay: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenOnlineStudio: () -> Unit,
    onOpenOfflineStudio: () -> Unit,
    onOpenVoiceLibrary: (openCreateTab: Boolean) -> Unit,
    onOpenAudioHistory: () -> Unit,
    onOpenDownloadedAudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val isForcedOffline = settings.connectionMode == ConnectionMode.OFFLINE_ONLY

    val tiles = listOf(
        DashboardTile(
            tag = "tile_online_studio",
            title = strings.navOnlineStudio,
            subtitle = strings.navOnlineStudioDesc,
            icon = Icons.Rounded.AutoAwesome,
            accentColors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
            badge = "Gemini TTS",
            onClick = onOpenOnlineStudio
        ),
        DashboardTile(
            tag = "tile_offline_studio",
            title = strings.navOfflineStudio,
            subtitle = strings.navOfflineStudioDesc,
            icon = Icons.Rounded.OfflineBolt,
            accentColors = listOf(MaterialTheme.colorScheme.secondary, palette.cyanGlow),
            badge = "Offline",
            onClick = onOpenOfflineStudio
        ),
        DashboardTile(
            tag = "tile_voice_library",
            title = strings.navVoiceLibrary,
            subtitle = strings.navVoiceLibraryDesc,
            icon = Icons.Rounded.LibraryMusic,
            accentColors = listOf(Color(0xFF7C3AED), Color(0xFFEC4899)),
            badge = "$voiceCount",
            onClick = { onOpenVoiceLibrary(false) }
        ),
        DashboardTile(
            tag = "tile_audio_history",
            title = strings.navAudioHistory,
            subtitle = strings.navAudioHistoryDesc,
            icon = Icons.Rounded.History,
            accentColors = listOf(Color(0xFF2563EB), Color(0xFF06B6D4)),
            badge = "$clipCount",
            onClick = onOpenAudioHistory
        ),
        DashboardTile(
            tag = "tile_downloaded_audio",
            title = strings.navDownloadedAudio,
            subtitle = strings.navDownloadedAudioDesc,
            icon = Icons.Rounded.DownloadDone,
            accentColors = listOf(Color(0xFF059669), Color(0xFF38BDF8)),
            badge = "$downloadedCount",
            onClick = onOpenDownloadedAudio
        ),
        DashboardTile(
            tag = "tile_settings",
            title = strings.navSettings,
            subtitle = strings.navSettingsDesc,
            icon = Icons.Rounded.Settings,
            accentColors = listOf(Color(0xFF475569), Color(0xFF6366F1)),
            onClick = onOpenSettings
        )
    )

    StudioBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                MiniNowPlayingBar(
                    playbackState = playbackState,
                    onTogglePlay = onToggleMiniPlay,
                    onStop = onStopMiniPlay
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                val isWide = maxWidth >= 600.dp

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Top Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.accentGradientBrush),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = stringResource(R.string.cd_app_logo),
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = strings.appTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = strings.studioSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = onToggleLanguage,
                                modifier = Modifier
                                    .testTag("dashboard_language_toggle")
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = if (settings.appLanguage == AppLanguage.ENGLISH) "বাং" else "EN",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    item {
                        // Hero Studio Card with Generated Art + Waveform
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 6.dp
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(215.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_studio_hero),
                                    contentDescription = stringResource(R.string.cd_hero_banner),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xEE060916),
                                                    Color(0xCC0A0F24),
                                                    Color(0x550A0F24)
                                                )
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    ConnectivityStatusPill(
                                        isOnline = isOnline,
                                        isForcedOffline = isForcedOffline
                                    )

                                    Column {
                                        Text(
                                            text = strings.dashboardTagline,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFFE2E8F0),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        LiveWaveformView(
                                            bars = listOf(
                                                0.28f, 0.48f, 0.72f, 0.94f, 0.62f, 0.35f, 0.55f, 0.88f,
                                                0.96f, 0.64f, 0.42f, 0.75f, 0.50f, 0.32f, 0.66f, 0.82f,
                                                0.45f, 0.30f, 0.58f, 0.90f, 0.70f, 0.40f, 0.62f, 0.34f
                                            ),
                                            progressFraction = 1f,
                                            isAnimating = true,
                                            modifier = Modifier.height(34.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // Studio Metrics Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatPillCard(
                                label = strings.statsVoices,
                                value = voiceCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            StatPillCard(
                                label = strings.statsClips,
                                value = clipCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            StatPillCard(
                                label = strings.statsExports,
                                value = downloadedCount.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        // Quick "Create My Voice" Personal Voice Replication CTA
                        StudioGlassCard(
                            highlighted = true,
                            onClick = { onOpenVoiceLibrary(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cta_create_my_voice")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentGradientBrush),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = strings.quickCreateVoiceTitle,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = strings.quickCreateVoiceDesc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { onOpenVoiceLibrary(true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(strings.createMyVoiceBtn, maxLines = 1)
                                }
                            }
                        }
                    }

                    // 6 Main Studio Navigation Modules (2-column grid on tablets/wide, 2-column compact or 1-column)
                    if (isWide) {
                        val rows = tiles.chunked(2)
                        items(rows.size) { rowIdx ->
                            val rowTiles = rows[rowIdx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowTiles.forEach { tile ->
                                    DashboardModuleCard(
                                        tile = tile,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        val rows = tiles.chunked(2)
                        items(rows.size) { rowIdx ->
                            val rowTiles = rows[rowIdx]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowTiles.forEach { tile ->
                                    DashboardModuleCard(
                                        tile = tile,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPillCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    StudioGlassCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DashboardModuleCard(
    tile: DashboardTile,
    modifier: Modifier = Modifier
) {
    StudioGlassCard(
        onClick = tile.onClick,
        modifier = modifier
            .testTag(tile.tag)
            .minimumInteractiveComponentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(tile.accentColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = tile.title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (!tile.badge.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = tile.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = tile.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
