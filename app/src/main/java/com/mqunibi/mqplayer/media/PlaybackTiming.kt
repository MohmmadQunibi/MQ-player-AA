package com.mqunibi.mqplayer.media

// Restart this far before the reported end so apps with autoplay/queues (YouTube, YouTube Music)
// never get the chance to advance to the next item.
internal const val LOOP_END_MARGIN_MS = 750L

internal object PlaybackTiming {
    // Apps only report position on state changes, so extrapolate it the same way the platform does.
    // Only while playing: many apps report speed 1.0 even when paused.
    fun estimatePositionMs(
        reportedPositionMs: Long,
        lastUpdateTimeMs: Long,
        playbackSpeed: Float,
        nowMs: Long,
        isPlaying: Boolean,
    ): Long? {
        if (reportedPositionMs < 0) {
            return null
        }
        if (!isPlaying) {
            return reportedPositionMs
        }

        val elapsedMs = (nowMs - lastUpdateTimeMs).coerceAtLeast(0L)
        return reportedPositionMs + (elapsedMs * playbackSpeed.orNormalSpeed()).toLong()
    }

    // Real-time delay until the loop should seek back to the start, or null if it can't be known.
    fun msUntilLoopRestart(positionMs: Long, durationMs: Long, playbackSpeed: Float): Long? {
        if (durationMs <= 0 || positionMs < 0) {
            return null
        }

        val remainingMs = durationMs - positionMs - LOOP_END_MARGIN_MS
        return (remainingMs / playbackSpeed.orNormalSpeed()).toLong().coerceAtLeast(0L)
    }

    private fun Float.orNormalSpeed(): Float = if (this > 0f) this else 1f
}
