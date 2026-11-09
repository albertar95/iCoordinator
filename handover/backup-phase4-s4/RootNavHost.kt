package com.icoordinator.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.icoordinator.R
import com.icoordinator.ui.accounting.AccountingNavHost
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.TbIcon

/** Root routes: the home menu and the two areas of the app. */
object RootRoutes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val ACCOUNTING = "accounting"
}

/** App entry: home menu with two rows; each area has its own inner NavHost and Back returns to home. */
@Composable
fun RootNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = RootRoutes.HOME) {
        composable(RootRoutes.HOME) {
            HomeMenu(
                onOpenTasks = {
                    navController.navigate(RootRoutes.TASKS) { launchSingleTop = true }
                },
                onOpenAccounting = {
                    navController.navigate(RootRoutes.ACCOUNTING) { launchSingleTop = true }
                },
            )
        }
        composable(RootRoutes.TASKS) { AppNavHost() }
        composable(RootRoutes.ACCOUNTING) {
            AccountingNavHost(onExit = { navController.popBackStack() })
        }
    }
}

@Composable
private fun HomeMenu(onOpenTasks: () -> Unit, onOpenAccounting: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        PageHeader(title = "آی‌کوردینیتور")
        Text(
            text = "یک بخش را انتخاب کنید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeCard(
                icon = R.drawable.ic_tb_checklist,
                title = "مدیریت کارها",
                subtitle = "پروژه‌ها، کارهای هفته، روتین‌ها و تقویم",
                onClick = onOpenTasks,
            )
            HomeCard(
                icon = R.drawable.ic_tb_wallet,
                title = "حسابداری شخصی",
                subtitle = "تراکنش‌ها، صندوق‌ها، وام‌ها و گزارش‌ها",
                onClick = onOpenAccounting,
            )
        }
    }
}

@Composable
private fun HomeCard(@DrawableRes icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon = icon, size = 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        TbIcon(R.drawable.ic_tb_chevron_left, null, tint = scheme.onSurfaceVariant)
    }
}
