package com.icoordinator.ui.accounting

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.icoordinator.R
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.TxType
import com.icoordinator.ui.components.IconCircle

/** Routes of the accounting section. */
object AccRoutes {
    const val DASHBOARD = "acc_dashboard"
    const val TRANSACTIONS = "acc_transactions"
    const val REPORTS = "acc_reports"
    const val LOANS = "acc_loans"
    const val ACCOUNTS = "acc_accounts"
    const val TX_FORM = "acc_tx_form/{type}?id={id}"

    fun txForm(type: TxType, id: Long? = null): String = "acc_tx_form/${type.name}?id=${id ?: -1L}"
}

/** Icon per account kind (D62). */
@DrawableRes
fun accountKindIcon(kind: AccountKind): Int = when (kind) {
    AccountKind.EXPENSE -> R.drawable.ic_tb_receipt
    AccountKind.INCOME -> R.drawable.ic_tb_coin
    AccountKind.FUND -> R.drawable.ic_tb_wallet
    AccountKind.BANK -> R.drawable.ic_tb_building_bank
    AccountKind.DEBTOR, AccountKind.CREDITOR, AccountKind.PERSON -> R.drawable.ic_tb_users
    AccountKind.LIABILITY -> R.drawable.ic_tb_credit_card
    AccountKind.ASSET, AccountKind.CAPITAL, AccountKind.OTHER -> R.drawable.ic_tb_category
}

/** Persian label of a transaction type. */
fun txTypeLabel(type: TxType): String = when (type) {
    TxType.PAY -> "پرداخت"
    TxType.RECEIVE -> "دریافت"
    TxType.TRANSFER -> "انتقال"
    TxType.OTHER -> "سایر"
}

/** Colour for an amount of this type: PAY error, RECEIVE income green, others onSurfaceVariant. */
@Composable
fun txTypeColor(type: TxType): Color {
    val scheme = MaterialTheme.colorScheme
    return when (type) {
        TxType.PAY -> scheme.error
        TxType.RECEIVE -> if (scheme.background.luminance() < 0.5f) Color(0xFF7BD88F) else Color(0xFF2E7D32)
        TxType.TRANSFER, TxType.OTHER -> scheme.onSurfaceVariant
    }
}

private class AccBarItem(
    val route: String?,
    val label: String,
    @DrawableRes val icon: Int,
    val raised: Boolean = false,
)

// First item = rightmost under RTL. The raised centre item opens the add sheet (no route).
private val accBarItems = listOf(
    AccBarItem(AccRoutes.DASHBOARD, "خانه", R.drawable.ic_tb_home),
    AccBarItem(AccRoutes.TRANSACTIONS, "تراکنش‌ها", R.drawable.ic_tb_list_details),
    AccBarItem(null, "افزودن", R.drawable.ic_tb_plus, raised = true),
    AccBarItem(AccRoutes.REPORTS, "گزارش‌ها", R.drawable.ic_tb_chart_pie),
    AccBarItem(AccRoutes.LOANS, "وام‌ها", R.drawable.ic_tb_credit_card),
)

private val AccBarRowHeight = 72.dp
private val AccRaisedCircleSize = 64.dp
private val AccRaisedRing = 6.dp
private val AccRaisedOverhang = 38.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingNavHost(onExit: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    // full-screen routes hide the bottom bar
    val onForm = current == AccRoutes.TX_FORM || current == AccRoutes.ACCOUNTS
    var showAdd by remember { mutableStateOf(false) }

    fun goTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!onForm) {
                AccBottomBar(
                    currentRoute = current,
                    onNavigate = { goTab(it) },
                    onAdd = { showAdd = true },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AccRoutes.DASHBOARD,
            // 24 dp extra room above the bar so nothing sits under the raised centre circle
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = if (onForm) 0.dp else 24.dp),
        ) {
            composable(AccRoutes.DASHBOARD) {
                DashboardScreen(
                    onExit = onExit,
                    onOpenTransactions = { goTab(AccRoutes.TRANSACTIONS) },
                    onAdd = { showAdd = true },
                    onOpenAccounts = { navController.navigate(AccRoutes.ACCOUNTS) },
                )
            }
            composable(AccRoutes.TRANSACTIONS) {
                TransactionListScreen(
                    onEdit = { id -> navController.navigate(AccRoutes.txForm(TxType.OTHER, id)) },
                )
            }
            composable(AccRoutes.REPORTS) { ReportsScreen() }
            composable(AccRoutes.LOANS) { LoansScreen() }
            composable(AccRoutes.ACCOUNTS) { AccountsScreen(onBack = { navController.popBackStack() }) }
            composable(
                AccRoutes.TX_FORM,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val type = runCatching { TxType.valueOf(entry.arguments?.getString("type") ?: "") }
                    .getOrDefault(TxType.OTHER)
                val id = entry.arguments?.getLong("id") ?: -1L
                TransactionFormScreen(
                    type = type,
                    txId = id.takeIf { it > 0 },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                AddSheetRow("پرداخت", R.drawable.ic_tb_arrow_up) {
                    showAdd = false
                    navController.navigate(AccRoutes.txForm(TxType.PAY))
                }
                AddSheetRow("دریافت", R.drawable.ic_tb_arrow_down) {
                    showAdd = false
                    navController.navigate(AccRoutes.txForm(TxType.RECEIVE))
                }
                AddSheetRow("انتقال", R.drawable.ic_tb_arrows_exchange) {
                    showAdd = false
                    navController.navigate(AccRoutes.txForm(TxType.TRANSFER))
                }
            }
        }
    }
}

@Composable
private fun AddSheetRow(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon = icon, size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Bottom bar styled like AppBottomBar: five equal items, centre + as the raised circle. */
@Composable
private fun AccBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val barShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val raisedItem = accBarItems.firstOrNull { it.raised }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AccRaisedOverhang)
                .background(scheme.surfaceContainerHigh, barShape)
                .border(1.dp, scheme.outlineVariant, barShape)
                .navigationBarsPadding(),
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(AccBarRowHeight)) {
                accBarItems.forEach { item ->
                    AccBarEntry(
                        item = item,
                        selected = item.route != null && currentRoute == item.route,
                        onClick = { item.route?.let(onNavigate) ?: onAdd() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (raisedItem != null) {
            // 6 dp ring in the background colour (the notch look)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -AccRaisedRing)
                    .size(AccRaisedCircleSize + AccRaisedRing * 2)
                    .drawBehind { drawCircle(color = scheme.background) },
            )
            val iconSize: Dp = 30.dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(AccRaisedCircleSize)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .selectable(selected = false, role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(raisedItem.icon),
                    contentDescription = raisedItem.label,
                    modifier = Modifier.size(iconSize),
                    tint = scheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AccBarEntry(item: AccBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tint: Color = if (selected) scheme.primary else scheme.onSurface
    Column(
        modifier = modifier
            .fillMaxSize()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (item.raised) {
            // the icon itself is drawn by the raised circle; keep its place so the label lines up
            Spacer(Modifier.height(28.dp))
        } else {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = tint,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else null,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
