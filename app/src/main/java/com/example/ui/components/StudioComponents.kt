package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.PlaybackState
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.LocalStudioPalette
import java.util.Locale
import kotlin.math.sin

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

fun formatBytesReadable(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

@Composable
fun StudioBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalStudioPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundBrush)
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val strings = LocalStrings.current
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = strings.playBtn
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun StudioGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalStudioPalette.current
    val borderBrush = if (highlighted) {
        palette.accentGradientBrush
    } else {
        Brush.linearGradient(listOf(palette.cardBorder, palette.cardBorder.copy(alpha = 0.5f)))
    }
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (highlighted) 6.dp else 2.dp,
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, borderBrush)
    ) {
        Column(
            modifier = Modifier
                .background(palette.cardGradientBrush)
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun ConnectivityStatusPill(
    isOnline: Boolean,
    isForcedOffline: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val (label, color, icon) = when {
        isForcedOffline -> Triple(strings.offlineForcedStatus, palette.amberWarning, Icons.Rounded.WifiOff)
        isOnline -> Triple(strings.onlineStatus, palette.emeraldStatus, Icons.Rounded.Wifi)
        else -> Triple(strings.offlineStatus, palette.roseError, Icons.Rounded.WifiOff)
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun LiveWaveformView(
    bars: List<Float>,
    progressFraction: Float,
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val safeBars = if (bars.isEmpty()) List(40) { 0.22f } else bars

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .semantics { contentDescription = "Audio waveform visualization" }
    ) {
        val count = safeBars.size.coerceAtLeast(1)
        val totalSpacing = size.width * 0.34f
        val gap = totalSpacing / (count + 1)
        val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(2.dp.toPx())
        val activeThreshold = (progressFraction.coerceIn(0f, 1f) * count).toInt()

        for (i in 0 until count) {
            val baseAmp = safeBars[i].coerceIn(0.08f, 1f)
            val animatedScale = if (isAnimating) {
                (0.84f + 0.22f * sin(phase + i * 0.45f)).coerceIn(0.5f, 1.15f)
            } else {
                1f
            }
            val barHeight = (size.height * baseAmp * animatedScale).coerceIn(6.dp.toPx(), size.height)
            val x = gap + i * (barWidth + gap)
            val y = (size.height - barHeight) / 2f

            val brush = if (i <= activeThreshold || isAnimating) {
                Brush.verticalGradient(listOf(palette.cyanGlow, primary, secondary))
            } else {
                Brush.verticalGradient(listOf(palette.waveformInactive, palette.waveformInactive))
            }

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun AudioPreviewPlayerCard(
    title: String,
    subtitle: String,
    playbackState: PlaybackState,
    isCurrentClip: Boolean,
    isVerifiedPlayable: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onDownloadWav: (() -> Unit)? = null,
    onConvertMp3: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isExporting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current
    val isPlaying = isCurrentClip && playbackState.isPlaying
    val isPaused = isCurrentClip && playbackState.isPaused
    val duration = if (isCurrentClip && playbackState.durationMs > 0L) playbackState.durationMs else 1L
    val position = if (isCurrentClip) playbackState.positionMs.coerceIn(0L, duration) else 0L
    val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    StudioGlassCard(modifier = modifier, highlighted = isPlaying) {
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentGradientBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isVerifiedPlayable) {
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
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = strings.verifiedPlayableBadge,
                            tint = palette.emeraldStatus,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = strings.verifiedPlayableBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.emeraldStatus
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LiveWaveformView(
            bars = if (isCurrentClip) playbackState.waveformBars else emptyList(),
            progressFraction = if (isCurrentClip) progress else 0f,
            isAnimating = isPlaying
        )

        Slider(
            value = progress,
            onValueChange = { frac ->
                if (isCurrentClip && playbackState.durationMs > 0L) {
                    onSeek((frac * playbackState.durationMs).toLong())
                }
            },
            enabled = isCurrentClip && (isPlaying || isPaused),
            colors = SliderDefaults.colors(
                thumbColor = palette.cyanGlow,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTimeMs(position)} / ${formatTimeMs(if (isCurrentClip) playbackState.durationMs else 0L)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = { if (isPlaying) onPause() else onPlay() },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) strings.pauseBtn else strings.playBtn
                    )
                }
                IconButton(
                    onClick = onStop,
                    enabled = isCurrentClip && (isPlaying || isPaused),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = strings.stopBtn
                    )
                }
                if (onShare != null) {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = strings.shareBtn
                        )
                    }
                }
            }
        }

        if (onDownloadWav != null || onConvertMp3 != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDownloadWav != null) {
                    OutlinedButton(
                        onClick = onDownloadWav,
                        enabled = !isExporting,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.downloadWavBtn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onConvertMp3 != null) {
                    Button(
                        onClick = onConvertMp3,
                        enabled = !isExporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Text(strings.convertMp3Btn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniNowPlayingBar(
    playbackState: PlaybackState,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (playbackState.activeFilePath == null) return
    val strings = LocalStrings.current
    val palette = LocalStudioPalette.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, palette.accentGradientBrush),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = palette.cyanGlow,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playbackState.activeTitle.ifBlank { "Studio Audio" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatTimeMs(playbackState.positionMs)} / ${formatTimeMs(playbackState.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) strings.pauseBtn else strings.playBtn
                )
            }
            IconButton(
                onClick = onStop,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = strings.stopBtn
                )
            }
        }
    }
}

@Composable
fun EmptyStudioState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalStudioPalette.current
    StudioGlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(palette.accentGradientBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
