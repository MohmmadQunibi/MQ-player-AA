package com.mqunibi.mqplayer.auto

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import com.mqunibi.mqplayer.MainActivity
import com.mqunibi.mqplayer.R
import com.mqunibi.mqplayer.media.ActiveMediaRepository
import com.mqunibi.mqplayer.media.ActiveMediaState
import com.mqunibi.mqplayer.media.SESSION_MEDIA_ID_PREFIX
import com.mqunibi.mqplayer.media.SessionInfo

private const val ROOT_ID = "mq_player_root"
private const val CURRENT_ITEM_ID = "current_active_session"
private const val ACTION_SEEK_BACK_10 = "com.mqunibi.mqplayer.ACTION_SEEK_BACK_10"
private const val ACTION_SEEK_FORWARD_30 = "com.mqunibi.mqplayer.ACTION_SEEK_FORWARD_30"
private const val ACTION_TOGGLE_LOOP = "com.mqunibi.mqplayer.ACTION_TOGGLE_LOOP"

class AutoMediaBrowserService : MediaBrowserServiceCompat(), ActiveMediaRepository.Observer {
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        ActiveMediaRepository.initialize(applicationContext)

        mediaSession = MediaSessionCompat(this, getString(R.string.auto_service_label)).apply {
            setSessionActivity(createSessionActivity())
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        ActiveMediaRepository.play()
                    }

                    override fun onPause() {
                        ActiveMediaRepository.pause()
                    }

                    override fun onSkipToPrevious() {
                        ActiveMediaRepository.skipToPrevious()
                    }

                    override fun onSkipToNext() {
                        ActiveMediaRepository.skipToNext()
                    }

                    override fun onRewind() {
                        ActiveMediaRepository.seekBackward10Seconds()
                    }

                    override fun onFastForward() {
                        ActiveMediaRepository.seekForward30Seconds()
                    }

                    override fun onSeekTo(pos: Long) {
                        ActiveMediaRepository.seekTo(pos)
                    }

                    override fun onCustomAction(action: String?, extras: Bundle?) {
                        when (action) {
                            ACTION_SEEK_BACK_10 -> ActiveMediaRepository.seekBackward10Seconds()
                            ACTION_SEEK_FORWARD_30 -> ActiveMediaRepository.seekForward30Seconds()
                            ACTION_TOGGLE_LOOP -> ActiveMediaRepository.toggleLoop()
                        }
                    }

                    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                        when {
                            mediaId == CURRENT_ITEM_ID -> ActiveMediaRepository.play()
                            mediaId?.startsWith(SESSION_MEDIA_ID_PREFIX) == true -> {
                                ActiveMediaRepository.switchToSession(mediaId)
                                ActiveMediaRepository.play()
                            }
                        }
                    }

                    override fun onStop() {
                        ActiveMediaRepository.pause()
                    }
                },
            )
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
        ActiveMediaRepository.addObserver(this)
    }

    override fun onDestroy() {
        ActiveMediaRepository.removeObserver(this)
        mediaSession.release()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?,
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, Bundle())
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>,
    ) {
        val items = when (parentId) {
            ROOT_ID -> {
                val state = ActiveMediaRepository.state.value
                when {
                    !state.permissionGranted -> mutableListOf(buildCurrentItem(state))
                    state.sessionInfos.isEmpty() -> mutableListOf(buildCurrentItem(state))
                    else -> state.sessionInfos.map { buildSessionItem(it) }.toMutableList()
                }
            }

            else -> mutableListOf()
        }
        result.sendResult(items)
    }

    override fun onStateChanged(state: ActiveMediaState) {
        mediaSession.setMetadata(buildMetadata(state))
        mediaSession.setPlaybackState(buildPlaybackState(state))
        mediaSession.isActive = true
        notifyChildrenChanged(ROOT_ID)
    }

    private fun createSessionActivity(): PendingIntent {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            1001,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildSessionItem(session: SessionInfo): MediaBrowserCompat.MediaItem {
        val title = when {
            !session.title.isNullOrBlank() -> session.title
            else -> session.appName
        }
        val subtitle = buildString {
            append(session.appName)
            append(" • ")
            append(session.playbackLabel)
            if (!session.subtitle.isNullOrBlank()) {
                append(" — ")
                append(session.subtitle)
            }
        }

        val description = MediaDescriptionCompat.Builder()
            .setMediaId(session.mediaId)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()

        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )
    }

    private fun buildCurrentItem(state: ActiveMediaState): MediaBrowserCompat.MediaItem {
        val title = when {
            !state.permissionGranted -> getString(R.string.auto_permission_title)
            !state.title.isNullOrBlank() -> state.title
            !state.currentAppName.isNullOrBlank() -> state.currentAppName
            else -> getString(R.string.auto_root_title)
        }
        val subtitle = when {
            !state.permissionGranted -> getString(R.string.auto_permission_subtitle)
            !state.subtitle.isNullOrBlank() -> state.subtitle
            !state.currentAppName.isNullOrBlank() -> state.currentAppName
            else -> getString(R.string.auto_root_subtitle)
        }

        val description = MediaDescriptionCompat.Builder()
            .setMediaId(CURRENT_ITEM_ID)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(state.playbackLabel)
            .apply {
                when {
                    state.albumArt != null -> setIconBitmap(state.albumArt)
                    state.albumArtUri != null -> setIconUri(state.albumArtUri.toUri())
                }
            }
            .build()

        return MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
        )
    }

    private fun buildMetadata(state: ActiveMediaState): MediaMetadataCompat {
        val title = when {
            !state.permissionGranted -> getString(R.string.auto_permission_title)
            !state.title.isNullOrBlank() -> state.title
            !state.currentAppName.isNullOrBlank() -> state.currentAppName
            else -> getString(R.string.auto_root_title)
        }
        val subtitle = when {
            !state.permissionGranted -> getString(R.string.auto_permission_subtitle)
            !state.subtitle.isNullOrBlank() -> state.subtitle
            !state.currentAppName.isNullOrBlank() -> state.currentAppName
            else -> getString(R.string.auto_root_subtitle)
        }

        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, CURRENT_ITEM_ID)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
            .apply {
                state.durationMs?.let {
                    putLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION,
                        it
                    )
                }
            }
            .apply {
                when {
                    state.albumArt != null -> {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, state.albumArt)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, state.albumArt)
                    }

                    state.albumArtUri != null -> {
                        putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, state.albumArtUri)
                        putString(
                            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
                            state.albumArtUri
                        )
                    }
                }
            }
            .build()
    }

    private fun buildPlaybackState(state: ActiveMediaState): PlaybackStateCompat {
        val actions = if (!state.permissionGranted) 0L else
            (if (state.canPlay) PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE else 0L) or
                    (if (state.canPause) PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE else 0L) or
                    (if (state.canSkipPrevious) PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS else 0L) or
                    (if (state.canSkipNext) PlaybackStateCompat.ACTION_SKIP_TO_NEXT else 0L) or
                    (if (state.canSeekBackward) PlaybackStateCompat.ACTION_REWIND else 0L) or
                    (if (state.canSeekForward) PlaybackStateCompat.ACTION_FAST_FORWARD else 0L) or
                    (if (state.canSeekBackward || state.canSeekForward) PlaybackStateCompat.ACTION_SEEK_TO else 0L) or
                    (if (state.sessionInfos.isNotEmpty()) PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID else 0L)

        val compatState = when {
            !state.permissionGranted -> PlaybackStateCompat.STATE_NONE
            state.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            state.currentAppName != null -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_NONE
        }

        val position = state.positionMs ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN

        return PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(compatState, position, 1f)
            .apply {
                if (state.permissionGranted && state.canSeekBackward) {
                    addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            ACTION_SEEK_BACK_10,
                            getString(R.string.undo_10_seconds_button),
                            R.drawable.ic_replay_10,
                        ).build(),
                    )
                }
                if (state.permissionGranted && state.canSeekForward) {
                    addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            ACTION_SEEK_FORWARD_30,
                            getString(R.string.skip_30_seconds_button),
                            R.drawable.ic_forward_30,
                        ).build(),
                    )
                }
                if (state.permissionGranted) {
                    addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            ACTION_TOGGLE_LOOP,
                            getString(R.string.loop_button),
                            if (state.loopEnabled) R.drawable.ic_repeat_on else R.drawable.ic_repeat,
                        ).build(),
                    )
                }
            }
            .build()
    }
}



