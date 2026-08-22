package com.spiramindscape.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import com.spiramindscape.android.ui.components.SpiraBadgeTone
import com.spiramindscape.android.ui.components.SpiraChoice
import com.spiramindscape.android.ui.components.SpiraFilterSheetContent
import com.spiramindscape.android.ui.components.SpiraSegmented
import com.spiramindscape.android.ui.components.SpiraSheetCards
import com.spiramindscape.android.ui.components.SpiraSheetConfidence
import com.spiramindscape.android.ui.components.SpiraSheetDateRange
import com.spiramindscape.android.ui.components.SpiraSheetChoices
import com.spiramindscape.android.ui.components.SpiraSheetGroup
import com.spiramindscape.android.ui.components.SpiraSheetPills
import com.spiramindscape.android.ui.theme.SpiraTheme
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.goals.DeadlineFilter
import com.spiramindscape.android.ui.goals.OptionFilter
import com.spiramindscape.android.ui.goals.SortKey
import com.spiramindscape.android.ui.goals.TargetDeadlineFilter
import com.spiramindscape.android.ui.goals.TargetTypeFilter
import com.spiramindscape.android.ui.goals.TargetFilter
import com.spiramindscape.android.ui.goals.StatusFilter
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The All-goals Filter & Sort sheet, rendered as pixels.
 *
 * `SpiraFilterSheetContent` rather than the modal wrapper: a `ModalBottomSheet` renders in its own
 * window, which the screenshot helper (it draws the activity's decor view) cannot capture — so an
 * open sheet is simply absent from the PNG. What this is here to catch is the two side-by-side
 * columns running off the side of a phone, which no existence assertion can see.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckFilterSheetTest : VisualCheckTestBase() {

    @Test
    fun `filter and sort sheet renders inside the screen`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                SpiraFilterSheetContent(
                    title = "Filter & Sort",
                    onDismiss = {},
                    onReset = {},
                    // Closed here and open on the target sheet below, so one sweep of the PNGs
                    // shows both states of the padlock (owner, 2026-08-21).
                    locked = true,
                    onLockedChange = {},
                ) {
                    SpiraSheetGroup("Status") {
                        SpiraSheetPills(
                            options = listOf(
                                SpiraChoice(StatusFilter.All, "All"),
                                SpiraChoice(StatusFilter.Achieved, "Achieved"),
                                SpiraChoice(StatusFilter.NotAchieved, "Not achieved"),
                            ),
                            value = StatusFilter.Achieved,
                            onChange = {},
                        )
                    }
                    SpiraSheetGroup("Deadline") {
                        SpiraSheetPills(
                            options = listOf(
                                SpiraChoice(DeadlineFilter.Any, "Any"),
                                SpiraChoice(DeadlineFilter.Has, "Deadline"),
                                SpiraChoice(DeadlineFilter.None, "No deadline"),
                            ),
                            value = DeadlineFilter.Has,
                            onChange = {},
                            tone = SpiraBadgeTone.Info,
                        )
                    }
                    // The two questions the phone gained on 2026-08-18: the dates themselves, and
                    // the confidence grid. Both are here because neither can be checked by an
                    // assertion — a two-column range or a ten-cell grid either fits across the
                    // sheet or it does not.
                    SpiraSheetGroup("Deadline range") {
                        SpiraSheetDateRange(
                            from = "2026-03-01T00:00:00Z",
                            to = "",
                            onFromChange = {},
                            onToChange = {},
                        )
                    }
                    SpiraSheetGroup("Confidence") {
                        SpiraSheetConfidence(value = 7, onChange = {})
                    }
                    SpiraSheetGroup("Direction") {
                        SpiraSegmented(
                            options = listOf(
                                SpiraChoice(true, "Ascending"),
                                SpiraChoice(false, "Descending"),
                            ),
                            value = false,
                            onChange = {},
                        )
                    }
                    SpiraSheetGroup("Sort by") {
                        SpiraSheetCards(
                            options = SortKey.entries.map { SpiraChoice(it, it.label) },
                            value = SortKey.Deadline,
                            onChange = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("filter-sort-sheet")
    }

    /**
     * The **target** sheet's questions, plus the Options lean pills.
     *
     * What this is here to see: that "Progress" now heads the done-ness answers and "Status" the
     * started ones, that **Type** wraps its four answers without running off the sheet, that the
     * deadline range sits under the overdue question without pushing the sheet off the screen, and
     * that the two lean pills carry the card's own smileys at a size where they read as faces
     * rather than as smudges.
     */
    @Test
    // Tall on purpose: the sheet scrolls, so a short frame simply clips the questions at the foot
    // and the check silently stops checking them.
    @Config(qualifiers = "w411dp-h1200dp")
    fun `target filter sheet renders its questions`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                SpiraFilterSheetContent(
                    title = "Filter",
                    onDismiss = {},
                    onReset = {},
                    locked = false,
                    onLockedChange = {},
                ) {
                    SpiraSheetGroup("Progress") {
                        SpiraSheetPills(
                            options = listOf(TargetFilter.All, TargetFilter.Done, TargetFilter.NotDone)
                                .map { SpiraChoice(it, it.label) },
                            value = TargetFilter.Done,
                            onChange = {},
                        )
                    }
                    SpiraSheetGroup("Status") {
                        SpiraSheetPills(
                            options = listOf(TargetFilter.Started, TargetFilter.NotStarted)
                                .map { SpiraChoice(it, it.label) },
                            value = TargetFilter.Started,
                            onChange = {},
                            tone = SpiraBadgeTone.Info,
                        )
                    }
                    SpiraSheetGroup("Deadline") {
                        SpiraSheetPills(
                            options = TargetDeadlineFilter.entries.map { SpiraChoice(it, it.label) },
                            value = TargetDeadlineFilter.Overdue,
                            onChange = {},
                            tone = SpiraBadgeTone.Warning,
                        )
                    }
                    SpiraSheetGroup("Type") {
                        SpiraSheetPills(
                            options = TargetTypeFilter.entries.map { SpiraChoice(it, it.label) },
                            value = TargetTypeFilter.Checklist,
                            onChange = {},
                            tone = SpiraBadgeTone.Intelligence,
                        )
                    }
                    SpiraSheetGroup("Deadline range") {
                        SpiraSheetDateRange(
                            from = "2026-03-01T00:00:00Z",
                            to = "2026-09-30T00:00:00Z",
                            onFromChange = {},
                            onToChange = {},
                        )
                    }
                    SpiraSheetGroup("Idea") {
                        SpiraSheetPills(
                            options = OptionFilter.entries.map { SpiraChoice(it, it.label, it.icon) },
                            value = OptionFilter.GoodIdea,
                            onChange = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("target-filter-sheet")
    }

    /**
     * The Options lean filter on its own, big enough in the frame to see the **faces**.
     *
     * On the target sheet these pills sit below the fold, and a smiley that reads as a smudge at
     * 13dp is exactly the kind of fault no assertion can fail on.
     */
    @Test
    fun `options lean pills carry the card smileys`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                SpiraFilterSheetContent(
                    title = "Filter",
                    onDismiss = {},
                    onReset = {},
                ) {
                    SpiraSheetGroup("Idea") {
                        SpiraSheetPills(
                            options = OptionFilter.entries.map { SpiraChoice(it, it.label, it.icon) },
                            value = OptionFilter.GoodIdea,
                            onChange = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("options-lean-pills")
    }
}
