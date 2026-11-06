package com.icoordinator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.icoordinator.ui.calendar.CalendarScreen
import com.icoordinator.ui.projects.ProjectDetailScreen
import com.icoordinator.ui.projects.ProjectsScreen
import com.icoordinator.ui.report.ReportScreen
import com.icoordinator.ui.routines.RoutinesScreen
import com.icoordinator.ui.weekly.WeeklyLogScreen

/** مسیرهای اصلی برنامه */
object Routes {
    const val PROJECTS = "projects"
    const val PROJECT_DETAIL = "project/{id}"
    const val WEEKLY_LOG = "weekly_log"
    const val ROUTINES = "routines"
    const val CALENDAR = "calendar"
    const val REPORT = "report"

    fun projectDetail(id: Long) = "project/$id"
}

data class BottomItem(val route: String, val label: String)

val bottomItems = listOf(
    BottomItem(Routes.PROJECTS, "پروژه‌ها"),
    BottomItem(Routes.WEEKLY_LOG, "لاگ هفتگی"),
    BottomItem(Routes.ROUTINES, "روتین‌ها"),
    BottomItem(Routes.CALENDAR, "تقویم"),
    BottomItem(Routes.REPORT, "گزارش هفته"),
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val onDetail = current == Routes.PROJECT_DETAIL

    Scaffold(
        bottomBar = {
            if (!onDetail) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = current == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            // icon is a required (non-null) slot in NavigationBarItem, so it stays empty: no icons (F1)
                            icon = { },
                            label = {
                                Text(
                                    item.label,
                                    fontWeight = if (current == item.route) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ExactAlarmBanner()

            NavHost(
                navController = navController,
                startDestination = Routes.PROJECTS,
                modifier = Modifier.weight(1f),
            ) {
                composable(Routes.PROJECTS) {
                    ProjectsScreen(onOpenProject = { id -> navController.navigate(Routes.projectDetail(id)) })
                }
                composable(
                    Routes.PROJECT_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: return@composable
                    ProjectDetailScreen(projectId = id, onBack = { navController.popBackStack() })
                }
                composable(Routes.WEEKLY_LOG) { WeeklyLogScreen() }
                composable(Routes.ROUTINES) { RoutinesScreen() }
                composable(Routes.CALENDAR) { CalendarScreen() }
                composable(Routes.REPORT) { ReportScreen() }
            }
        }
    }
}
