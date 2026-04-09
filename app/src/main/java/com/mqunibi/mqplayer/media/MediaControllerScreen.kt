package com.mqunibi.mqplayer.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mqunibi.mqplayer.R
import com.mqunibi.mqplayer.ui.MessageCard
import com.mqunibi.mqplayer.ui.stringResourceSafe
import com.mqunibi.mqplayer.ui.theme.MQPlayerTheme

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResourceSafe(R.string.screen_title), fontWeight = FontWeight.Bold)
        Text(text = stringResourceSafe(R.string.screen_subtitle))

        if (!state.permissionGranted) {
            if (showRestrictedSettingsHint) {
                MessageCard(
                    title = stringResourceSafe(R.string.restricted_settings_title),
                    body = stringResourceSafe(R.string.restricted_settings_body),
                ) {
                    Button(onClick = onAllowRestrictedSettings) {
                        Text(text = stringResourceSafe(R.string.restricted_settings_button))
                    }
                }
            }
            MessageCard(
                title = stringResourceSafe(R.string.grant_access_title),
                body = stringResourceSafe(R.string.grant_access_body),
            ) {
                Button(onClick = onGrantAccess) {
                    Text(text = stringResourceSafe(R.string.grant_access_button))
                }
            }
        }

        MessageCard(
            title = stringResourceSafe(R.string.status_title),
            body = state.title ?: stringResourceSafe(R.string.status_idle),
        ) {
            val source = state.currentAppName ?: state.currentPackageName ?: "—"
            Text(text = stringResourceSafe(R.string.current_source_format, source))
            Text(text = state.playbackLabel)
            if (!state.subtitle.isNullOrBlank()) Text(text = state.subtitle)
            Text(text = stringResourceSafe(R.string.session_count_format, state.activeSessionCount))
            if (state.availableSessions.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResourceSafe(R.string.active_sessions_title),
                    fontWeight = FontWeight.SemiBold,
                )
                state.availableSessions.forEach { Text(text = "• $it") }
            } else {
                Text(text = stringResourceSafe(R.string.status_hint))
            }
            state.errorMessage?.takeIf(String::isNotBlank)?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = it)
            }
            TextButton(onClick = onRefresh) {
                Text(text = stringResourceSafe(R.string.refresh_button))
            }
        }

        MessageCard(
            title = stringResourceSafe(R.string.controls_title),
            body = stringResourceSafe(R.string.android_auto_hint),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSkipPrevious, enabled = state.canSkipPrevious) {
                    Icon(painterResource(R.drawable.ic_skip_previous), stringResourceSafe(R.string.previous_button))
                }
                IconButton(onClick = onUndo10Seconds, enabled = state.canSeekBackward) {
                    Icon(painterResource(R.drawable.ic_replay_10), stringResourceSafe(R.string.undo_10_seconds_button))
                }
                IconButton(onClick = onTogglePlayback, enabled = state.canPlay || state.canPause) {
                    Icon(
                        painter = painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (state.isPlaying) stringResourceSafe(R.string.pause_button) else stringResourceSafe(R.string.play_button),
                    )
                }
                IconButton(onClick = onSkip30Seconds, enabled = state.canSeekForward) {
                    Icon(painterResource(R.drawable.ic_forward_30), stringResourceSafe(R.string.skip_30_seconds_button))
                }
                IconButton(onClick = onSkipNext, enabled = state.canSkipNext) {
                    Icon(painterResource(R.drawable.ic_skip_next), stringResourceSafe(R.string.next_button))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaControllerScreenPreview() {
    MQPlayerTheme {
        MediaControllerScreen(
            state = ActiveMediaState(
                permissionGranted = true,
                currentAppName = "Spotify",
                currentPackageName = "com.spotify.music",
                title = "Example Track",
                subtitle = "Example Artist",
                playbackLabel = "Playing",
                isPlaying = true,
                canPause = true,
                canSkipNext = true,
                canSkipPrevious = true,
                canSeekBackward = true,
                canSeekForward = true,
                activeSessionCount = 2,
                availableSessions = listOf("Spotify • Playing", "Pocket Casts • Paused"),
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
        )
    }
}

@Preview(showBackground = true, name = "Restricted settings hint")
@Composable
private fun MediaControllerScreenRestrictedPreview() {
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
        )
    }
}
