package com.spiramindscape.android.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI test for the login screen, run on the JVM via Robolectric (no emulator).
 */
@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the sign-in button and fires the callback when tapped`() {
        var clicked = false
        compose.setContent {
            LoginScreen(signingIn = false, error = null, onSignIn = { clicked = true })
        }

        compose.onNodeWithText("Continue with Google").assertIsDisplayed().performClick()

        assertTrue(clicked)
    }

    @Test
    fun `shows the error message when one is provided`() {
        compose.setContent {
            LoginScreen(signingIn = false, error = "Sign-in failed", onSignIn = {})
        }

        compose.onNodeWithText("Sign-in failed").assertIsDisplayed()
    }

    @Test
    fun `hides the button label while signing in (spinner instead)`() {
        compose.setContent {
            LoginScreen(signingIn = true, error = null, onSignIn = {})
        }

        compose.onNodeWithText("Continue with Google").assertDoesNotExist()
    }
}
