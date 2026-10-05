package com.spiramindscape.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.DeadlinePickerDialog
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **The picker as it is actually used — inside its `Dialog`.**
 *
 * `VisualCheckDatePickerTest` renders `SpiraMonthGrid` on its own, which proves the grid and
 * nothing about the window it lives in. That is a real gap: a Compose `Dialog` defaults to
 * `usePlatformDefaultWidth = true`, which hands its content a width the platform chose rather
 * than the one the card asks for, and a seven-column grid with a week-number column is exactly
 * the sort of thing that gets squeezed by it without failing to compile or to render.
 *
 * A `Dialog` also opens its own window, so the `VisualCheck*` screenshot helper (it draws the
 * activity's decor view) cannot photograph it — the same trap `VisualCheckFilterSheetTest`
 * documents for `ModalBottomSheet`. Semantics reach into it, so this asserts rather than looks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeadlinePickerDialogTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the dialog gives the grid room, and its year menu opens`() {
        compose.activityRule.scenario.onActivity { }
        var saved: String? = "unset"
        compose.setContent {
            SpiraTheme {
                DeadlinePickerDialog(
                    value = "2026-08-15T00:00:00Z",
                    onChange = { saved = it },
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()

        // The head states the draft, and the grid is there with its week numbers.
        compose.onNodeWithText("August 15, 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Week 33").assertIsDisplayed()

        // **The card must size itself, and this is a CONVENTION check, not a rendering one.**
        //
        // A Compose `Dialog` defaults to `usePlatformDefaultWidth = true`, which hands its
        // content a width the platform chose. On the owner's phone that was ~320dp: each foot
        // button got ~139dp and "Set deadline" wrapped onto two lines (2026-08-29, photographed).
        //
        // Robolectric cannot see it. Its dialog window is the full 411dp whether the flag is set
        // or not, so the grid measures the same either way — an assertion on the rendered width
        // was written first, at `>= 280.dp` and then at `>= 330.dp`, and **passed with the flag
        // back on both times**. That is the same wall as the keyboard: a headless host does not
        // reproduce a platform window's sizing. So the source is checked instead, the way
        // `sheet-units.test.ts` checks vaul's `repositionInputs` on the web.
        // Comments are stripped first, and that is not fussiness: the note above the call names
        // the flag twice, so the first version of this check passed with the code reverted — a
        // source scan that reads its own explanation as evidence proves nothing.
        val source = java.io.File(
            "src/main/java/com/spiramindscape/android/ui/components/FormComponents.kt",
        ).readText().lines().filterNot { it.trim().startsWith("//") }
            .joinToString("\n")
        assertTrue(
            "DeadlinePickerDialog must pass `usePlatformDefaultWidth = false` and state its own " +
                "width — without it the platform picks one and the foot's labels wrap.",
            source.contains("DialogProperties(usePlatformDefaultWidth = false)"),
        )

        // **The foot's words fit on one line.** `SpiraButton` sets `maxLines = 1` now, so a
        // label with too little room ellipsises instead of silently growing the button a second
        // line. This assertion holds that: a single-line Material button is ~48dp, two lines push
        // it past 60. (It would not have caught the original defect either — see above — but it
        // does catch a `maxLines` that goes missing.)
        val confirm = compose.onNodeWithText("Set deadline").getUnclippedBoundsInRoot()
        val confirmHeight = confirm.bottom - confirm.top
        assertTrue(
            "the confirm button is $confirmHeight tall — its label has wrapped, so the dialog " +
                "is too narrow again (check `usePlatformDefaultWidth`).",
            confirmHeight < 60.dp,
        )

        // The year menu is a Popup inside a Dialog window — a pairing that has its own history of
        // going wrong. Opening it must not throw, and it must actually show years.
        compose.onNodeWithText("2026").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2027").assertIsDisplayed()

        // Nothing has been committed by any of that: a day is a draft, the foot commits.
        assertEquals("unset", saved)
    }
}
