package com.mqunibi.mqplayer.auto

import com.mqunibi.mqplayer.media.ActiveMediaState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionActivityTest {
    @Test
    fun `inactive when no media session is being mirrored`() {
        assertFalse(hasMirroredSession(ActiveMediaState(permissionGranted = true)))
    }

    @Test
    fun `inactive when notification access is missing`() {
        assertFalse(
            hasMirroredSession(
                ActiveMediaState(
                    permissionGranted = false,
                    currentPackageName = "com.google.android.apps.youtube.music",
                ),
            ),
        )
    }

    @Test
    fun `active while mirroring another app's session`() {
        assertTrue(
            hasMirroredSession(
                ActiveMediaState(
                    permissionGranted = true,
                    currentPackageName = "com.google.android.apps.youtube.music",
                ),
            ),
        )
    }
}
