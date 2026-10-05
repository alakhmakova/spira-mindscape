package com.spiramindscape.android.ui.ai

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The closing card is the one place a session's record can be kept, so what it says about an
 * EMPTY record matters. The coach writes none when the provider fails mid-close, and none at all
 * when End was pressed and the session ended locally with no AI involved — and the card used to
 * offer "Save memory" over an empty box, which wrote a blank memory over what the previous
 * session had left (owner, 2026-09-08).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class GrowEndCardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `an empty record is said plainly and cannot be saved`() {
        var saved = 0
        compose.setContent {
            SpiraTheme {
                GrowEndCard(
                    record = "",
                    revising = false,
                    onRevise = {},
                    onSave = { saved++ },
                    onDiscard = {},
                )
            }
        }

        compose.onNodeWithText("No record was written.").assertIsDisplayed()
        compose.onNodeWithText("The coach wrote no record", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Save memory").assertIsNotEnabled()
        compose.onNodeWithText("Save memory").performClick()
        assertEquals(0, saved)
        // The quiet button says what it does here: there is nothing to discard.
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun `a real record is shown and can be saved`() {
        var saved = 0
        compose.setContent {
            SpiraTheme {
                GrowEndCard(
                    record = "Chose the freelance route.",
                    revising = false,
                    onRevise = {},
                    onSave = { saved++ },
                    onDiscard = {},
                )
            }
        }

        compose.onNodeWithText("Chose the freelance route.").assertIsDisplayed()
        compose.onNodeWithText("Save memory").assertIsEnabled()
        compose.onNodeWithText("Save memory").performClick()
        assertEquals(1, saved)
        compose.onNodeWithText("Discard").assertIsDisplayed()
    }
}
