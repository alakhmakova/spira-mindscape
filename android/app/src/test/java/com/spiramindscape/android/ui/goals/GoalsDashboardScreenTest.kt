package com.spiramindscape.android.ui.goals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.goals.GoalSummary
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Compose UI test for the goals dashboard, run on the JVM via Robolectric. */
@RunWith(RobolectricTestRunner::class)
class GoalsDashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val goal = GoalSummary("g1", "Learn Kotlin", 7, "2026-08-01T00:00:00Z", 0.5f, 3, false)
    private val user = AuthUser(id = 1, email = "tester@example.com", name = "Tester", pictureUrl = null)

    @Test
    fun `renders a goal card with title, progress and confidence`() {
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Content(listOf(goal)),
                visibleGoals = listOf(goal),
                user = user,
            )
        }

        compose.onNodeWithText("Learn Kotlin").assertIsDisplayed()
        compose.onNodeWithText("50%").assertIsDisplayed() // inside the progress ring
        compose.onNodeWithText("All goals").assertIsDisplayed()
    }

    @Test
    fun `search icon opens a full-width search bar and closes`() {
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Content(listOf(goal)),
                visibleGoals = listOf(goal),
                user = user,
            )
        }

        compose.onNodeWithContentDescription("Search goals").performClick()
        compose.onNodeWithText("Search for goals").assertIsDisplayed() // the placeholder
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithContentDescription("Search goals").assertIsDisplayed() // back to normal
    }

    @Test
    fun `long-press reveals the confidence banner`() {
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Content(listOf(goal)),
                visibleGoals = listOf(goal),
                user = user,
            )
        }

        // Hidden by default, revealed on long-press.
        compose.onNodeWithText("Learn Kotlin").performTouchInput { longClick() }
        compose.onNodeWithText("Confidence 7/10").assertIsDisplayed()
    }

    @Test
    fun `shows the empty state when there are no goals`() {
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Content(emptyList()),
                visibleGoals = emptyList(),
                user = user,
            )
        }
        compose.onNodeWithText("No goals yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `error state offers a retry that fires the callback`() {
        var retried = false
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Error("Couldn't load your goals."),
                visibleGoals = emptyList(),
                user = user,
                onRetry = { retried = true },
            )
        }

        compose.onNodeWithText("Couldn't load your goals.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }

    @Test
    fun `drawer shows the email and signs out`() {
        var loggedOut = false
        compose.setContent {
            GoalsDashboardScreen(
                state = GoalsUiState.Content(listOf(goal)),
                visibleGoals = listOf(goal),
                user = user,
                onLogout = { loggedOut = true },
            )
        }

        // The drawer (with the account block at the bottom) is composed; Sign out uses a
        // semantics click action, so we drive it directly without animating the drawer open.
        compose.onNodeWithText("tester@example.com").assertExists() // full email at the bottom
        compose.onNodeWithText("Sign out").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(loggedOut)
    }
}
