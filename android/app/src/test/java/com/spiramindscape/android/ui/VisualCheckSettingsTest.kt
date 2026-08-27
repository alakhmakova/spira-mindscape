package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.spiramindscape.android.ui.settings.UserSettingsScreen
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The account page's two tabs (GRO-122).
 *
 * The **Fonts** tab is the one that has to be looked at rather than asserted: each row is set in
 * the face it offers, so a font file that failed to load shows up as a row silently rendered in
 * the system sans — and `onNodeWithText("Guidy").assertIsDisplayed()` passes either way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckSettingsTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the settings page shows the profile and the font specimens`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                UserSettingsScreen(user = user, onBack = {}, onLogout = {})
            }
        }
        compose.waitForIdle()
        saveWindow("settings-profile")

        // My profile: the address, said plainly, with nothing to edit. It appears twice — once
        // under the name in the account card, once as the Email row — so match all of them.
        compose.onAllNodesWithText(user.email)[0].assertIsDisplayed()
        compose.onNodeWithText("Sign out").assertIsDisplayed()

        compose.onNodeWithText("Fonts").performClick()
        compose.waitForIdle()
        saveWindow("settings-fonts")

        // The Cyrillic group comes first — those are the candidates that can actually be judged on
        // Russian text, so they are what the tab opens on.
        compose.onNodeWithText("With Cyrillic").assertIsDisplayed()
        compose.onNodeWithText("Tilda Sans").assertIsDisplayed()

        // GCentra now sits under "Latin only", well below the fold, so it has to be scrolled to.
        compose.onNodeWithText("GCentra").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        saveWindow("settings-fonts-latin")
        compose.onNodeWithText("Latin only").assertExists()
    }

    /**
     * **About Spira** — the page the coach points at when someone asks how it works, and the
     * only place the coaching contract is spelled out. Worth a picture rather than assertions:
     * it is the longest prose in the app, and the failure mode is not a missing string but text
     * that runs off the side or collapses into an unreadable wall.
     */
    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `about spira explains coaching, GROW, the session and the app`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                UserSettingsScreen(user = user, onBack = {}, onLogout = {}, startOnAbout = true)
            }
        }
        compose.waitForIdle()
        saveWindow("settings-about")

        // The drawer links straight here, so the page must open already on this tab.
        compose.onNodeWithText("What coaching is — and what it isn't").assertIsDisplayed()

        compose.onNodeWithText("What GROW is").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        saveWindow("settings-about-grow")

        compose.onNodeWithText("How to use Spira").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        saveWindow("settings-about-using")

        // The books are offered as further reading, never as the coach's sources.
        compose.onNodeWithText("Further reading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Coaching for Performance").assertExists()
        compose.waitForIdle()
        saveWindow("settings-about-reading")
    }
}
