package com.storyboard.editor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.storyboard.editor.ui.home.HomeScreen
import com.storyboard.editor.ui.editor.TimelineEditorScreen
import com.storyboard.editor.ui.export.ExportScreen

object Routes {
    const val HOME = "home"
    const val TIMELINE = "timeline"
    const val EXPORT = "export/{projectId}"

    fun exportRoute(projectId: String) = "export/$projectId"
}

@Composable
fun StoryboardNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToTimeline = { navController.navigate(Routes.TIMELINE) }
            )
        }
        composable(Routes.TIMELINE) {
            TimelineEditorScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExport = { projectId ->
                    navController.navigate(Routes.exportRoute(projectId))
                }
            )
        }
        composable(Routes.EXPORT) {
            ExportScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
    }
}
