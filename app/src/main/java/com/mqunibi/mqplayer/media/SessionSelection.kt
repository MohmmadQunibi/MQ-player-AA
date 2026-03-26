package com.mqunibi.mqplayer.media

internal enum class SessionPlaybackStatus {
    PLAYING,
    TRANSITIONING,
    PAUSED,
    STOPPED,
    NONE,
}

internal data class SessionCandidate(
    val packageName: String,
    val playbackStatus: SessionPlaybackStatus,
    val actions: Long,
    val hasMetadata: Boolean,
)

internal object SessionSelection {
    fun pickIndex(candidates: List<SessionCandidate>): Int? {
        if (candidates.isEmpty()) {
            return null
        }

        return candidates.indices.maxByOrNull { index ->
            score(candidate = candidates[index], index = index)
        }
    }

    private fun score(candidate: SessionCandidate, index: Int): Int {
        val playbackScore = when (candidate.playbackStatus) {
            SessionPlaybackStatus.PLAYING -> 5_000
            SessionPlaybackStatus.TRANSITIONING -> 4_000
            SessionPlaybackStatus.PAUSED -> 3_000
            SessionPlaybackStatus.STOPPED -> 1_500
            SessionPlaybackStatus.NONE -> 500
        }
        val metadataScore = if (candidate.hasMetadata) 150 else 0
        val actionScore = candidate.actions.coerceAtMost(15L).toInt() * 10
        val orderScore = 100 - index.coerceAtMost(99)
        return playbackScore + metadataScore + actionScore + orderScore
    }
}

