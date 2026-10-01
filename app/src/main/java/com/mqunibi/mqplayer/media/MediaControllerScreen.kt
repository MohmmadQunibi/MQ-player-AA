package com.mqunibi.mqplayer.media

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mqunibi.mqplayer.R
import com.mqunibi.mqplayer.ui.MessageCard
import com.mqunibi.mqplayer.ui.stringResourceSafe
import com.mqunibi.mqplayer.ui.theme.MQPlayerTheme

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@Composable
internal fun MediaControllerScreen(
    state: ActiveMediaState,
    onGrantAccess: () -> Unit,
    onAllowRestrictedSettings: () -> Unit,
    showRestrictedSettingsHint: Boolean,
    onRefresh: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onUndo10Seconds: () -> Unit,
    onSkip30Seconds: () -> Unit,
    onToggleLoop: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
    onSwitchSession: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (!state.permissionGranted) {
            Spacer(Modifier.height(16.dp))
            MessageCard(
                title = stringResourceSafe(R.string.grant_access_title),
                body = stringResourceSafe(R.string.grant_access_body),
            ) {
                Button(
                    onClick = onGrantAccess,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(stringResourceSafe(R.string.grant_access_button))
                }
            }
            if (showRestrictedSettingsHint) {
                Spacer(Modifier.height(12.dp))
                MessageCard(
                    title = stringResourceSafe(R.string.restricted_settings_title),
                    body = stringResourceSafe(R.string.restricted_settings_body),
                ) {
                    Button(
                        onClick = onAllowRestrictedSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResourceSafe(R.string.restricted_settings_button))
                    }
                }
            }
            return@Column
        }

        Spacer(Modifier.height(20.dp))
        AlbumArtSection(state)
        Spacer(Modifier.height(28.dp))

        val title = state.title ?: stringResourceSafe(R.string.status_idle)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        val metaLine = buildString {
            if (!state.subtitle.isNullOrBlank()) append(state.subtitle)
            val app = state.currentAppName ?: state.currentPackageName
            if (!app.isNullOrBlank()) {
                if (isNotEmpty()) append("  ·  ")
                append(app)
            }
        }.ifEmpty { state.playbackLabel }
        Text(
            text = metaLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
        ProgressSection(state, onSeekTo)

        Spacer(Modifier.height(12.dp))
        ControlsRow(state, onTogglePlayback, onSkipPrevious, onSkipNext, onUndo10Seconds, onSkip30Seconds)

        Spacer(Modifier.height(8.dp))
        SecondaryControls(state, onToggleLoop, onRefresh)

        if (state.activeSessionCount > 1) {
            Spacer(Modifier.height(24.dp))
            SessionSwitcher(state, onSwitchSession)
        }

        state.errorMessage?.takeIf(String::isNotBlank)?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AlbumArtSection(state: ActiveMediaState) {
    val artSize = 260.dp
    val primary = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(artSize + 40.dp),
    ) {
        Box(
            modifier = Modifier
                .size(artSize)
                .blur(32.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.30f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
        val hasArt = state.albumArtUri != null || state.albumArt != null
        Box(
            modifier = Modifier
                .size(artSize)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (hasArt) {
                AsyncImage(
                    model = state.albumArtUri ?: state.albumArt,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(state: ActiveMediaState, onSeekTo: (Long) -> Unit) {
    val duration = state.durationMs
    val position = state.positionMs
    val hasProgress = duration != null && duration > 0 && position != null

    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val durationSafe = duration ?: 0L
    val sliderValue = if (isDragging) {
        dragValue
    } else {
        if (hasProgress && durationSafe > 0) (position.toFloat() / durationSafe.toFloat()).coerceIn(0f, 1f) else 0f
    }

    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { v ->
                isDragging = true
                dragValue = v
            },
            onValueChangeFinished = {
                if (hasProgress && isDragging) {
                    onSeekTo((dragValue * durationSafe).toLong())
                }
                isDragging = false
            },
            enabled = hasProgress,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = secondary.copy(alpha = 0.3f),
                disabledThumbColor = secondary.copy(alpha = 0.4f),
                disabledActiveTrackColor = secondary.copy(alpha = 0.2f),
                disabledInactiveTrackColor = secondary.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (position != null) formatDuration(position) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
            )
            Text(
                text = if (duration != null) formatDuration(duration) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
            )
        }
    }
}

@Composable
private fun ControlsRow(
    state: ActiveMediaState,
    onTogglePlayback: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onUndo10Seconds: () -> Unit,
    onSkip30Seconds: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSkipPrevious, enabled = state.canSkipPrevious) {
            Icon(painterResource(R.drawable.ic_skip_previous), stringResourceSafe(R.string.previous_button), modifier = Modifier.size(26.dp))
        }

        IconButton(onClick = onUndo10Seconds, enabled = state.canSeekBackward) {
            Icon(painterResource(R.drawable.ic_replay_10), stringResourceSafe(R.string.undo_10_seconds_button), modifier = Modifier.size(28.dp))
        }

        val playEnabled = state.canPlay || state.canPause
        val playBg by animateColorAsState(
            targetValue = if (playEnabled) primary else onSurfaceVariant.copy(alpha = 0.3f),
            animationSpec = tween(200),
            label = "playBg",
        )
        FilledIconButton(
            onClick = onTogglePlayback,
            enabled = playEnabled,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = playBg,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = onSurfaceVariant.copy(alpha = 0.2f),
                disabledContentColor = onSurfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Icon(
                painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (state.isPlaying) stringResourceSafe(R.string.pause_button) else stringResourceSafe(R.string.play_button),
                modifier = Modifier.size(30.dp),
            )
        }

        IconButton(onClick = onSkip30Seconds, enabled = state.canSeekForward) {
            Icon(painterResource(R.drawable.ic_forward_30), stringResourceSafe(R.string.skip_30_seconds_button), modifier = Modifier.size(28.dp))
        }

        IconButton(onClick = onSkipNext, enabled = state.canSkipNext) {
            Icon(painterResource(R.drawable.ic_skip_next), stringResourceSafe(R.string.next_button), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun SecondaryControls(
    state: ActiveMediaState,
    onToggleLoop: () -> Unit,
    onRefresh: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onRefresh) {
            Text(
                text = stringResourceSafe(R.string.refresh_button),
                style = MaterialTheme.typography.labelLarge,
                color = onSurfaceVariant,
            )
        }

        val loopTint by animateColorAsState(
            targetValue = if (state.loopEnabled) primary else onSurfaceVariant,
            animationSpec = tween(200),
            label = "loopTint",
        )
        val loopScale by animateFloatAsState(
            targetValue = if (state.loopEnabled) 1.15f else 1f,
            animationSpec = tween(200),
            label = "loopScale",
        )
        IconButton(onClick = onToggleLoop, enabled = state.permissionGranted) {
            Icon(
                painter = painterResource(if (state.loopEnabled) R.drawable.ic_repeat_on else R.drawable.ic_repeat),
                contentDescription = stringResourceSafe(R.string.loop_button),
                tint = loopTint,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { scaleX = loopScale; scaleY = loopScale },
            )
        }
    }
}

@Composable
private fun SessionSwitcher(state: ActiveMediaState, onSwitchSession: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sources",
            style = MaterialTheme.typography.labelLarge,
            color = onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.sessionInfos.forEach { session ->
                val isActive = session.packageName == state.currentPackageName
                FilterChip(
                    selected = isActive,
                    onClick = { onSwitchSession(session.mediaId) },
                    label = { Text(session.appName, style = MaterialTheme.typography.labelLarge) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primary.copy(alpha = 0.15f),
                        selectedLabelColor = primary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isActive,
                        selectedBorderColor = primary,
                        borderColor = onSurfaceVariant.copy(alpha = 0.3f),
                    ),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0F)
@Composable
private fun MediaControllerScreenPreview() {
    MQPlayerTheme {
        MediaControllerScreen(
            state = ActiveMediaState(
                permissionGranted = true,
                currentAppName = "Spotify",
                currentPackageName = "com.spotify.music",
                title = "Bohemian Rhapsody",
                subtitle = "Queen",
                playbackLabel = "Playing",
                isPlaying = true,
                canPause = true,
                canSkipNext = true,
                canSkipPrevious = true,
                canSeekBackward = true,
                canSeekForward = true,
                positionMs = 82_000L,
                durationMs = 354_000L,
                activeSessionCount = 2,
                sessionInfos = listOf(
                    SessionInfo("session:com.spotify.music", "Spotify", "com.spotify.music", "Bohemian Rhapsody", "Queen", "Playing", true),
                    SessionInfo("session:au.com.shiftyjelly.pocketcasts", "Pocket Casts", "au.com.shiftyjelly.pocketcasts", "Episode", null, "Paused", false),
                ),
            ),
            onGrantAccess = {},
            onAllowRestrictedSettings = {},
            showRestrictedSettingsHint = false,
            onRefresh = {},
            onTogglePlayback = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onUndo10Seconds = {},
            onSkip30Seconds = {},
            onToggleLoop = {},
        )
    }
}

@Preview(showBackground = true, name = "Permission denied")
@Composable
private fun MediaControllerScreenPermissionPreview() {
    MQPlayerTheme {
        MediaControllerScreen(
            state = ActiveMediaState(permissionGranted = false),
            onGrantAccess = {},
            onAllowRestrictedSettings = {},
            showRestrictedSettingsHint = true,
            onRefresh = {},
            onTogglePlayback = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onUndo10Seconds = {},
            onSkip30Seconds = {},
            onToggleLoop = {},
        )
    }
}
