package com.icoordinator.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.LoanPlanner
import com.icoordinator.accounting.MoneyFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.foundation.background
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

/** Live summaries for the two home cards; null lines mean "show nothing" (also when loading fails). */
data class HomeSummary(
    val tasksLine: String? = null,
    val spendLine: String? = null,
    val installmentLine: String? = null,
)

class HomeViewModel : ViewModel() {
    private val app = ICoordinatorApp.instance

    private val tasksLine: kotlinx.coroutines.flow.Flow<String?> = run {
        val today = LocalDate.now()
        app.taskRepository.observeRange(today, today.plusDays(1)).map { tasks ->
            if (tasks.isEmpty()) "امروز کاری نداری"
            else AccountingFormat.digits("${tasks.size} کار امروز، ${tasks.count { it.isDone }} انجام‌شده")
        }
    }

    private val accountingLines: kotlinx.coroutines.flow.Flow<Pair<String?, String?>> = run {
        val repo = app.accountingRepository
        combine(
            repo.observeAccounts(), repo.observeTransactions(), repo.observeLoans(), repo.observeInstallments(),
        ) { accounts, txs, loans, installments ->
            val today = LocalDate.now()
            val (y, m) = AccountingFormat.currentMonth(today)
            val range = AccountingFormat.monthRange(y, m)
            val expense = AccountingMath.periodTotals(accounts, txs, range.first, range.last).expense
            val spend = "هزینه‌ی این ماه: " + MoneyFormat.format(expense) + " تومان"
            val titles = loans.associate { it.id to it.title }
            val next = LoanPlanner.nextDuePerLoan(loans, installments).values.minByOrNull { it.dueEpochDay }
            val inst = if (next == null) "قسطی در پیش نیست" else {
                val days = next.dueEpochDay - today.toEpochDay()
                val title = titles[next.loanId] ?: ""
                val whenText = when {
                    days > 0L -> AccountingFormat.digits("$days روز دیگر")
                    days == 0L -> "امروز"
                    else -> AccountingFormat.digits("${-days} روز معوق")
                }
                "قسط بعدی: $title - $whenText"
            }
            spend to inst
        }
    }

    val summary: StateFlow<HomeSummary> = combine(
        tasksLine.catchNull(), accountingLines.catchPair(),
    ) { t, a -> HomeSummary(t, a.first, a.second) }
        .catch { e -> if (e is CancellationException) throw e else emit(HomeSummary()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary())
}

private fun kotlinx.coroutines.flow.Flow<String?>.catchNull(): kotlinx.coroutines.flow.Flow<String?> =
    catch { e -> if (e is CancellationException) throw e else emit(null) }

private fun kotlinx.coroutines.flow.Flow<Pair<String?, String?>>.catchPair(): kotlinx.coroutines.flow.Flow<Pair<String?, String?>> =
    catch { e -> if (e is CancellationException) throw e else emit(null to null) }

private fun greeting(hour: Int): String = when {
    hour < 12 -> "صبح بخیر"
    hour < 18 -> "عصر بخیر"
    else -> "شب بخیر"
}

@Composable
private fun HomeMenu(onOpenTasks: () -> Unit, onOpenAccounting: () -> Unit) {
    val vm: HomeViewModel = viewModel(key = "home")
    val summary by vm.summary.collectAsStateWithLifecycle()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeader(dark)
        HomeCard(
            icon = R.drawable.ic_tb_checklist,
            title = "مدیریت کارها",
            lines = listOfNotNull(summary.tasksLine),
            colors = if (dark) listOf(Color(0xFF4338CA), Color(0xFF6D28D9)) else listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
            onClick = onOpenTasks,
        )
        HomeCard(
            icon = R.drawable.ic_tb_wallet,
            title = "حسابداری شخصی",
            lines = listOfNotNull(summary.spendLine, summary.installmentLine),
            colors = if (dark) listOf(Color(0xFF047857), Color(0xFF0F766E)) else listOf(Color(0xFF10B981), Color(0xFF0D9488)),
            onClick = onOpenAccounting,
        )
    }
}

@Composable
private fun HomeHeader(dark: Boolean) {
    val colors = if (dark) listOf(Color(0xFF3730A3), Color(0xFF0369A1)) else listOf(Color(0xFF4F46E5), Color(0xFF0EA5E9))
    val dateText = remember { AccountingFormat.dayHeader(AccountingFormat.todayJalali()) }
    val greet = remember { greeting(LocalTime.now().hour) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(colors))
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "iCoordinator",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            if (dateText.isNotEmpty()) {
                Text(text = dateText, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f))
            }
            Text(text = greet, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun HomeCard(
    @DrawableRes icon: Int,
    title: String,
    lines: List<String>,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "homeCardScale")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            TbIcon(icon, null, size = 34.dp, tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            }
        }
        TbIcon(R.drawable.ic_tb_chevron_right, null, tint = Color.White)
    }
}
