package com.mqunibi.mqplayer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

data class TestSession(val packageName: String)

class ActiveMediaRepositoryFilterTest {
    @Test
    fun `filters out mq player sessions and keeps external ones`() {
        val sessions = listOf(
            TestSession(packageName = "com.spotify.music"),
            TestSession(packageName = "com.mqunibi.mqplayer"),
            TestSession(packageName = "com.google.android.apps.youtube.music"),
        )

        val filteredSessions = filterOutOwnSessions(
            items = sessions,
            ownPackageName = "com.mqunibi.mqplayer",
            packageNameOf = TestSession::packageName,
        )

        assertEquals(
            listOf(
                TestSession(packageName = "com.spotify.music"),
                TestSession(packageName = "com.google.android.apps.youtube.music"),
            ),
            filteredSessions,
        )
    }

    @Test
    fun `returns empty when only mq player session is present`() {
        val filteredSessions = filterOutOwnSessions(
            items = listOf(TestSession(packageName = "com.mqunibi.mqplayer")),
            ownPackageName = "com.mqunibi.mqplayer",
            packageNameOf = TestSession::packageName,
        )

        assertTrue(filteredSessions.isEmpty())
    }
}

