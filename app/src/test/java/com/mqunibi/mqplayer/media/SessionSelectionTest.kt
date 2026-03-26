package com.mqunibi.mqplayer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSelectionTest {
    @Test
    fun `returns null when there are no sessions`() {
        assertNull(SessionSelection.pickIndex(emptyList()))
    }

    @Test
    fun `prefers actively playing session over paused one`() {
        val candidates = listOf(
            SessionCandidate(
                packageName = "paused.app",
                playbackStatus = SessionPlaybackStatus.PAUSED,
                actions = 6,
                hasMetadata = true,
            ),
            SessionCandidate(
                packageName = "playing.app",
                playbackStatus = SessionPlaybackStatus.PLAYING,
                actions = 2,
                hasMetadata = false,
            ),
        )

        assertEquals(1, SessionSelection.pickIndex(candidates))
    }

    @Test
    fun `prefers richer metadata and actions when playback state is tied`() {
        val candidates = listOf(
            SessionCandidate(
                packageName = "basic.app",
                playbackStatus = SessionPlaybackStatus.PAUSED,
                actions = 1,
                hasMetadata = false,
            ),
            SessionCandidate(
                packageName = "rich.app",
                playbackStatus = SessionPlaybackStatus.PAUSED,
                actions = 8,
                hasMetadata = true,
            ),
        )

        assertEquals(1, SessionSelection.pickIndex(candidates))
    }

    @Test
    fun `prefers earlier item when all other scores match`() {
        val candidates = listOf(
            SessionCandidate(
                packageName = "first.app",
                playbackStatus = SessionPlaybackStatus.NONE,
                actions = 0,
                hasMetadata = false,
            ),
            SessionCandidate(
                packageName = "second.app",
                playbackStatus = SessionPlaybackStatus.NONE,
                actions = 0,
                hasMetadata = false,
            ),
        )

        assertEquals(0, SessionSelection.pickIndex(candidates))
    }
}

