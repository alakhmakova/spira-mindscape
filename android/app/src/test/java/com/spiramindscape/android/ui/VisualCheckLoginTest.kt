package com.spiramindscape.android.ui

import com.spiramindscape.android.ui.auth.LoginScreen
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the sign-in screen to `app/build/reports/visual/login.png` so it can be held against the
 * web mobile login it mirrors (`src/routes/login.tsx` below 820px) — the wordmark's corner, the
 * serif heading, the outlined Google button and the legal line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckLoginTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `sign-in screen`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                LoginScreen(signingIn = false, error = null, onSignIn = {})
            }
        }
        compose.waitForIdle()
        saveWindow("login")
    }
}
