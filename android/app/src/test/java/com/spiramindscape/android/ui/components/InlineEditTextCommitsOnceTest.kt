package com.spiramindscape.android.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A field that commits on Done and is then taken off the screen commits **once** (GRO-144).
 *
 * This is the "Add task" row on a target card: pressing Done writes the task and closes the row in
 * the same frame. `InlineEditText` guards against writing twice by remembering what it last sent —
 * but the dispose hook used to read that guard through `rememberUpdatedState`, which stops
 * refreshing the moment the parent drops the field. So the hook compared the new text against the
 * value from *before* the commit, decided the edit was unsent, and sent it again. Two
 * `setChecklistItems` calls then raced, each replacing a list it had read before the other inserted
 * into it, and the typed task appeared twice on the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class InlineEditTextCommitsOnceTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Done then close commits the text exactly once`() {
        val committed = mutableListOf<String>()

        compose.setContent {
            SpiraTheme {
                // The AddTaskControl shape: the row exists only while `open`, and losing focus —
                // which Done does — closes it. `everFocused` is the same guard the real control
                // carries, because a field reports `focused = false` once before it has ever been
                // focused, which would otherwise close the row on its first frame.
                var open by remember { mutableStateOf(true) }
                var everFocused by remember { mutableStateOf(false) }
                if (open) {
                    InlineEditText(
                        value = "",
                        onCommit = { committed += it },
                        placeholder = "Add task…",
                        autoFocus = true,
                        onFocusChanged = { focused ->
                            if (focused) everFocused = true else if (everFocused) open = false
                        },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Add task…").performTextInput("Buy milk")
        compose.waitForIdle()
        compose.onNodeWithText("Buy milk").performImeAction()
        compose.waitForIdle()

        assertEquals(listOf("Buy milk"), committed)
    }

    /** The other half of the guard: a row torn down mid-edit must not LOSE what was typed. */
    @Test
    fun `closing without Done still commits the pending text once`() {
        val committed = mutableListOf<String>()
        val open = mutableStateOf(true)

        compose.setContent {
            SpiraTheme {
                if (open.value) {
                    InlineEditText(
                        value = "",
                        onCommit = { committed += it },
                        placeholder = "Add task…",
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Add task…").performTextInput("Buy milk")
        compose.waitForIdle()

        open.value = false
        compose.waitForIdle()

        assertEquals(listOf("Buy milk"), committed)
    }
}
