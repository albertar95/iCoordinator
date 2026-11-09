package com.icoordinator.ui.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.icoordinator.R
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.accounting.ReportStats
import com.icoordinator.accounting.TxType
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.ui.components.TbIcon
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

private const val EMPTY_TAB_TEXT = "داده‌ای برای نمایش نیست"
private val WEEKDAY_NAMES = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

private fun tabMoney(amount: Long): String = MoneyFormat.format(amount) + " تومان"

/** part / whole as a percent with one decimal in Persian digits ("۲۵٫۰٪"); "—" when whole is 0. */
private fun tabPercent(part: Long, whole: Long): String {
    if (whole == 0L) return "—"
    val tenths = (part.toDouble() * 1000.0 / whole.toDouble()).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val a = abs(tenths)
    return AccountingFormat.digits("$sign${a / 10}.${a % 10}").replace('.', '٫') + "٪"
}

private fun monthNumber(month: Int): String = AccountingFormat.digits(month.toString())

@Composable
private fun TabEmpty() {
    Text(
        text = EMPTY_TAB_TEXT,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TabScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** Year arrows: under RTL the right-pointing chevron is "previous" (same as the month arrows). */
@Composable
private fun YearSelector(year: Int, onYear: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onYear(year - 1) }) {
            TbIcon(R.drawable.ic_tb_chevron_right, "سال قبل", tint = scheme.onSurface)
        }
        Text(
            text = AccountingFormat.digits(year.toString()),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onYear(year + 1) }) {
            TbIcon(R.drawable.ic_tb_chevron_left, "سال بعد", tint = scheme.onSurface)
        }
    }
}

private class Cell(val text: String, val color: Color? = null)

private const val FIRST_COL_DP = 84
private const val OTHER_COL_DP = 108

/** Table with a fixed-width first column; scrolls sideways when wider than the screen. */
@Composable
private fun ReportTable(header: List<String>, rows: List<List<Cell>>, boldRows: Set<Int> = emptySet()) {
    val scheme = MaterialTheme.colorScheme
    val totalWidth = (FIRST_COL_DP + OTHER_COL_DP * (header.size - 1)).dp
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        TableLine(header.map { Cell(it, scheme.onSurfaceVariant) }, bold = true)
        Spacer(Modifier.width(totalWidth).height(1.dp).background(scheme.outlineVariant))
        rows.forEachIndexed { index, cells -> TableLine(cells, bold = index in boldRows) }
    }
}

@Composable
private fun TableLine(cells: List<Cell>, bold: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell.text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.Bold else null,
                color = cell.color ?: scheme.onSurface,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .width((if (index == 0) FIRST_COL_DP else OTHER_COL_DP).dp)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Tab "ماهانه"
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun MonthlyReportTab(data: ReportsData) {
    val current = remember { AccountingFormat.currentMonth() }
    var year by rememberSaveable { mutableStateOf(current.first) }
    val scheme = MaterialTheme.colorScheme
    val series = remember(data, year) { AccountingMath.monthlySeries(data.accounts, data.txs, year, 12, 12) }
    val active = series.filter { it.income != 0L || it.expense != 0L }
    val totalIncome = series.sumOf { it.income }
    val totalExpense = series.sumOf { it.expense }
    val avgIncome = if (active.isEmpty()) 0L else totalIncome / active.size
    val avgExpense = if (active.isEmpty()) 0L else totalExpense / active.size

    TabScaffold {
        YearSelector(year = year, onYear = { year = it })
        ReportSection("درآمد و هزینه‌ی ماه‌های سال") {
            if (active.isEmpty()) {
                TabEmpty()
            } else {
                BarPairChart(months = series, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LegendDot(txTypeColor(TxType.RECEIVE), "درآمد")
                    Spacer(Modifier.width(24.dp))
                    LegendDot(scheme.error, "هزینه")
                }
            }
        }
        ReportSection("جدول ماهانه") {
            val incomeColor = txTypeColor(TxType.RECEIVE)
            fun line(label: String, income: Long, expense: Long): List<Cell> {
                val net = income - expense
                return listOf(
                    Cell(label),
                    Cell(MoneyFormat.format(income), incomeColor),
                    Cell(MoneyFormat.format(expense), scheme.error),
                    Cell(MoneyFormat.format(net), if (net < 0L) scheme.error else null),
                    Cell(tabPercent(net, income)),
                )
            }
            val rows = series.map { m -> line(PersianCalendar.monthName(m.month), m.income, m.expense) } +
                listOf(
                    line("جمع", totalIncome, totalExpense),
                    line("میانگین ماهانه", avgIncome, avgExpense),
                )
            ReportTable(
                header = listOf("ماه", "درآمد", "هزینه", "خالص", "پس‌انداز"),
                rows = rows,
                boldRows = setOf(series.size, series.size + 1),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Tab "حساب"
// ---------------------------------------------------------------------------------------------------------------

private val BALANCE_KINDS = setOf(
    AccountKind.FUND, AccountKind.BANK, AccountKind.DEBTOR, AccountKind.CREDITOR,
    AccountKind.PERSON, AccountKind.LIABILITY, AccountKind.ASSET,
)

@Composable
internal fun AccountReportTab(data: ReportsData) {
    val scheme = MaterialTheme.colorScheme
    val current = remember { AccountingFormat.currentMonth() }
    var accountId by rememberSaveable { mutableStateOf(-1L) }
    var year by rememberSaveable { mutableStateOf(current.first) }
    var pickerOpen by remember { mutableStateOf(false) }
    val byId = remember(data.accounts) { data.accounts.associateBy { it.id } }
    val account = byId[accountId]
    val kind = account?.let { AccountKind.fromCode(it.kindCode) }

    TabScaffold {
        ReportSection("حساب") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { pickerOpen = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (account != null) AccountingFormat.accountPath(account.id, byId) else "انتخاب حساب",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (account != null) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TbIcon(R.drawable.ic_tb_chevron_right, null, tint = scheme.onSurfaceVariant)
            }
        }
        if (account == null || kind == null) {
            ReportSection("گزارش حساب") { TabEmpty() }
        } else {
            YearSelector(year = year, onYear = { year = it })
            val rows = remember(data, accountId, year) {
                ReportStats.accountMonthly(data.accounts, data.txs, accountId, year)
            }
            // EXPENSE: inflow (debit) = expense, outflow (credit) = refund; INCOME: inflow (debit) = refund, outflow (credit) = income
            val inflowTitle = when (kind) {
                AccountKind.EXPENSE -> "هزینه"
                AccountKind.INCOME -> "برگشت"
                else -> "ورود"
            }
            val outflowTitle = when (kind) {
                AccountKind.EXPENSE -> "برگشت"
                AccountKind.INCOME -> "درآمد"
                else -> "خروج"
            }
            val sign = if (kind == AccountKind.INCOME) -1L else 1L // income accounts: net = income - refund
            val showBalance = kind in BALANCE_KINDS
            ReportSection("گزارش ماهانه‌ی " + account.title) {
                fun cells(label: String, inflow: Long, outflow: Long, net: Long, balance: Long?): List<Cell> {
                    val list = mutableListOf(
                        Cell(label),
                        Cell(MoneyFormat.format(inflow)),
                        Cell(MoneyFormat.format(outflow)),
                        Cell(MoneyFormat.format(net * sign), if (net * sign < 0L) scheme.error else null),
                    )
                    if (showBalance) {
                        list.add(Cell(MoneyFormat.format(balance ?: 0L), if ((balance ?: 0L) < 0L) scheme.error else null))
                    }
                    return list
                }
                val body = rows.map { r -> cells(PersianCalendar.monthName(r.month), r.inflow, r.outflow, r.net, r.closingBalance) } +
                    listOf(
                        cells(
                            "جمع",
                            rows.sumOf { it.inflow },
                            rows.sumOf { it.outflow },
                            rows.sumOf { it.net },
                            rows.lastOrNull()?.closingBalance,
                        ),
                    )
                ReportTable(
                    header = listOf("ماه", inflowTitle, outflowTitle, "خالص") + (if (showBalance) listOf("مانده‌ی پایان ماه") else emptyList()),
                    rows = body,
                    boldRows = setOf(rows.size),
                )
            }
            ReportSection("خالص هر ماه") {
                if (rows.all { it.net == 0L }) {
                    TabEmpty()
                } else {
                    HorizontalBars(
                        items = rows.map { r ->
                            BarItem(PersianCalendar.monthName(r.month), r.net * sign, MoneyFormat.format(r.net * sign), scheme.primary)
                        },
                    )
                }
            }
            val trend = remember(data, accountId) {
                ReportStats.categoryTrend(data.accounts, data.txs, accountId, current.first, current.second, 12)
            }
            ReportSection("روند ۱۲ ماه") {
                if (trend.all { it.amount == 0L }) {
                    TabEmpty()
                } else {
                    val average = trend.sumOf { it.amount } / trend.size
                    LineChart(
                        points = trend.map { it.amount },
                        labels = trend.map { monthNumber(it.month) },
                        modifier = Modifier.fillMaxWidth(),
                        average = average,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "میانگین: " + tabMoney(average),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        AccountPickerDialog(
            title = "انتخاب حساب",
            accounts = data.accounts.filter { it.isEnabled },
            allowed = { true },
            onDismiss = { pickerOpen = false },
            onPick = {
                accountId = it.id
                pickerOpen = false
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Tab "مقایسه"
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun CompareReportTab(data: ReportsData) {
    val scheme = MaterialTheme.colorScheme
    val current = remember { AccountingFormat.currentMonth() }
    var monthIndex by rememberSaveable { mutableStateOf(current.first * 12 + current.second - 1) }
    val year = Math.floorDiv(monthIndex, 12)
    val month = Math.floorMod(monthIndex, 12) + 1
    val byId = remember(data.accounts) { data.accounts.associateBy { it.id } }
    val changes = remember(data, monthIndex) {
        ReportStats.monthComparison(data.accounts, data.txs, AccountKind.EXPENSE, year, month)
    }
    val prevIndex = monthIndex - 1
    val prevTitle = AccountingFormat.monthTitle(Math.floorDiv(prevIndex, 12), Math.floorMod(prevIndex, 12) + 1)
    val range = AccountingFormat.monthRange(year, month)
    val top = remember(data, monthIndex) {
        ReportStats.topTransactions(data.accounts, data.txs, AccountKind.EXPENSE, range.first, range.last, 10)
    }
    val today = LocalDate.now().toEpochDay()
    val averages = remember(data, today) {
        ReportStats.weekdayAverages(data.accounts, data.txs, today - 89, today)
    }
    val green = txTypeColor(TxType.RECEIVE)

    TabScaffold {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // under RTL the right-pointing chevron is "previous"
            IconButton(onClick = { monthIndex -= 1 }) {
                TbIcon(R.drawable.ic_tb_chevron_right, "ماه قبل", tint = scheme.onSurface)
            }
            Text(
                text = AccountingFormat.monthTitle(year, month),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { monthIndex += 1 }) {
                TbIcon(R.drawable.ic_tb_chevron_left, "ماه بعد", tint = scheme.onSurface)
            }
        }
        ReportSection("مقایسه‌ی هزینه‌ها با " + prevTitle) {
            if (changes.isEmpty()) {
                TabEmpty()
            } else {
                changes.forEachIndexed { index, c ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = c.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
                            Text(
                                text = "این ماه: " + MoneyFormat.format(c.current),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                text = "ماه قبل: " + MoneyFormat.format(c.previous),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        val up = c.current > c.previous
                        val down = c.current < c.previous
                        val color = if (down) green else if (up) scheme.error else scheme.onSurfaceVariant
                        if (up || down) {
                            TbIcon(
                                icon = if (up) R.drawable.ic_tb_arrow_up else R.drawable.ic_tb_arrow_down,
                                contentDescription = if (up) "افزایش" else "کاهش",
                                size = 18.dp,
                                tint = color,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (c.previous == 0L) "جدید" else tabPercent(abs(c.current - c.previous), abs(c.previous)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                        )
                    }
                }
            }
        }
        ReportSection("میانگین هزینه در روزهای هفته") {
            Text(
                text = "۹۰ روز اخیر",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (averages.all { it == 0L }) {
                TabEmpty()
            } else {
                HorizontalBars(
                    items = averages.mapIndexed { i, v ->
                        BarItem(WEEKDAY_NAMES[i], v, tabMoney(v), scheme.primary)
                    },
                )
            }
        }
        ReportSection("بزرگ‌ترین هزینه‌ها") {
            if (top.isEmpty()) {
                TabEmpty()
            } else {
                top.forEachIndexed { index, t ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AccountingFormat.accountPath(t.debitAccountId, byId).ifEmpty { "نامشخص" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface,
                            )
                            Text(
                                text = AccountingFormat.digits(t.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = tabMoney(t.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Tab "دارایی و وام"
// ---------------------------------------------------------------------------------------------------------------

@Composable
internal fun WealthReportTab(data: ReportsData) {
    val scheme = MaterialTheme.colorScheme
    val current = remember { AccountingFormat.currentMonth() }
    val today = LocalDate.now().toEpochDay()
    val worth = remember(data, today) { ReportStats.netWorth(data.accounts, data.txs, today) }
    val worthTrend = remember(data) { ReportStats.netWorthTrend(data.accounts, data.txs, current.first, current.second, 12) }
    val cash = remember(data) { ReportStats.cashTrend(data.accounts, data.txs, current.first, current.second, 12) }
    val forecast = remember(data) { ReportStats.installmentForecast(data.installments, current.first, current.second, 12) }
    val remaining = data.installments.filter { !it.isPaid }.sumOf { it.amount }
    val overdue = ReportStats.overdueTotal(data.installments, today)
    val green = txTypeColor(TxType.RECEIVE)

    TabScaffold {
        ReportSection("خالص دارایی امروز") {
            WealthLine("دارایی‌ها", tabMoney(worth.assets), green)
            Spacer(Modifier.height(8.dp))
            WealthLine("بدهی‌ها", tabMoney(worth.liabilities), scheme.error)
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
            Spacer(Modifier.height(8.dp))
            WealthLine("خالص", tabMoney(worth.net), if (worth.net < 0L) scheme.error else green)
        }
        ReportSection("روند خالص دارایی (۱۲ ماه)") {
            if (worthTrend.all { it.amount == 0L }) {
                TabEmpty()
            } else {
                LineChart(
                    points = worthTrend.map { it.amount },
                    labels = worthTrend.map { monthNumber(it.month) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ReportSection("روند موجودی صندوق و بانک (۱۲ ماه)") {
            if (cash.all { it.amount == 0L }) {
                TabEmpty()
            } else {
                LineChart(
                    points = cash.map { it.amount },
                    labels = cash.map { monthNumber(it.month) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ReportSection("اقساط ۱۲ ماه آینده") {
            if (forecast.all { it.amount == 0L }) {
                TabEmpty()
            } else {
                HorizontalBars(
                    items = forecast.map {
                        BarItem(AccountingFormat.monthTitle(it.year, it.month), it.amount, tabMoney(it.amount), scheme.tertiary)
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            WealthLine("کل بدهی باقیمانده", tabMoney(remaining), scheme.onSurface)
            if (overdue > 0L) {
                Spacer(Modifier.height(8.dp))
                WealthLine("اقساط معوق", tabMoney(overdue), scheme.error)
            }
        }
    }
}

@Composable
private fun WealthLine(label: String, value: String, color: Color) {
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
