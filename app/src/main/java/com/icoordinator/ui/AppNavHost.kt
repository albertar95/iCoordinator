package com.icoordinator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.icoordinator.ui.calendar.CalendarScreen
import com.icoordinator.ui.goals.GoalScreen
import com.icoordinator.ui.projects.ProjectDetailScreen
import com.icoordinator.ui.projects.ProjectsScreen
import com.icoordinator.ui.report.ReportScreen
import com.icoordinator.ui.routines.RoutinesScreen
import com.icoordinator.ui.settings.SettingsScreen
import com.icoordinator.ui.weekly.WeeklyLogScreen

/** مسیرهای اصلی برنامه */
object Routes {
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{id}"
    const val GOAL_DETAIL = "goal/{id}"
    const val WEEKLY_LOG = "weekly_log"
    const val ROUTINES = "routines"
    const val CALENDAR = "calendar"
    const val REPORT = "report"
    const val SETTINGS = "settings"

    fun projectDetail(id: Long) = "project/$id"

    fun goalDetail(id: Long) = "goal/$id"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val onDetail = current == Routes.PROJECT_DETAIL || current == Routes.GOAL_DETAIL || current == Routes.SETTINGS
    val showGear = current != null && !onDetail

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!onDetail) {
                AppBottomBar(
                    currentRoute = current,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        NavHost(
            navController = navController,
            startDestination = Routes.PROJECTS,
            // 24 dp extra room above the bar so nothing sits under the raised centre circle
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (onDetail) 0.dp else 24.dp),
        ) {
            composable(Routes.PROJECTS) {
                ProjectsScreen(onOpenProject = { id -> navController.navigate(Routes.projectDetail(id)) })
            }
            composable(
                Routes.PROJECT_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                ProjectDetailScreen(
                    projectId = id,
                    onBack = { navController.popBackStack() },
                    onOpenGoal = { gid -> navController.navigate(Routes.goalDetail(gid)) },
                )
            }
            composable(
                Routes.GOAL_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                GoalScreen(goalId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.WEEKLY_LOG) { WeeklyLogScreen() }
            composable(Routes.ROUTINES) { RoutinesScreen() }
            composable(Routes.CALENDAR) { CalendarScreen() }
            composable(Routes.REPORT) { ReportScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        }
        if (showGear) {
            // top-left under RTL, just right of the 40 dp header action circle (20 dp margin + 40 dp + 8 dp gap)
            IconButton(
                onClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 68.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "تنظیمات",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        }
    }
}
