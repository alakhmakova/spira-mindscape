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

    /**
     * The same drawer opened from inside a goal: the goal's own name under Home, over the places
     * within it, with the one the user is on marked. Home must NOT look current here — Home is the
     * All-goals page, and saying otherwise was the bug this covers.
     */
    @Test
    fun `drawer inside a goal marks where the user is`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                SpiraDrawer(
                    user,
                    onLogout = {},
                    goalTitle = "Change career into product design",
                    currentPlace = 1, // Reality
                )
            }
        }
        compose.waitForIdle()
        saveWindow("drawer-in-goal")
    }
}
