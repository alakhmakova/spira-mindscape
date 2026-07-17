package com.spiramindscape.android.ui.goals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.OptionItem
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.data.goals.TextItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoalWorkspaceScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Regression: the workspace renders one LazyColumn containing targets, options and resources.
     * Their ids come from different backend tables and can collide (e.g. a target and an option
     * both id "5"). With id-only LazyColumn keys that threw "key was already used" and crashed
     * the app. Keys are now namespaced per type; this goal (all three share id "5") must render.
     */
    @Test
    fun `renders even when a target, option and resource share an id`() {
        val goal = GoalDetail(
            id = "g1", title = "My Goal", description = "", confidence = 5, deadline = null,
            progress = 0.5f, achieved = false,
            actions = listOf(TextItem("1", "an action")),
            obstacles = emptyList(),
            options = listOf(OptionItem("5", "an option", selected = false)),
            targets = listOf(TargetItem.Binary("5", "a target", 0f, null, false, done = false)),
            resources = listOf(ResourceItem("5", "note", "a resource")),
        )

        compose.setContent {
            GoalWorkspaceScreen(
                state = GoalUiState.Content(goal),
                actions = GoalWorkspaceActions(),
                user = AuthUser(1, "t@e.com", "T", null),
            )
        }

        // The Goal tab shows the title; switching tabs renders the colliding-id target and
        // resource without the old duplicate-key crash.
        compose.onNodeWithText("My Goal").assertIsDisplayed()
        // Bottom-navigation tabs (clickable; the drawer's rubric titles share the same text).
        compose.onNode(hasText("Targets") and hasClickAction()).performClick()
        compose.onNodeWithText("a target").assertIsDisplayed()
        compose.onNode(hasText("Resources") and hasClickAction()).performClick()
        compose.onNodeWithText("a resource").assertIsDisplayed()
    }
}
