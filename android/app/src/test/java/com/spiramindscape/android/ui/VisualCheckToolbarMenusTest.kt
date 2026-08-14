package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.SpiraMenuChoice
import com.spiramindscape.android.ui.components.SpiraMenuColumnDivider
import com.spiramindscape.android.ui.components.SpiraMenuColumns
import com.spiramindscape.android.ui.components.SpiraMenuGroup
import com.spiramindscape.android.ui.components.SpiraMenuSurface
import com.spiramindscape.android.ui.goals.TargetDeadlineFilter
import com.spiramindscape.android.ui.goals.TargetFilter
import com.spiramindscape.android.ui.goals.TargetLockFilter
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two toolbar menu bodies drawn at phone width, right-aligned exactly as the popup places
 * them, with the 12dp margin the position provider keeps.
 *
 * This is the picture that matters for the 2026-08-13 redesign: **three columns plus their
 * hairlines have to fit across a phone**, and an existence assertion stays perfectly green with
 * the "Lock" column hanging off the right of the screen. It also shows the drop shadow, which used
 * to be `shadowElevation = 12.dp` — a hard grey band round the card rather than depth.
 *
 * Rendered directly rather than by opening a real menu: a [androidx.compose.ui.window.Popup] lives
 * in its own window, which the screenshot helper (it draws the activity's decor view) cannot
 * reach. `VisualCheckToolbarWiringTest` is what checks that the trigger really opens them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckToolbarMenusTest : VisualCheckTestBase() {

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the menu bodies fit across a phone`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SpiraMenuSurface {
                        SpiraMenuColumns {
                            SpiraMenuGroup("Sort by") {
                                listOf("Name", "Progress", "Deadline").forEachIndexed { i, label ->
                                    SpiraMenuChoice(label, {}, selected = i == 0)
                                }
                            }
                            SpiraMenuColumnDivider()
                            SpiraMenuGroup("Direction") {
                                SpiraMenuChoice("Ascending", {}, selected = true)
                                SpiraMenuChoice("Descending", {})
                            }
                        }
                    }
                    SpiraMenuSurface {
                        SpiraMenuColumns {
                            SpiraMenuGroup("Status") {
                                TargetFilter.entries.forEach {
                                    SpiraMenuChoice(it.label, {}, selected = it == TargetFilter.All)
                                }
                            }
                            SpiraMenuColumnDivider()
                            SpiraMenuGroup("Deadline") {
                                TargetDeadlineFilter.entries.forEach {
                                    SpiraMenuChoice(
                                        it.label,
                                        {},
                                        selected = it == TargetDeadlineFilter.Overdue,
                                    )
                                }
                            }
                            SpiraMenuColumnDivider()
                            SpiraMenuGroup("Lock") {
                                TargetLockFilter.entries.forEach {
                                    SpiraMenuChoice(
                                        it.label,
                                        {},
                                        selected = it == TargetLockFilter.All,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("toolbar-menus")
    }
}
