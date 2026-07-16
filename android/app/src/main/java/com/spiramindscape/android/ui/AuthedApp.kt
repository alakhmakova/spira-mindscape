package com.spiramindscape.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spiramindscape.android.ui.goals.GoalWorkspaceRoute
import com.spiramindscape.android.ui.goals.GoalsRoute

/** Navigation for the signed-in app: goals dashboard → goal workspace. */
@Composable
fun AuthedApp(onLogout: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "goals") {
        composable("goals") {
            GoalsRoute(
                onGoalClick = { goalId -> nav.navigate("goal/$goalId") },
                onLogout = onLogout,
            )
        }
        composable(
            route = "goal/{goalId}",
            arguments = listOf(navArgument("goalId") { type = NavType.StringType }),
        ) { entry ->
            GoalWorkspaceRoute(
                goalId = entry.arguments?.getString("goalId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
    }
}
