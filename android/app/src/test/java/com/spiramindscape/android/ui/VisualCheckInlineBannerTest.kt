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
import com.spiramindscape.android.ui.theme.SpiraTheme
import org.junit.Test

/**
 * Renders the failure banner so it can be checked by eye — a long message wrapping, the dismiss
 * button staying reachable, and the error tint reading as a warning rather than as decoration.
 * Existence assertions cannot tell you any of that.
 *
 * Look at `app/build/reports/visual/inline-banner.png`.
 */
class VisualCheckInlineBannerTest : VisualCheckTestBase() {

    @Test
    fun `the inline error banner draws`() {
        compose.activityRule.scenario.onActivity { }
        compose.setContent {
            SpiraTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SpiraInlineBanner(
                        message = "Couldn't delete this goal. Please try again.",
                        onDismiss = {},
                    )
                    SpiraInlineBanner(
                        message = "Couldn't create this goal. Please try again.",
                        onDismiss = {},
                    )
                    // A long message must wrap rather than push the dismiss button off-screen —
                    // the exact class of defect this suite exists to catch.
                    SpiraInlineBanner(
                        message = "Something went wrong while saving your changes, and the app " +
                            "could not reach the server. Your work is still here — please try again.",
                        onDismiss = {},
                    )
                    Text(
                        "Content stays visible behind the banner",
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
