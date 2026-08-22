package com.spiramindscape.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **A search field empties itself with the word "Clear", never with a cross** (owner, 2026-08-22).
 *
 * Both bars this field lives in — the All-goals header in search mode and the goal workspace's
 * switcher — carry a white disc with an X of their own, a few dp to the right. A cross inside the
 * field made two identical marks side by side, and neither said which one dropped the query and
 * which one dismissed the search. The web draws the same word (`ClearSearchWord`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SearchFieldClearsWithAWordTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the word Clear appears once something is typed and empties the field`() {
        compose.setContent {
            SpiraTheme {
                var query by remember { mutableStateOf("") }
                SpiraSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search goals",
                )
            }
        }

        // An empty field has nothing to clear, so it shows no control at all.
        compose.onNodeWithText("Clear").assertDoesNotExist()

        compose.onNodeWithText("Search goals").performTextInput("Lisbon")
        compose.onNodeWithText("Clear").assertIsDisplayed()

        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Search goals").assertIsDisplayed()   // the placeholder is back
        compose.onNodeWithText("Clear").assertDoesNotExist()
    }
}
