package com.mqunibi.mqplayer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import androidx.palette.graphics.Palette
import com.mqunibi.mqplayer.media.ActiveMediaRepository
import com.mqunibi.mqplayer.media.MediaControllerScreen
import com.mqunibi.mqplayer.settings.AppSettingsRepository
import com.mqunibi.mqplayer.settings.SettingsScreen
import com.mqunibi.mqplayer.ui.stringResourceSafe
import com.mqunibi.mqplayer.ui.theme.MQPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppScreen { PLAYER, SETTINGS }

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
            var currentScreen by rememberSaveable { mutableStateOf(AppScreen.PLAYER) }

            val scope = rememberCoroutineScope()

            // Extract dominant color from album art to seed the theme
            var rawSeedColor by remember { mutableStateOf<Color?>(null) }
            LaunchedEffect(mediaState.albumArt) {
                val bitmap = mediaState.albumArt
                rawSeedColor = if (bitmap != null) {
                    withContext(Dispatchers.Default) {
                        val palette = Palette.from(bitmap).generate()
                        val swatch = palette.vibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.lightVibrantSwatch
                            ?: palette.mutedSwatch
                        swatch?.rgb?.let { Color(it) }
                    }
                } else null
            }

            var latestRelease by remember { mutableStateOf<Pair<String, String>?>(null) }
            var isCheckingUpdate by remember { mutableStateOf(false) }
            var showUpdateDialog by remember { mutableStateOf(false) }

            val currentVersion = remember {
                packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            }
            val hasUpdate = latestRelease != null && isNewerVersion(currentVersion, latestRelease!!.first)
            val updateUrl = latestRelease?.second

            LaunchedEffect(Unit) {
                isCheckingUpdate = true
                latestRelease = fetchLatestRelease()
                isCheckingUpdate = false
            }

            LaunchedEffect(hasUpdate) {
                if (hasUpdate) showUpdateDialog = true
            }

            // Animate seed color transitions smoothly (600ms) when track changes
            val animatedSeed by animateColorAsState(
                targetValue = rawSeedColor ?: Color.Transparent,
                animationSpec = tween(600),
                label = "seedColor",
            )
            val seedColor = if (rawSeedColor != null) animatedSeed else null

            MQPlayerTheme(themeMode = settingsState.themeMode, seedColor = seedColor) {
                if (showUpdateDialog && hasUpdate && updateUrl != null) {
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = { Text(stringResourceSafe(R.string.update_available_title)) },
                        text = { Text(stringResourceSafe(R.string.update_available_body, latestRelease!!.first)) },
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

                BackHandler(enabled = currentScreen == AppScreen.SETTINGS) {
                    currentScreen = AppScreen.PLAYER
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                            title = {
                                Text(
                                    text = if (currentScreen == AppScreen.PLAYER)
                                        stringResourceSafe(R.string.app_name)
                                    else
                                        stringResourceSafe(R.string.settings_title),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            navigationIcon = {
                                if (currentScreen == AppScreen.SETTINGS) {
                                    IconButton(onClick = { currentScreen = AppScreen.PLAYER }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_arrow_back),
                                            contentDescription = stringResourceSafe(R.string.back_button),
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen == AppScreen.PLAYER) {
                                    IconButton(onClick = { currentScreen = AppScreen.SETTINGS }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_settings),
                                            contentDescription = stringResourceSafe(R.string.open_settings_button),
                                        )
                                    }
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        transitionSpec = {
                            if (targetState == AppScreen.SETTINGS) {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                        (slideOutHorizontally { -it / 4 } + fadeOut()) using
                                        SizeTransform(clip = false)
                            } else {
                                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                        (slideOutHorizontally { it } + fadeOut()) using
                                        SizeTransform(clip = false)
                            }
                        },
                        label = "main_settings_transition",
                    ) { screen ->
                        when (screen) {
                            AppScreen.PLAYER -> MediaControllerScreen(
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
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                settingsState = settingsState,
                                hasUpdate = hasUpdate,
                                updateUrl = updateUrl,
                                isCheckingUpdate = isCheckingUpdate,
                                onCheckUpdate = {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        latestRelease = fetchLatestRelease()
                                        isCheckingUpdate = false
                                    }
                                },
                                onThemeModeSelected = AppSettingsRepository::setThemeMode,
                                onOpenRepository = { openUrl("https://github.com/MohmmadQunibi/MQ-player-AA") },
                                onOpenProfile = { openUrl("https://github.com/MohmmadQunibi") },
                                onDownloadUpdate = { openUrl(it) },
                            )
                        }
                    }
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
