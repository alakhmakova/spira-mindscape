package com.spiramindscape.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.theme.AppFont
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A specimen of every Fonts-tab candidate, in Latin **and** Cyrillic.
 *
 * A font that failed to load does not disappear — the text is still there, drawn in the system
 * sans — so no existence assertion can catch it. This renders each face's name in its own family
 * so the failure is visible: a row set in the wrong face is a row whose file did not resolve.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualCheckFontsTest : VisualCheckTestBase() {

    @Test
    fun `every candidate face renders`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppFont.entries.forEach { font ->
                        // Both weights on one row: a family whose heavy slot did not resolve draws
                        // its bold in the system sans, and the two halves then disagree visibly.
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "${font.label} Цель",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = font.fontFamily,
                                ),
                                fontWeight = FontWeight.Normal,
                            )
                            Text(
                                "${font.label} Цель",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = font.fontFamily,
                                ),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveWindow("font-specimens")
    }
}
