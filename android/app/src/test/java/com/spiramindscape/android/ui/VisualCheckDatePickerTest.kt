package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.SpiraMonthGrid
import com.spiramindscape.android.ui.components.SpiraSheetHead
import com.spiramindscape.android.ui.theme.SpiraTheme
import com.spiramindscape.android.ui.theme.spiraExtras
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **The date picker, with its ISO week numbers** (owner, 2026-08-29).
 *
 * Material 3's `DatePicker` cannot draw a week-number column, so the grid is the app's own — and
 * a hand-drawn grid is exactly the kind of thing that renders subtly wrong while every assertion
 * stays green, which is why this writes `app/build/reports/visual/date-picker.png` as well as
 * asserting.
 *
 * The numbers are pinned against a **known month**: August 2026 begins on a Saturday, so the row
 * holding the 1st is ISO week **31** and the six rows run 31–36. That is also what the web draws
 * for the same month (`react-day-picker` with `ISOWeek`), so if the two ever disagree this test
 * says which one moved. `WeekFields.ISO` is what makes them agree — `WeekFields.of(Locale)` would
 * number the same rows differently on a US locale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckDatePickerTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the month grid numbers its weeks the way the web does`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.spiraExtras.surfaceRaised),
                    ) {
                        // The head states the draft — the date once one is chosen, "Set
                        // deadline" only while there is none. Short on purpose: the weekday and
                        // the days-left the old line carried truncate the band on a narrow
                        // phone (owner, 2026-08-29).
                        SpiraSheetHead("August 15, 2026", {})
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            SpiraMonthGrid(
                                month = YearMonth.of(2026, 8),
                                selected = LocalDate.of(2026, 8, 15),
                                onSelect = {},
                                onMonthChange = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("date-picker")

        // The column that Material could not give us at all. By content description, because
        // the digits alone are ambiguous — this month's rows are 31–36 and it also *contains* a
        // 31st, twice over (July's and August's).
        compose.onNodeWithText("W").assertIsDisplayed()
        for (week in 31..36) {
            compose.onNodeWithContentDescription("Week $week").assertIsDisplayed()
        }
        // Monday first, like the web — not Sunday, which is what a locale-derived week would do.
        compose.onNodeWithText("Mo").assertIsDisplayed()
        compose.onNodeWithText("Su").assertIsDisplayed()
    }
}
