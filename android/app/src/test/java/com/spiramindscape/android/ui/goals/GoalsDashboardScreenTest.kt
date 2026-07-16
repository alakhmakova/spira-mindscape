package com.spiramindscape.android.ui.goals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.goals.GoalSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI test for the goals dashboard, run on the JVM via Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class GoalsDashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val goal = GoalSummary("g1", "Learn Kotlin", 7, "2026-08-01T00:00:00Z", 0.5f, 3, false)

    @Test
    fun `renders a goal card with title, progress and confidence`() {
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Content(listOf(goal)),
                onRetry = {},
                onLogout = {},
            )
        }

        compose.onNodeWithText("Learn Kotlin").assertIsDisplayed()
        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.onNodeWithText("Confidence 7/10").assertIsDisplayed()
    }

    @Test
    fun `shows the empty state when there are no goals`() {
        compose.setContent {
            GoalsDashboardScreen(state = GoalsUiState.Content(emptyList()), onRetry = {}, onLogout = {})
        }
        compose.onNodeWithText("No goals yet.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `error state offers a retry that fires the callback`() {
        var retried = false
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Error("Couldn't load your goals."),
                onRetry = { retried = true },
                onLogout = {},
            )
        }

        compose.onNodeWithText("Couldn't load your goals.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }

    @Test
    fun `sign out button fires the callback`() {
        var loggedOut = false
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Content(listOf(goal)),
                onRetry = {},
                onLogout = { loggedOut = true },
            )
        }

        compose.onNodeWithText("Sign out").performClick()
        assertTrue(loggedOut)
    }
}
