package com.mqunibi.mqplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import com.mqunibi.mqplayer.media.ActiveMediaRepository
import com.mqunibi.mqplayer.media.MediaControllerScreen
import com.mqunibi.mqplayer.settings.AppSettingsRepository
import com.mqunibi.mqplayer.settings.SettingsActivity
import com.mqunibi.mqplayer.ui.stringResourceSafe
import com.mqunibi.mqplayer.ui.theme.MQPlayerTheme
import com.mqunibi.mqplayer.ui.theme.rememberAlbumSeedColor

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActiveMediaRepository.initialize(applicationContext)
        AppSettingsRepository.initialize(applicationContext)
        val showRestrictedSettingsHint = isSideloaded()
        enableEdgeToEdge()
        setContent {
            val mediaState by ActiveMediaRepository.state.collectAsState()
            val settingsState by AppSettingsRepository.state.collectAsState()
            val updateState by UpdateRepository.state.collectAsState()

            var showUpdateDialog by remember { mutableStateOf(false) }

            val currentVersion = remember {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            }
            val latestTag = updateState.latestTag
            val hasUpdate = latestTag != null && isNewerVersion(currentVersion, latestTag)
            val updateUrl = updateState.downloadUrl

            LaunchedEffect(Unit) {
                UpdateRepository.checkForUpdates()
            }

            LaunchedEffect(hasUpdate) {
                if (hasUpdate) showUpdateDialog = true
            }

            MQPlayerTheme(
                themeMode = settingsState.themeMode,
                seedColor = rememberAlbumSeedColor(mediaState.albumArt),
            ) {
                if (showUpdateDialog && latestTag != null && hasUpdate && updateUrl != null) {
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = { Text(stringResourceSafe(R.string.update_available_title)) },
                        text = { Text(stringResourceSafe(R.string.update_available_body, latestTag)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showUpdateDialog = false
                                openUrl(updateUrl)
                            }) {
                                Text(stringResourceSafe(R.string.update_download_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text(stringResourceSafe(R.string.update_dismiss_button))
                            }
                        },
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                            title = {
                                Text(
                                    text = stringResourceSafe(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            actions = {
                                IconButton(onClick = {
                                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings),
                                        contentDescription = stringResourceSafe(R.string.open_settings_button),
                                    )
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    MediaControllerScreen(
                        state = mediaState,
                        onGrantAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onAllowRestrictedSettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null),
                                )
                            )
                        },
                        showRestrictedSettingsHint = showRestrictedSettingsHint && !mediaState.permissionGranted,
                        onRefresh = ActiveMediaRepository::refresh,
                        onTogglePlayback = ActiveMediaRepository::togglePlayPause,
                        onSkipPrevious = ActiveMediaRepository::skipToPrevious,
                        onSkipNext = ActiveMediaRepository::skipToNext,
                        onUndo10Seconds = ActiveMediaRepository::seekBackward10Seconds,
                        onSkip30Seconds = ActiveMediaRepository::seekForward30Seconds,
                        onToggleLoop = ActiveMediaRepository::toggleLoop,
                        onSeekTo = ActiveMediaRepository::seekTo,
                        onSwitchSession = ActiveMediaRepository::switchToSession,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ActiveMediaRepository.refresh()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    /**
     * Returns true when the app is running on Android 13+ and was NOT installed through the
     * Play Store. In that case Android restricts notification listener access behind an extra
     * "Allow restricted settings" gate in the app-info screen.
     */
    private fun isSideloaded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val installer = packageManager.getInstallSourceInfo(packageName).installingPackageName
            installer != "com.android.vending"
        } catch (_: Exception) {
            true
        }
    }
}
