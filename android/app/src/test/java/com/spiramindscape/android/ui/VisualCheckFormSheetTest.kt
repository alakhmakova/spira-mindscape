package com.spiramindscape.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.ConfidenceStepper
import com.spiramindscape.android.ui.components.DeadlineField
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.SpiraFormSheetContent
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The create-form sheet (New goal), rendered as pixels.
 *
 * What this is here to catch is the **head**: it must be the app's Kale band with white type, the
 * same one `SpiraFilterSheetContent` draws, and not the white title row with Material's grey drag
 * handle that these sheets wore until 2026-08-22. A colour is invisible to every existence
 * assertion, and the sheet cannot be photographed through its `ModalBottomSheet` — hence
 * `SpiraFormSheetContent`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckFormSheetTest : VisualCheckTestBase() {

    @Test
    fun `create form sheet wears the Kale head`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                SpiraFormSheetContent(
                    title = "New goal",
                    onDismiss = {},
                    confirmLabel = "Create goal",
                    onConfirm = {},
                    confirmEnabled = true,
                ) {
                    SpiraTextField("Run a half marathon", {}, "Title")
                    SpiraTextField(
                        "",
                        {},
                        "Description",
                        singleLine = false,
                        minLines = 3,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("Confidence: 7/10")
                        ConfidenceStepper(7, {})
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FieldLabel("Deadline")
                        DeadlineField(null, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("form-sheet-new-goal")
    }
}
