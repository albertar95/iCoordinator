package com.icoordinator.ui.accounting

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.accounting.AccountingBackup
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.CategoryTotal
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.data.BackupManager
import com.icoordinator.security.LockState
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TbIcon
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DashboardScreen(
    onExit: () -> Unit,
    onOpenTransactions: () -> Unit,
    onAdd: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val vm: AccountingViewModel = viewModel(key = "acc_dashboard") {
        AccountingViewModel(ICoordinatorApp.instance.accountingRepository, ICoordinatorApp.instance)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val scope = rememberCoroutineScope()
    val backup = remember { AccountingBackup(ICoordinatorApp.instance) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        LockState.skipNextLock = false
        if (uri != null) {
            scope.launch {
                backupBusy = true
                val error = backup.export(uri)
                backupBusy = false
                snackbar.showSnackbar(error ?: "پشتیبان ذخیره شد")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        LockState.skipNextLock = false
        if (uri != null) {
            scope.launch {
                backupBusy = true
                // L2: the restore swaps files and closes the database; it must not be cut off half-way
                val error = withContext(NonCancellable) { backup.restore(uri) }
                if (error == null || backup.needsRestart) {
                    withContext(NonCancellable) {
                        if (error != null) {
                            launch { snackbar.showSnackbar(error) }
                            delay(1500)
                        }
                        restartApp(activity)
                    }
                    backupBusy = false
                } else {
                    backupBusy = false
                    snackbar.showSnackbar(error)
                }
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        LockState.skipNextLock = false
        if (uri != null) vm.onFilePicked(uri)
    }
    val startImport: () -> Unit = {
        LockState.skipNextLock = true
        launcher.launch(arrayOf("*/*"))
    }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbar.showSnackbar(text)
            vm.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PageHeader(
                    title = "حسابداری شخصی",
                    onBack = onExit,
                    actionIcon = R.drawable.ic_tb_category,
                    actionDescription = "گزینه‌ها",
                    onAction = { menuOpen = true },
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("مدیریت حساب‌ها") },
                            onClick = {
                                menuOpen = false
                                onOpenAccounts()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("وارد کردن از پارمیس") },
                            onClick = {
                                menuOpen = false
                                startImport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("پشتیبان‌گیری حسابداری") },
                            enabled = !backupBusy,
                            onClick = {
                                menuOpen = false
                                LockState.skipNextLock = true
                                exportLauncher.launch(backup.suggestedFileName())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("بازیابی پشتیبان حسابداری") },
                            enabled = !backupBusy,
                            onClick = {
                                menuOpen = false
                                confirmRestore = true
                            },
                        )
                    }
                }
            }
            if (busy || backupBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            when {
                !state.loaded -> Unit
                !state.hasAccounts -> EmptyState(
                    icon = R.drawable.ic_tb_wallet,
                    title = "هنوز حسابی ندارید",
                    message = "پشتیبان پارمیس را وارد کنید یا با حساب‌های پایه شروع کنید",
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(
                                text = "وارد کردن از پارمیس",
                                onClick = startImport,
                                icon = R.drawable.ic_tb_file_import,
                                enabled = !busy,
                            )
                            PillButton(
                                text = "شروع با حساب‌های پایه",
                                onClick = vm::createStarterAccounts,
                                icon = R.drawable.ic_tb_plus,
                                outlined = true,
                                enabled = !busy,
                            )
                        }
                    },
                )
                else -> DashboardContent(
                    state = state,
                    onPrevMonth = { vm.shiftMonth(-1) },
                    onNextMonth = { vm.shiftMonth(1) },
                    onOpenTransactions = onOpenTransactions,
                )
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            text = { Text("همه‌ی داده‌های حسابداری با فایل پشتیبان جایگزین می‌شود و برنامه دوباره باز می‌شود.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        LockState.skipNextLock = true
                        restoreLauncher.launch(arrayOf("*/*"))
                    },
                ) { Text("ادامه", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("انصراف") } },
        )
    }

    val p = pending
    if (p != null) {
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("وارد کردن از پارمیس") },
            text = {
                val r = p.result
                val lines = buildList {
                    add(
                        "حساب: ${num(r.accounts.size)}، تراکنش: ${num(r.transactions.size)}، " +
                            "وام: ${num(r.loans.size)}، قسط: ${num(r.installments.size)}",
                    )
                    if (r.warnings.isNotEmpty()) add("تعداد هشدارها: ${num(r.warnings.size)}")
                    if (p.replacesExisting) add("همه‌ی داده‌های فعلی حسابداری جایگزین می‌شود.")
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmImport) { Text("وارد کن") } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("انصراف") } },
        )
    }
}

private fun findActivity(context: Context): Activity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Restarts the app via the activity; without an activity it launches the main intent from the application context (as BackupManager.restartApp does). */
private fun restartApp(activity: Activity?) {
    if (activity != null) {
        BackupManager.restartApp(activity)
        return
    }
    val app = ICoordinatorApp.instance
    val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(intent)
    }
    Process.killProcess(Process.myPid())
}

private fun num(n: Int): String = AccountingFormat.digits(n.toString())

private fun money(amount: Long): String = MoneyFormat.format(amount) + " تومان"

/** Percent of total with one decimal in Persian digits, e.g. "۲۵٫۰٪". Empty when total <= 0. */
private fun percentText(amount: Long, total: Long): String {
    if (total <= 0L) return ""
    val tenths = (amount.toDouble() * 1000.0 / total.toDouble()).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val a = abs(tenths)
    return AccountingFormat.digits("$sign${a / 10}.${a % 10}").replace('.', '٫') + "٪"
}

@Composable
private fun DashboardContent(
    state: DashboardState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenTransactions: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "month") {
            MonthCard(state = state, onPrev = onPrevMonth, onNext = onNextMonth)
        }
        item(key = "funds") {
            FundsSection(state = state, onClick = onOpenTransactions)
        }
        item(key = "expense") {
            CategoryPanel(
                title = "هزینه‌های این ماه",
                categories = state.expenseCategories,
                saveKey = "acc_panel_expense",
            )
        }
        item(key = "income") {
            CategoryPanel(
                title = "درآمدهای این ماه",
                categories = state.incomeCategories,
                saveKey = "acc_panel_income",
            )
        }
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant, shape),
    ) { content() }
}

@Composable
private fun MonthCard(state: DashboardState, onPrev: () -> Unit, onNext: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    CardBox {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // under RTL the right-pointing chevron is "previous"
                IconButton(onClick = onPrev) {
                    TbIcon(R.drawable.ic_tb_chevron_right, "ماه قبل", tint = scheme.onSurface)
                }
                Text(
                    text = AccountingFormat.monthTitle(state.year, state.month),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onNext) {
                    TbIcon(R.drawable.ic_tb_chevron_left, "ماه بعد", tint = scheme.onSurface)
                }
            }
            Spacer(Modifier.height(8.dp))
            AmountLine("درآمد", money(state.income), txTypeColor(com.icoordinator.accounting.TxType.RECEIVE))
            Spacer(Modifier.height(8.dp))
            AmountLine("هزینه", money(state.expense), scheme.error)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = scheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            AmountLine("مانده", money(state.income - state.expense), scheme.onSurface)
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun FundsSection(state: DashboardState, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionLabel("صندوق‌ها")
        Spacer(Modifier.height(8.dp))
        CardBox(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.funds.isEmpty()) {
                    Text(
                        text = "صندوقی ثبت نشده",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    state.funds.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconCircle(icon = accountKindIcon(row.kind), size = 40.dp, iconSize = 22.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = row.account.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = money(row.balance),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (row.balance < 0) scheme.error else scheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = scheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "جمع موجودی",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = money(state.fundsTotal),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.fundsTotal < 0) scheme.error else scheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPanel(title: String, categories: List<CategoryTotal>, saveKey: String) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(saveKey) { mutableStateOf(false) }
    val total = categories.sumOf { it.amount }
    CardBox {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TbIcon(
                    icon = R.drawable.ic_tb_chevron_left,
                    contentDescription = if (expanded) "بستن" else "باز کردن",
                    modifier = Modifier.rotate(if (expanded) 90f else 0f),
                    tint = scheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    if (categories.isEmpty()) {
                        Text(
                            text = "موردی ثبت نشده",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        categories.forEach { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = c.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = scheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = money(c.amount),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = scheme.onSurface,
                                    )
                                    val percent = percentText(c.amount, total)
                                    if (percent.isNotEmpty()) {
                                        Text(
                                            text = percent,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
