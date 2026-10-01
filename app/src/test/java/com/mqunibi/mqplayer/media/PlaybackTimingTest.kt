package com.mqunibi.mqplayer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTimingTest {
    @Test
    fun `estimated position advances by elapsed time since last update`() {
        val position = PlaybackTiming.estimatePositionMs(
            reportedPositionMs = 10_000,
            lastUpdateTimeMs = 1_000,
            playbackSpeed = 1f,
            nowMs = 3_000,
            isPlaying = true,
        )

        assertEquals(12_000L, position)
    }

    @Test
    fun `estimated position scales with playback speed`() {
        val position = PlaybackTiming.estimatePositionMs(
            reportedPositionMs = 10_000,
            lastUpdateTimeMs = 1_000,
            playbackSpeed = 2f,
            nowMs = 3_000,
            isPlaying = true,
        )

        assertEquals(14_000L, position)
    }

    @Test
    fun `estimated position treats non-positive speed as normal speed`() {
        val position = PlaybackTiming.estimatePositionMs(
            reportedPositionMs = 10_000,
            lastUpdateTimeMs = 1_000,
            playbackSpeed = 0f,
            nowMs = 3_000,
            isPlaying = true,
        )

        assertEquals(12_000L, position)
    }

    @Test
    fun `estimated position is null when reported position is unknown`() {
        assertNull(
            PlaybackTiming.estimatePositionMs(
                reportedPositionMs = -1,
                lastUpdateTimeMs = 1_000,
                playbackSpeed = 1f,
                nowMs = 3_000,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun `estimated position does not advance while paused`() {
        val position = PlaybackTiming.estimatePositionMs(
            reportedPositionMs = 10_000,
            lastUpdateTimeMs = 1_000,
            playbackSpeed = 1f,
            nowMs = 3_000,
            isPlaying = false,
        )

        assertEquals(10_000L, position)
    }

    @Test
    fun `restart is scheduled just before the track ends`() {
        val delay = PlaybackTiming.msUntilLoopRestart(
            positionMs = 190_000,
            durationMs = 200_000,
            playbackSpeed = 1f,
        )

        assertEquals(10_000L - LOOP_END_MARGIN_MS, delay)
    }

    @Test
    fun `restart delay is shortened by faster playback`() {
        val delay = PlaybackTiming.msUntilLoopRestart(
            positionMs = 190_000,
            durationMs = 200_000,
            playbackSpeed = 2f,
        )

        assertEquals((10_000L - LOOP_END_MARGIN_MS) / 2, delay)
    }

    @Test
    fun `restart is immediate when already inside the end margin`() {
        val delay = PlaybackTiming.msUntilLoopRestart(
            positionMs = 199_900,
            durationMs = 200_000,
            playbackSpeed = 1f,
        )

        assertEquals(0L, delay)
    }

    @Test
    fun `restart is null when duration is unknown`() {
        assertNull(
            PlaybackTiming.msUntilLoopRestart(
                positionMs = 10_000,
                durationMs = 0,
                playbackSpeed = 1f,
            ),
        )
    }
}
