package com.mqunibi.mqplayer.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet

private const val FORWARD_SKIP_MS = 30_000L
private const val BACK_SKIP_MS = 10_000L
private const val SESSION_CLEAR_DEBOUNCE_MS = 800L
private const val LOOP_CHECK_TOLERANCE_MS = 250L
const val SESSION_MEDIA_ID_PREFIX = "session:"

data class SessionInfo(
    val mediaId: String,
    val appName: String,
    val packageName: String,
    val title: String?,
    val subtitle: String?,
    val playbackLabel: String,
    val isPlaying: Boolean,
)

data class ActiveMediaState(
    val permissionGranted: Boolean = false,
    val currentAppName: String? = null,
    val currentPackageName: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val playbackLabel: String = "No active session",
    val isPlaying: Boolean = false,
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSeekBackward: Boolean = false,
    val canSeekForward: Boolean = false,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val albumArt: android.graphics.Bitmap? = null,
    val albumArtUri: String? = null,
    val activeSessionCount: Int = 0,
    val availableSessions: List<String> = emptyList(),
    val sessionInfos: List<SessionInfo> = emptyList(),
    val errorMessage: String? = null,
    val loopEnabled: Boolean = false,
)

object ActiveMediaRepository {
    fun interface Observer {
        fun onStateChanged(state: ActiveMediaState)
    }

    private val observers = CopyOnWriteArraySet<Observer>()
    private val mutableState = MutableStateFlow(ActiveMediaState())
    val state: StateFlow<ActiveMediaState> = mutableState.asStateFlow()

    @Volatile
    private var initialized = false
    private lateinit var appContext: Context
    private lateinit var sessionManager: MediaSessionManager
    private lateinit var listenerComponent: ComponentName
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeControllers: List<MediaController> = emptyList()
    private var currentController: MediaController? = null
    // Compat view of the same session: the framework controller can't read or set repeat mode.
    private var currentCompatController: MediaControllerCompat? = null
    private var activeSessionsListenerRegistered = false
    private var userSelectedPackageName: String? = null
    private var loopEnabled = false
    private var repeatModeBeforeLoop = PlaybackStateCompat.REPEAT_MODE_NONE
    private var previousPlaybackState = PlaybackState.STATE_NONE
    private var pendingSessionClearJob: Runnable? = null

    // Fallback for apps that ignore the repeat-mode request: seek back to the start just before
    // the track ends, so autoplay/queue advance (YouTube, YouTube Music) never kicks in.
    private val loopCheckRunnable: Runnable = Runnable {
        if (!loopEnabled || isNativeRepeatOneActive()) {
            return@Runnable
        }
        val controller = currentController ?: return@Runnable
        val waitMs = msUntilLoopRestart(controller) ?: return@Runnable
        if (waitMs > LOOP_CHECK_TOLERANCE_MS) {
            mainHandler.postDelayed(loopCheckRunnable, waitMs)
        } else {
            controller.transportControls.seekTo(0)
        }
    }

    private val activeSessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleControllersChanged(controllers.orEmpty(), permissionGranted = isNotificationListenerEnabled())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val newState = state?.state ?: PlaybackState.STATE_NONE
            if (loopEnabled && previousPlaybackState == PlaybackState.STATE_PLAYING) {
                val trackEnded = when (newState) {
                    PlaybackState.STATE_STOPPED -> true
                    PlaybackState.STATE_PAUSED -> {
                        val position = state?.position ?: 0L
                        val duration = currentController?.metadata
                            ?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                        duration > 0 && position >= duration - 500L
                    }
                    else -> false
                }
                if (trackEnded) {
                    mainHandler.postDelayed({
                        currentController?.transportControls?.seekTo(0)
                        play()
                    }, 300)
                }
            }
            previousPlaybackState = newState
            scheduleLoopCheck()
            publishCurrentState(permissionGranted = isNotificationListenerEnabled())
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            scheduleLoopCheck()
            publishCurrentState(permissionGranted = isNotificationListenerEnabled())
        }

        override fun onSessionDestroyed() {
            refresh()
        }
    }

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) {
            return
        }

        appContext = context.applicationContext
        sessionManager = appContext.getSystemService(MediaSessionManager::class.java)
        listenerComponent = ComponentName(appContext, ActiveSessionNotificationListener::class.java)
        initialized = true
        refresh()
    }

    fun addObserver(observer: Observer) {
        observers += observer
        observer.onStateChanged(state.value)
    }

    fun removeObserver(observer: Observer) {
        observers -= observer
    }

    fun refresh() {
        if (!initialized) {
            return
        }

        val permissionGranted = isNotificationListenerEnabled()
        if (!permissionGranted) {
            unregisterActiveSessionsListener()
            updateCurrentController(null)
            activeControllers = emptyList()
            publishState(
                ActiveMediaState(
                    permissionGranted = false,
                    playbackLabel = "Notification access required",
                    errorMessage = null,
                ),
            )
            return
        }

        registerActiveSessionsListener()
        try {
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            handleControllersChanged(controllers, permissionGranted = true)
        } catch (securityException: SecurityException) {
            unregisterActiveSessionsListener()
            updateCurrentController(null)
            activeControllers = emptyList()
            publishState(
                ActiveMediaState(
                    permissionGranted = false,
                    playbackLabel = "Notification access required",
                    errorMessage = securityException.localizedMessage,
                ),
            )
        }
    }

    fun onNotificationListenerAvailabilityChanged() {
        refresh()
    }

    fun play() {
        currentController?.transportControls?.play()
    }

    fun pause() {
        currentController?.transportControls?.pause()
    }

    fun togglePlayPause() {
        if (state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun skipToPrevious() {
        currentController?.transportControls?.skipToPrevious()
    }

    fun skipToNext() {
        currentController?.transportControls?.skipToNext()
    }

    fun seekBackward10Seconds() {
        seekBy(deltaMillis = -BACK_SKIP_MS)
    }

    fun seekForward30Seconds() {
        seekBy(deltaMillis = FORWARD_SKIP_MS)
    }

    fun seekTo(positionMs: Long) {
        currentController?.transportControls?.seekTo(positionMs)
    }

    fun toggleLoop() {
        loopEnabled = !loopEnabled
        applyNativeLoop(enabled = loopEnabled)
        scheduleLoopCheck()
        publishCurrentState(permissionGranted = isNotificationListenerEnabled())
    }

    fun switchToSession(mediaId: String) {
        val packageName = mediaId.removePrefix(SESSION_MEDIA_ID_PREFIX)
        val controller = activeControllers.find { it.packageName == packageName } ?: return
        userSelectedPackageName = packageName
        updateCurrentController(controller)
        publishCurrentState(permissionGranted = isNotificationListenerEnabled())
    }

    private fun handleControllersChanged(
        controllers: List<MediaController>,
        permissionGranted: Boolean,
    ) {
        val externalControllers = filterOutOwnSessions(
            items = controllers,
            ownPackageName = appContext.packageName,
            packageNameOf = { controller -> controller.packageName },
        )

        activeControllers = externalControllers

        // Honor an explicit user selection as long as that session is still alive.
        // Fall back to auto-selection only if the chosen session has disappeared.
        val selectedController = userSelectedPackageName
            ?.let { pkg -> externalControllers.find { it.packageName == pkg } }
            ?: run {
                userSelectedPackageName = null
                // Stick with the current controller while it's still present and actively playing.
                // This prevents transient sessions (system sounds, assistant, notifications) from
                // temporarily outscoring the real player and causing a flicker mid-stream.
                val current = currentController
                val stickyController = if (current != null) {
                    val stillPresent = externalControllers.any { it.sessionToken == current.sessionToken }
                    val activelyPlaying = when (current.playbackState?.state) {
                        PlaybackState.STATE_PLAYING,
                        PlaybackState.STATE_BUFFERING,
                        PlaybackState.STATE_CONNECTING -> true
                        else -> false
                    }
                    if (stillPresent && activelyPlaying) current else null
                } else null
                stickyController ?: selectPreferredController(externalControllers)
            }

        if (selectedController == null && currentController != null) {
            // Session temporarily disappeared (e.g. YouTube track transition).
            // Debounce the clear — if the session reappears within the window, skip the blank state.
            if (pendingSessionClearJob == null) {
                val runnable = Runnable {
                    pendingSessionClearJob = null
                    updateCurrentController(null)
                    publishCurrentState(permissionGranted = permissionGranted)
                }
                pendingSessionClearJob = runnable
                mainHandler.postDelayed(runnable, SESSION_CLEAR_DEBOUNCE_MS)
            }
            return
        }

        // Valid session found — cancel any pending clear and switch immediately.
        pendingSessionClearJob?.let { mainHandler.removeCallbacks(it) }
        pendingSessionClearJob = null

        updateCurrentController(selectedController)
        publishCurrentState(permissionGranted = permissionGranted)
    }

    private fun publishCurrentState(permissionGranted: Boolean) {
        val controller = currentController
        val playbackState = controller?.playbackState
        val metadata = controller?.metadata
        val title = metadata?.firstNonBlank(
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
            MediaMetadata.METADATA_KEY_TITLE,
        )
        val subtitle = metadata?.firstNonBlank(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM,
        ) ?: metadata?.description?.subtitle?.toString()
        val actions = playbackState?.actions ?: 0L
        val appLabel = controller?.packageName?.let(::resolveAppLabel)
        val currentPlaybackState = playbackState?.state ?: PlaybackState.STATE_NONE
        val rawPosition = playbackState?.position ?: -1L
        val positionMs = if (rawPosition >= 0) rawPosition else null
        val rawDuration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val durationMs = if (rawDuration > 0) rawDuration else null
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val albumArtUri = metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            ?: controller?.metadata?.description?.iconUri?.toString()

        publishState(
            ActiveMediaState(
                permissionGranted = permissionGranted,
                currentAppName = appLabel,
                currentPackageName = controller?.packageName,
                title = title ?: metadata?.description?.title?.toString(),
                subtitle = subtitle,
                playbackLabel = playbackLabelFor(currentPlaybackState),
                isPlaying = currentPlaybackState == PlaybackState.STATE_PLAYING ||
                    currentPlaybackState == PlaybackState.STATE_BUFFERING ||
                    currentPlaybackState == PlaybackState.STATE_CONNECTING,
                canPlay = actions.hasAny(
                    PlaybackState.ACTION_PLAY,
                    PlaybackState.ACTION_PLAY_PAUSE,
                    PlaybackState.ACTION_PREPARE,
                ),
                canPause = actions.hasAny(
                    PlaybackState.ACTION_PAUSE,
                    PlaybackState.ACTION_PLAY_PAUSE,
                ),
                canSkipPrevious = actions.hasAny(PlaybackState.ACTION_SKIP_TO_PREVIOUS),
                canSkipNext = actions.hasAny(PlaybackState.ACTION_SKIP_TO_NEXT),
                canSeekBackward = actions.hasAny(
                    PlaybackState.ACTION_SEEK_TO,
                    PlaybackState.ACTION_REWIND,
                ),
                canSeekForward = actions.hasAny(
                    PlaybackState.ACTION_SEEK_TO,
                    PlaybackState.ACTION_FAST_FORWARD,
                ),
                positionMs = positionMs,
                durationMs = durationMs,
                albumArt = albumArt,
                albumArtUri = albumArtUri,
                activeSessionCount = activeControllers.size,
                availableSessions = activeControllers.map(::formatSessionSummary),
                sessionInfos = activeControllers.map(::buildSessionInfo),
                errorMessage = null,
                loopEnabled = loopEnabled,
            ),
        )
    }

    private fun publishState(newState: ActiveMediaState) {
        mutableState.value = newState
        observers.forEach { observer ->
            observer.onStateChanged(newState)
        }
    }

    private fun selectPreferredController(controllers: List<MediaController>): MediaController? {
        val preferredIndex = SessionSelection.pickIndex(
            controllers.map { controller ->
                val playbackState = controller.playbackState
                SessionCandidate(
                    packageName = controller.packageName,
                    playbackStatus = playbackState?.state.toPlaybackStatus(),
                    actions = playbackState?.actions?.countOneBits()?.toLong() ?: 0L,
                    hasMetadata = controller.metadata != null,
                )
            },
        ) ?: return null

        return controllers.getOrNull(preferredIndex)
    }

    private fun updateCurrentController(newController: MediaController?) {
        val sameController = currentController?.sessionToken == newController?.sessionToken
        if (sameController) {
            return
        }

        // Hand the loop over: give the old app its repeat mode back, then ask the new one to repeat.
        if (loopEnabled) applyNativeLoop(enabled = false)
        currentController?.unregisterCallback(controllerCallback)
        currentController = newController
        currentCompatController = newController?.let { controller ->
            MediaControllerCompat(appContext, MediaSessionCompat.Token.fromToken(controller.sessionToken))
        }
        currentController?.registerCallback(controllerCallback, mainHandler)
        if (loopEnabled) applyNativeLoop(enabled = true)
        previousPlaybackState = PlaybackState.STATE_NONE
        scheduleLoopCheck()
    }

    // Repeat mode is a compat extension (custom action under the hood). Apps that support it
    // (e.g. Media3/MediaSessionCompat players) loop seamlessly; the rest ignore it harmlessly.
    private fun applyNativeLoop(enabled: Boolean) {
        val compatController = currentCompatController ?: return
        if (enabled) {
            repeatModeBeforeLoop = compatController.repeatMode.takeIf { mode ->
                mode != PlaybackStateCompat.REPEAT_MODE_INVALID && mode != PlaybackStateCompat.REPEAT_MODE_ONE
            } ?: PlaybackStateCompat.REPEAT_MODE_NONE
            compatController.transportControls.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ONE)
        } else {
            compatController.transportControls.setRepeatMode(repeatModeBeforeLoop)
        }
    }

    private fun isNativeRepeatOneActive(): Boolean =
        currentCompatController?.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE

    private fun scheduleLoopCheck() {
        mainHandler.removeCallbacks(loopCheckRunnable)
        if (!loopEnabled) {
            return
        }
        val waitMs = currentController?.let(::msUntilLoopRestart) ?: return
        mainHandler.postDelayed(loopCheckRunnable, waitMs)
    }

    private fun msUntilLoopRestart(controller: MediaController): Long? {
        val playbackState = controller.playbackState ?: return null
        if (playbackState.state != PlaybackState.STATE_PLAYING) {
            return null
        }
        val durationMs = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: return null
        val positionMs = livePositionMs(playbackState) ?: return null
        return PlaybackTiming.msUntilLoopRestart(positionMs, durationMs, playbackState.playbackSpeed)
    }

    private fun livePositionMs(playbackState: PlaybackState): Long? =
        PlaybackTiming.estimatePositionMs(
            reportedPositionMs = playbackState.position,
            lastUpdateTimeMs = playbackState.lastPositionUpdateTime,
            playbackSpeed = playbackState.playbackSpeed,
            nowMs = SystemClock.elapsedRealtime(),
            isPlaying = playbackState.state == PlaybackState.STATE_PLAYING,
        )

    private fun registerActiveSessionsListener() {
        if (activeSessionsListenerRegistered) {
            return
        }

        sessionManager.addOnActiveSessionsChangedListener(
            activeSessionsChangedListener,
            listenerComponent,
            mainHandler,
        )
        activeSessionsListenerRegistered = true
    }

    private fun unregisterActiveSessionsListener() {
        if (!activeSessionsListenerRegistered) {
            return
        }

        sessionManager.removeOnActiveSessionsChangedListener(activeSessionsChangedListener)
        activeSessionsListenerRegistered = false
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()

        return enabledListeners
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { componentName ->
                componentName.packageName == appContext.packageName
            }
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val packageInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(packageInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun formatSessionSummary(controller: MediaController): String {
        val appName = resolveAppLabel(controller.packageName)
        val playbackLabel = playbackLabelFor(controller.playbackState?.state ?: PlaybackState.STATE_NONE)
        return "$appName • $playbackLabel"
    }

    private fun buildSessionInfo(controller: MediaController): SessionInfo {
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val appName = resolveAppLabel(controller.packageName)
        val title = metadata?.firstNonBlank(
            MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
            MediaMetadata.METADATA_KEY_TITLE,
        ) ?: metadata?.description?.title?.toString()
        val subtitle = metadata?.firstNonBlank(
            MediaMetadata.METADATA_KEY_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
            MediaMetadata.METADATA_KEY_ALBUM,
        ) ?: metadata?.description?.subtitle?.toString()
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        return SessionInfo(
            mediaId = "$SESSION_MEDIA_ID_PREFIX${controller.packageName}",
            appName = appName,
            packageName = controller.packageName,
            title = title,
            subtitle = subtitle,
            playbackLabel = playbackLabelFor(state),
            isPlaying = state == PlaybackState.STATE_PLAYING ||
                state == PlaybackState.STATE_BUFFERING ||
                state == PlaybackState.STATE_CONNECTING,
        )
    }

    private fun seekBy(deltaMillis: Long) {
        val controller = currentController ?: return
        val playbackState = controller.playbackState
        val actions = playbackState?.actions ?: 0L
        val transportControls = controller.transportControls

        if (actions.hasAny(PlaybackState.ACTION_SEEK_TO)) {
            val currentPosition = playbackState?.let(::livePositionMs)
            if (currentPosition != null) {
                val newPosition = (currentPosition + deltaMillis).coerceAtLeast(0L)
                transportControls.seekTo(newPosition)
                return
            }
        }

        if (deltaMillis > 0 && actions.hasAny(PlaybackState.ACTION_FAST_FORWARD)) {
            transportControls.fastForward()
            return
        }

        if (deltaMillis < 0 && actions.hasAny(PlaybackState.ACTION_REWIND)) {
            transportControls.rewind()
        }
    }
}

private fun MediaMetadata.firstNonBlank(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        getString(key)?.takeIf(String::isNotBlank)
    }
}

private fun Int?.toPlaybackStatus(): SessionPlaybackStatus {
    return when (this) {
        PlaybackState.STATE_PLAYING -> SessionPlaybackStatus.PLAYING
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> SessionPlaybackStatus.TRANSITIONING
        PlaybackState.STATE_PAUSED -> SessionPlaybackStatus.PAUSED
        PlaybackState.STATE_STOPPED -> SessionPlaybackStatus.STOPPED
        else -> SessionPlaybackStatus.NONE
    }
}

private fun playbackLabelFor(state: Int): String {
    return when (state) {
        PlaybackState.STATE_PLAYING -> "Playing"
        PlaybackState.STATE_BUFFERING -> "Buffering"
        PlaybackState.STATE_CONNECTING -> "Connecting"
        PlaybackState.STATE_PAUSED -> "Paused"
        PlaybackState.STATE_STOPPED -> "Stopped"
        PlaybackState.STATE_FAST_FORWARDING -> "Seeking forward"
        PlaybackState.STATE_REWINDING -> "Rewinding"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "Skipping next"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "Skipping previous"
        else -> "Idle"
    }
}

private fun Long.hasAny(vararg flags: Long): Boolean {
    return flags.any { flag -> this and flag != 0L }
}

internal fun <T> filterOutOwnSessions(
    items: List<T>,
    ownPackageName: String,
    packageNameOf: (T) -> String,
): List<T> {
    return items.filterNot { item -> packageNameOf(item) == ownPackageName }
}
