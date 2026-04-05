package com.mqunibi.mqplayer.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mqunibi.mqplayer.R
import com.mqunibi.mqplayer.ui.MessageCard
import com.mqunibi.mqplayer.ui.stringResourceSafe
import com.mqunibi.mqplayer.ui.theme.MQPlayerTheme

@Composable
internal fun SettingsScreen(
    settingsState: AppSettingsState,
    hasUpdate: Boolean,
    updateUrl: String?,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onOpenRepository: () -> Unit,
    onOpenProfile: () -> Unit,
    onDownloadUpdate: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }
    var manualCheckTriggered by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MessageCard(
            title = stringResourceSafe(R.string.settings_title),
            body = stringResourceSafe(R.string.settings_body),
        ) {
            Text(text = stringResourceSafe(R.string.theme_mode_title), fontWeight = FontWeight.SemiBold)
            ThemeMode.entries.forEach { themeMode ->
                ThemeModeOption(
                    label = themeMode.label(),
                    selected = settingsState.themeMode == themeMode,
                    onClick = { onThemeModeSelected(themeMode) },
                )
            }
        }

        MessageCard(
            title = stringResourceSafe(R.string.about_title),
            body = stringResourceSafe(R.string.about_body),
        ) {
            Text(
                text = stringResourceSafe(R.string.version_label, currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenRepository,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResourceSafe(R.string.repo_link_button))
                }
                OutlinedButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResourceSafe(R.string.profile_link_button))
                }
            }

            when {
                isCheckingUpdate -> {
                    FilledTonalButton(
                        enabled = false,
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResourceSafe(R.string.checking_label))
                    }
                }
                manualCheckTriggered && hasUpdate && updateUrl != null -> {
                    Button(
                        onClick = { onDownloadUpdate(updateUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResourceSafe(R.string.update_download_button))
                    }
                }
                manualCheckTriggered && !hasUpdate -> {
                    FilledTonalButton(
                        onClick = { onCheckUpdate() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "✓  ${stringResourceSafe(R.string.up_to_date_label)}")
                    }
                }
                else -> {
                    FilledTonalButton(
                        onClick = {
                            manualCheckTriggered = true
                            onCheckUpdate()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResourceSafe(R.string.check_update_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label)
    }
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> stringResourceSafe(R.string.theme_mode_system)
    ThemeMode.LIGHT  -> stringResourceSafe(R.string.theme_mode_light)
    ThemeMode.DARK   -> stringResourceSafe(R.string.theme_mode_dark)
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MQPlayerTheme {
        SettingsScreen(
            settingsState = AppSettingsState(themeMode = ThemeMode.SYSTEM),
            hasUpdate = false,
            updateUrl = null,
            isCheckingUpdate = false,
            onCheckUpdate = {},
            onThemeModeSelected = {},
            onOpenRepository = {},
            onOpenProfile = {},
            onDownloadUpdate = {},
        )
    }
}


