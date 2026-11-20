package com.icoordinator.ui.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.accounting.AccountEntity
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.AccountingRepository
import com.icoordinator.accounting.CategoryTotal
import com.icoordinator.accounting.DueTotals
import com.icoordinator.accounting.InstallmentEntity
import com.icoordinator.accounting.InstallmentStats
import com.icoordinator.accounting.LoanEntity
import com.icoordinator.accounting.MonthTotals
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.accounting.ReportMath
import com.icoordinator.accounting.TransactionEntity
import com.icoordinator.accounting.TxType
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TbIcon
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val MODE_MONTH = 0
private const val MODE_YEAR = 1
private const val MODE_LAST12 = 2

private const val CHIP_THIS_MONTH = 0
private const val CHIP_PREV_MONTH = 1
private const val CHIP_YEAR = 2
private const val CHIP_LAST12 = 3

private const val EMPTY_TEXT = "داده‌ای برای این بازه نیست"

private data class ReportPeriod(val mode: Int, val year: Int, val month: Int)

data class ReportsState(
    val loaded: Boolean = false,
    val mode: Int = MODE_MONTH,
    val year: Int = 0,
    val month: Int = 0,
    val selectedChip: Int = CHIP_THIS_MONTH,
    val rangeTitle: String = "",
    val income: Long = 0,
    val expense: Long = 0,
    /** Installments due in the selected range (paid / unpaid). */
    val due: DueTotals = DueTotals(0L, 0L),
    /** Average expense per day of the range (days up to today), or null when the range has not started. */
    val avgDailyExpense: Long? = null,
    val expenseSlices: List<CategoryTotal> = emptyList(),
    val incomeSlices: List<CategoryTotal> = emptyList(),
    val series: List<MonthTotals> = emptyList(),
    val topAccounts: List<CategoryTotal> = emptyList(),
    /** accountId, title, amount = current balance. */
    val fundBalances: List<CategoryTotal> = emptyList(),
)

/** Raw data for the report tabs other than the summary. */
data class ReportsData(
    val loaded: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    val txs: List<TransactionEntity> = emptyList(),
    val installments: List<InstallmentEntity> = emptyList(),
    val loans: List<LoanEntity> = emptyList(),
)

class ReportsViewModel(repo: AccountingRepository) : ViewModel() {

    val data: StateFlow<ReportsData> =
        combine(repo.observeAccounts(), repo.observeTransactions(), repo.observeInstallments(), repo.observeLoans()) { accounts, txs, installments, loans ->
            ReportsData(loaded = true, accounts = accounts, txs = txs, installments = installments, loans = loans)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsData())

    private val period = MutableStateFlow(
        AccountingFormat.currentMonth().let { ReportPeriod(MODE_MONTH, it.first, it.second) },
    )

    val state: StateFlow<ReportsState> =
        combine(repo.observeAccounts(), repo.observeTransactions(), period, repo.observeInstallments()) { accounts, txs, p, installments ->
            compute(accounts, txs, installments, p)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsState())

    fun selectThisMonth() {
        val (y, m) = AccountingFormat.currentMonth()
        period.value = ReportPeriod(MODE_MONTH, y, m)
    }

    fun selectPrevMonth() {
        val (y, m) = AccountingFormat.currentMonth()
        period.value = shifted(y, m, -1)
    }

    fun selectYear() {
        val (y, m) = AccountingFormat.currentMonth()
        period.value = ReportPeriod(MODE_YEAR, y, m)
    }

    fun selectLast12() {
        val (y, m) = AccountingFormat.currentMonth()
        period.value = ReportPeriod(MODE_LAST12, y, m)
    }

    /** Month arrows: only meaningful while a single month is selected. */
    fun shiftMonth(delta: Int) {
        val p = period.value
        if (p.mode != MODE_MONTH) return
        period.value = shifted(p.year, p.month, delta)
    }

    private fun shifted(year: Int, month: Int, delta: Int): ReportPeriod {
        val index = year * 12 + (month - 1) + delta
        return ReportPeriod(MODE_MONTH, Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)
    }

    private fun compute(
        accounts: List<com.icoordinator.accounting.AccountEntity>,
        txs: List<com.icoordinator.accounting.TransactionEntity>,
        installments: List<InstallmentEntity>,
        p: ReportPeriod,
    ): ReportsState {
        val today = LocalDate.now().toEpochDay()
        val (cy, cm) = AccountingFormat.currentMonth()
        val from: Long
        val to: Long
        val title: String
        when (p.mode) {
            MODE_YEAR -> {
                from = PersianCalendar.monthStart(cy, 1).toEpochDay()
                to = AccountingFormat.monthRange(cy, 12).last
                title = "امسال (سال " + AccountingFormat.digits(cy.toString()) + ")"
            }
            MODE_LAST12 -> {
                val index = cy * 12 + (cm - 1) - 11
                from = PersianCalendar.monthStart(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1).toEpochDay()
                to = AccountingFormat.monthRange(cy, cm).last
                title = "۱۲ ماه اخیر"
            }
            else -> {
                val range = AccountingFormat.monthRange(p.year, p.month)
                from = range.first
                to = range.last
                title = AccountingFormat.monthTitle(p.year, p.month)
            }
        }
        val chip = when {
            p.mode == MODE_YEAR -> CHIP_YEAR
            p.mode == MODE_LAST12 -> CHIP_LAST12
            p.year == cy && p.month == cm -> CHIP_THIS_MONTH
            p == shifted(cy, cm, -1) -> CHIP_PREV_MONTH
            else -> -1
        }
        val totals = AccountingMath.periodTotals(accounts, txs, from, to)
        val days = minOf(to, today) - from + 1
        val avg = if (days > 0L) totals.expense / days else null

        val expenseSlices = ReportMath.topWithOthers(
            AccountingMath.categoryTotals(accounts, txs, AccountKind.EXPENSE, from, to),
            6,
        )
        val incomeSlices = ReportMath.topWithOthers(
            AccountingMath.categoryTotals(accounts, txs, AccountKind.INCOME, from, to),
            6,
        )
        val series = AccountingMath.monthlySeries(accounts, txs, cy, cm, 12)
        val topAccounts = ReportMath.leafTotals(accounts, txs, AccountKind.EXPENSE, from, to).take(10)

        val balances = AccountingMath.balances(accounts, txs)
        val kinds = AccountingMath.kindsById(accounts)
        val parents = accounts.mapNotNull { it.parentId }.toSet()
        val funds = accounts
            .filter { a -> isListedFund(a, kinds[a.id], a.id in parents, balances[a.id] ?: 0L) }
            .map { a -> CategoryTotal(a.id, a.title, balances[a.id] ?: 0L) }
            .sortedWith(compareByDescending<CategoryTotal> { it.amount }.thenBy { it.accountId })

        return ReportsState(
            loaded = true,
            mode = p.mode,
            year = p.year,
            month = p.month,
            selectedChip = chip,
            rangeTitle = title,
            income = totals.income,
            expense = totals.expense,
            due = InstallmentStats.dueInRange(installments, from, to),
            avgDailyExpense = avg,
            expenseSlices = expenseSlices,
            incomeSlices = incomeSlices,
            series = series,
            topAccounts = topAccounts,
            fundBalances = funds,
        )
    }
}

private fun money(amount: Long): String = MoneyFormat.format(amount) + " تومان"

/** Funds rule shared with ReportsScreen (M1): a non-root FUND/BANK account that has no children or whose own balance is not 0. Disabled accounts stay listed. */
private fun isListedFund(account: AccountEntity, kind: AccountKind?, hasChildren: Boolean, ownBalance: Long): Boolean =
    account.parentId != null && (kind == AccountKind.FUND || kind == AccountKind.BANK) && (!hasChildren || ownBalance != 0L)

/** Percent with one decimal in Persian digits, e.g. 70.588 -> "۷۰٫۶٪". */
private fun percentLabel(percent: Double): String {
    val tenths = (percent * 10.0).roundToLong()
    val sign = if (tenths < 0) "-" else ""
    val a = abs(tenths)
    return AccountingFormat.digits("$sign${a / 10}.${a % 10}").replace('.', '٫') + "٪"
}

@Composable
fun ReportsScreen(onOpenLoans: () -> Unit = {}) {
    val vm: ReportsViewModel = viewModel(key = "acc_reports") {
        ReportsViewModel(ICoordinatorApp.instance.accountingRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val scheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        PageHeader(title = "گزارش‌ها")
        ReportTabRow(selected = tab, onSelect = { tab = it })
        if (state.loaded && data.loaded && tab != 0) {
            when (tab) {
                1 -> MonthlyReportTab(data)
                2 -> AccountReportTab(data)
                3 -> CompareReportTab(data)
                else -> WealthReportTab(data, onOpenLoans)
            }
        }
        if (state.loaded && tab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "period") {
                    PeriodSelector(
                        state = state,
                        onThisMonth = vm::selectThisMonth,
                        onPrevMonth = vm::selectPrevMonth,
                        onYear = vm::selectYear,
                        onLast12 = vm::selectLast12,
                        onShift = vm::shiftMonth,
                    )
                }
                item(key = "summary") { SummarySection(state) }
                item(key = "expense") {
                    ReportSection("هزینه به تفکیک دسته") { CategoryPie(state.expenseSlices) }
                }
                item(key = "income") {
                    ReportSection("درآمد به تفکیک دسته") { CategoryPie(state.incomeSlices) }
                }
                item(key = "series") {
                    ReportSection("درآمد و هزینه‌ی ۱۲ ماه اخیر") { SeriesChart(state.series) }
                }
                item(key = "top") {
                    ReportSection("پرهزینه‌ترین حساب‌ها") {
                        if (state.topAccounts.isEmpty()) {
                            EmptyHint()
                        } else {
                            HorizontalBars(
                                items = state.topAccounts.map {
                                    BarItem(it.title, it.amount, money(it.amount), scheme.primary)
                                },
                            )
                        }
                    }
                }
                item(key = "funds") {
                    ReportSection("موجودی صندوق‌ها") {
                        if (state.fundBalances.isEmpty()) {
                            EmptyHint()
                        } else {
                            HorizontalBars(
                                items = state.fundBalances.map {
                                    BarItem(it.title, it.amount, money(it.amount), scheme.tertiary)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val REPORT_TABS = listOf("خلاصه", "ماهانه", "حساب", "مقایسه", "دارایی و وام")

/** Scrollable chip row that switches between the report tabs. */
@Composable
private fun ReportTabRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        REPORT_TABS.forEachIndexed { index, title ->
            PeriodChip(title, selected == index) { onSelect(index) }
        }
    }
}

@Composable
private fun PeriodSelector(
    state: ReportsState,
    onThisMonth: () -> Unit,
    onPrevMonth: () -> Unit,
    onYear: () -> Unit,
    onLast12: () -> Unit,
    onShift: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodChip("این ماه", state.selectedChip == CHIP_THIS_MONTH, onThisMonth)
            PeriodChip("ماه قبل", state.selectedChip == CHIP_PREV_MONTH, onPrevMonth)
            PeriodChip("امسال", state.selectedChip == CHIP_YEAR, onYear)
            PeriodChip("۱۲ ماه اخیر", state.selectedChip == CHIP_LAST12, onLast12)
        }
        Spacer(Modifier.height(8.dp))
        if (state.mode == MODE_MONTH) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // like the calendar: previous on the right (start side under RTL), next on the left
                IconButton(onClick = { onShift(-1) }) {
                    TbIcon(R.drawable.ic_tb_chevron_left, "ماه قبل", tint = scheme.onSurface)
                }
                Text(
                    text = state.rangeTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = { onShift(1) }) {
                    TbIcon(R.drawable.ic_tb_chevron_right, "ماه بعد", tint = scheme.onSurface)
                }
            }
        } else {
            Text(
                text = state.rangeTitle,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun PeriodChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surfaceContainer, shape)
            .then(if (selected) Modifier else Modifier.border(1.dp, scheme.outlineVariant, shape))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ReportSection(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Column {
        SectionLabel(title)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surfaceContainer, shape)
                .border(1.dp, scheme.outlineVariant, shape),
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun EmptyHint() {
    Text(
        text = EMPTY_TEXT,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummarySection(state: ReportsState) {
    val scheme = MaterialTheme.colorScheme
    val net = state.income - state.expense
    ReportSection("خلاصه") {
        SummaryLine("درآمد", money(state.income), txTypeColor(TxType.RECEIVE))
        Spacer(Modifier.height(8.dp))
        SummaryLine("هزینه", money(state.expense), scheme.error)
        Spacer(Modifier.height(8.dp))
        SummaryLine("اقساط", money(state.due.total), scheme.error)
        Text(
            text = "پرداخت‌شده " + money(state.due.paid) + " - مانده " + money(state.due.unpaid),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = scheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        SummaryLine(
            "خالص",
            money(net),
            if (net < 0L) scheme.error else txTypeColor(TxType.RECEIVE),
        )
        Spacer(Modifier.height(8.dp))
        SummaryLine(
            "مانده بعد از اقساط",
            money(net - state.due.total),
            if (net - state.due.total < 0L) scheme.error else txTypeColor(TxType.RECEIVE),
        )
        Spacer(Modifier.height(8.dp))
        SummaryLine(
            "میانگین هزینه‌ی روزانه",
            state.avgDailyExpense?.let { money(it) } ?: "—",
            scheme.onSurface,
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: Color) {
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

/** Six fixed-order slice colours; the merged "سایر موارد" slice (accountId -1) uses the outline colour. */
@Composable
private fun sliceColors(slices: List<CategoryTotal>): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primary,
        scheme.tertiary,
        scheme.secondary,
        scheme.error,
        Color(0xFF7E57C2),
        Color(0xFF26A69A),
    )
    return slices.mapIndexed { index, slice ->
        if (slice.accountId == -1L) scheme.outline else palette[index % palette.size]
    }
}

@Composable
private fun CategoryPie(slices: List<CategoryTotal>) {
    val scheme = MaterialTheme.colorScheme
    if (slices.isEmpty()) {
        EmptyHint()
        return
    }
    val colors = sliceColors(slices)
    val shares = ReportMath.shares(slices)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PieChart(
            values = slices.map { it.amount },
            colors = colors,
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(16.dp))
        slices.forEachIndexed { index, slice ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(colors[index]))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slice.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = money(slice.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = percentLabel(shares[index]),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun SeriesChart(series: List<MonthTotals>) {
    val scheme = MaterialTheme.colorScheme
    if (series.none { it.income != 0L || it.expense != 0L }) {
        EmptyHint()
        return
    }
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

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
