package com.spiramindscape.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spiramindscape.android.ui.components.SpiraInlineBanner
import com.spiramindscape.android.ui.components.SpiraNoticeKind
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test

/**
 * Renders the in-block notice in all four kinds so they can be checked by eye — a long message
 * wrapping, the dismiss X staying reachable, the glyph sitting on the message's **first** line, and
 * the card staying white in every kind with only the mark changing. Existence assertions cannot
 * tell you any of that.
 *
 * The card is the same one a toast draws ([com.spiramindscape.android.ui.components.SpiraToast]),
 * which is the whole point of the shared shape (owner, 2026-08-18).
 *
 * Look at `app/build/reports/visual/inline-banner.png`.
 */
class VisualCheckInlineBannerTest : VisualCheckTestBase() {

    @Test
    fun `the inline notice draws in every kind`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpiraInlineBanner(
                        message = "\"Set up manager lunch\" added to your checklist",
                        onDismiss = {},
                        kind = SpiraNoticeKind.Success,
                    )
                    SpiraInlineBanner(
                        message = "Couldn't delete this goal. Please try again.",
                        onDismiss = {},
                        kind = SpiraNoticeKind.Error,
                    )
                    // The filter-hid-everything case: a warning, and one the user cannot dismiss —
                    // it stands until the filter changes, so an X on it would be a lie.
                    SpiraInlineBanner(
                        message = "No goals match that search or filter.",
                        onDismiss = null,
                        kind = SpiraNoticeKind.Warning,
                    )
                    SpiraInlineBanner(
                        message = "Clear the search and filter to rearrange.",
                        onDismiss = null,
                        kind = SpiraNoticeKind.Info,
                    )
                    // A long message must wrap rather than push the dismiss X off-screen — the
                    // exact class of defect this suite exists to catch — and the glyph must stay on
                    // the first line rather than centring itself against three.
                    SpiraInlineBanner(
                        message = "Something went wrong while saving your changes, and the app " +
                            "could not reach the server. Your work is still here — please try again.",
                        onDismiss = {},
                        kind = SpiraNoticeKind.Error,
                    )
                    Text(
                        "Content stays visible behind the notice",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
        saveWindow("inline-banner")
    }
}
