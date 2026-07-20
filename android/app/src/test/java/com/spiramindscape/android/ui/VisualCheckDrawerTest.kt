package com.spiramindscape.android.ui

import com.spiramindscape.android.ui.goals.SpiraDrawer
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckDrawerTest : VisualCheckTestBase() {

    @Test
    fun `drawer renders fully`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent { SpiraTheme { SpiraDrawer(user, onLogout = {}) } }
        compose.waitForIdle()
        saveWindow("drawer")
    }
}
