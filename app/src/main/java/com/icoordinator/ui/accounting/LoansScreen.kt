package com.icoordinator.ui.accounting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.icoordinator.accounting.InstallmentEntity
import com.icoordinator.accounting.LoanEntity
import com.icoordinator.accounting.LoanPlanner
import com.icoordinator.accounting.LoanSummary
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.accounting.TxType
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.ShamsiDatePickerDialog
import com.icoordinator.ui.components.TbIcon
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A loan with its installments (sorted by seq) and derived data. */
data class LoanRow(
    val loan: LoanEntity,
    val installments: List<InstallmentEntity>,
    val summary: LoanSummary,
    val next: InstallmentEntity?,
)

data class LoansState(
    val loaded: Boolean = false,
    val rows: List<LoanRow> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)

/** Validated-by-the-form input of a brand-new loan. Amounts in toman, dates Jalali "yyyy/MM/dd". */
data class NewLoanInput(
    val title: String,
    val principal: Long,
    val installmentAmount: Long,
    val count: Int,
    val firstDueDate: String,
    val receiveDate: String,
    val fundId: Long?,
    val note: String,
    val recordReceipt: Boolean,
)

class LoansViewModel(private val repo: AccountingRepository) : ViewModel() {

    val state: StateFlow<LoansState> =
        combine(repo.observeLoans(), repo.observeInstallments(), repo.observeAccounts()) { loans, installments, accounts ->
            val nextDue = LoanPlanner.nextDuePerLoan(loans, installments)
            val byLoan = installments.groupBy { it.loanId }
            val rows = loans.map { loan ->
                val list = (byLoan[loan.id] ?: emptyList()).sortedWith(compareBy({ it.seq }, { it.id }))
                LoanRow(loan = loan, installments = list, summary = AccountingMath.loanSummary(list), next = nextDue[loan.id])
            }
            LoansState(loaded = true, rows = rows, accounts = accounts)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoansState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    fun consumeMessage() {
        _message.value = null
    }

    fun clearFormError() {
        _formError.value = null
    }

    fun pay(installmentId: Long, fromAccountId: Long, date: String) {
        launchGuarded("پرداخت قسط انجام نشد") {
            repo.payInstallment(installmentId, fromAccountId, date, nowTime())
        }
    }

    fun unpay(installmentId: Long) {
        launchGuarded("برگرداندن پرداخت انجام نشد") { repo.unpayInstallment(installmentId) }
    }

    fun delete(loanId: Long) {
        launchGuarded("حذف وام انجام نشد") { repo.deleteLoan(loanId) }
    }

    /** Edit form: only title and note change. Never calls saveLoan (it would recreate the installments, F23). */
    fun updateLoan(loan: LoanEntity, title: String, note: String, onDone: () -> Unit) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _formError.value = "عنوان وام را وارد کنید"
            return
        }
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _formError.value = null
            try {
                repo.updateLoan(loan.copy(title = cleanTitle, note = note.trim()))
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formError.value = e.message ?: "ذخیره انجام نشد"
            } finally {
                _saving.value = false
            }
        }
    }

    /** Creates a brand-new loan with one atomic repository call (accounts, loan, installments and transactions). */
    fun createLoan(input: NewLoanInput, onDone: () -> Unit) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            _formError.value = null
            try {
                val fundId = if (input.recordReceipt) input.fundId else null
                if (input.recordReceipt) require(fundId != null) { "صندوق دریافت‌کننده را انتخاب کنید" }
                repo.createLoan(
                    title = input.title,
                    principal = input.principal,
                    installmentAmount = input.installmentAmount,
                    count = input.count,
                    firstDueDate = input.firstDueDate,
                    receiveDate = input.receiveDate,
                    time = nowTime(),
                    fundAccountId = fundId,
                    note = input.note,
                )
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _formError.value = e.message ?: "ذخیره‌ی وام ممکن نشد"
            } catch (e: Exception) {
                _formError.value = "ذخیره‌ی وام ممکن نشد"
            } finally {
                _saving.value = false
            }
        }
    }

    private fun launchGuarded(failText: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: failText
            }
        }
    }

    private fun nowTime(): String {
        val t = LocalTime.now()
        return String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)
    }
}

private sealed interface LoanEntry {
    data class DateHeader(val key: String, val title: String) : LoanEntry
    data class ClosedLabel(val key: String) : LoanEntry
    data class Card(val row: LoanRow) : LoanEntry
}

private fun money(amount: Long): String = MoneyFormat.format(amount) + " تومان"

private fun num(n: Int): String = AccountingFormat.digits(n.toString())

/** Day of month of a Jalali "yyyy/MM/dd" date as an Int, or null. */
private fun dayOf(date: String): Int? =
    AccountingMath.normalizeDate(date)?.split('/')?.getOrNull(2)?.toIntOrNull()

private const val NO_DAY = 99

/** Day of month of the loan's installment: next unpaid due date, else the first due date, else [NO_DAY]. */
private fun installmentDay(row: LoanRow): Int {
    val fromNext = row.next?.let { dayOf(it.dueDate) }
    return fromNext ?: dayOf(row.loan.firstDueDate) ?: NO_DAY
}

@Composable
fun LoansScreen() {
    val vm: LoansViewModel = viewModel(key = "acc_loans") {
        LoansViewModel(ICoordinatorApp.instance.accountingRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val formError by vm.formError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var selectedId by rememberSaveable { mutableStateOf(-1L) }
    // 0 = no form, 1 = new loan, 2 = edit the selected loan
    var formMode by rememberSaveable { mutableStateOf(0) }
    val selectedRow = state.rows.firstOrNull { it.loan.id == selectedId }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbar.showSnackbar(text)
            vm.consumeMessage()
        }
    }
    // the selected loan vanished (deleted): back to the list
    LaunchedEffect(state.loaded, selectedRow == null) {
        if (state.loaded && selectedId != -1L && selectedRow == null) {
            selectedId = -1L
            formMode = 0
        }
    }

    BackHandler(enabled = formMode != 0 || selectedRow != null) {
        if (formMode != 0) formMode = 0 else selectedId = -1L
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            formMode == 1 || (formMode == 2 && selectedRow != null) -> LoanFormScreen(
                row = if (formMode == 2) selectedRow else null,
                accounts = state.accounts,
                saving = saving,
                error = formError,
                onClearError = vm::clearFormError,
                onCreate = { input -> vm.createLoan(input) { formMode = 0 } },
                onSaveEdit = { loan, title, note -> vm.updateLoan(loan, title, note) { formMode = 0 } },
                onBack = { formMode = 0 },
            )
            selectedRow != null -> LoanDetail(
                row = selectedRow,
                accounts = state.accounts,
                onBack = { selectedId = -1L },
                onEdit = { formMode = 2 },
                onPay = vm::pay,
                onUnpay = vm::unpay,
                onDelete = { vm.delete(selectedRow.loan.id) },
            )
            else -> LoanList(
                state = state,
                onAdd = { formMode = 1 },
                onOpen = { selectedId = it },
            )
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

@Composable
private fun LoanList(state: LoansState, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val entries = remember(state.rows) {
        val open = state.rows.filter { !it.loan.isClosed }
        val closed = state.rows.filter { it.loan.isClosed }.sortedWith(compareBy<LoanRow>({ it.loan.title }, { it.loan.id }))
        val sortedOpen = open.sortedWith(
            compareBy<LoanRow>({ installmentDay(it) }, { it.loan.title }, { it.loan.id }),
        )
        buildList<LoanEntry> {
            for ((day, group) in sortedOpen.groupBy { installmentDay(it) }) {
                val title = if (day == NO_DAY) "بدون قسط باز" else "روز " + num(day) + " هر ماه"
                add(LoanEntry.DateHeader(day.toString(), title))
                group.forEach { add(LoanEntry.Card(it)) }
            }
            if (closed.isNotEmpty()) {
                add(LoanEntry.ClosedLabel("closed"))
                closed.forEach { add(LoanEntry.Card(it)) }
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = "وام‌ها",
            actionIcon = R.drawable.ic_tb_plus,
            actionDescription = "وام جدید",
            onAction = onAdd,
        )
        if (state.loaded && entries.isEmpty()) {
            EmptyState(
                icon = R.drawable.ic_tb_credit_card,
                title = "وامی ثبت نشده",
                message = "با دکمه‌ی + وام جدید اضافه کنید",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    entries,
                    key = { entry ->
                        when (entry) {
                            is LoanEntry.DateHeader -> "d" + entry.key
                            is LoanEntry.ClosedLabel -> "s" + entry.key
                            is LoanEntry.Card -> "c" + entry.row.loan.id
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is LoanEntry.DateHeader -> Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
                        )
                        is LoanEntry.ClosedLabel -> SectionLabel("تسویه‌شده")
                        is LoanEntry.Card -> LoanCard(row = entry.row, onOpen = { onOpen(entry.row.loan.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanCard(row: LoanRow, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val dueText = when {
        row.loan.isClosed -> "تسویه شد"
        row.next == null -> "قسطی ثبت نشده"
        else -> "موعد قسط: " + AccountingFormat.digits((dayOf(row.next.dueDate) ?: 0).toString()) + " ام هر ماه"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon = R.drawable.ic_tb_coin, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.loan.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = "اقساط باقیمانده : " + num(row.summary.remainingCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = money(row.loan.principal),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
        Text(
            text = "باقیمانده : " + MoneyFormat.format(row.summary.remainingAmount),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dueText,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) {
                Text("مشاهده", color = scheme.primary)
            }
        }
    }
}

@Composable
private fun LoanDetail(
    row: LoanRow,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPay: (installmentId: Long, fromAccountId: Long, date: String) -> Unit,
    onUnpay: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now().toEpochDay()
    var payTarget by remember { mutableStateOf<InstallmentEntity?>(null) }
    var payFundId by remember { mutableStateOf<Long?>(null) }
    var undoTarget by remember { mutableStateOf<InstallmentEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = row.loan.title,
            onBack = onBack,
            actionIcon = R.drawable.ic_tb_pencil,
            actionDescription = "ویرایش وام",
            onAction = onEdit,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                val s = row.summary
                val shape = RoundedCornerShape(24.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainer, shape)
                        .border(1.dp, scheme.outlineVariant, shape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryLine("مبلغ وام", money(row.loan.principal))
                    SummaryLine("پرداخت‌شده", num(s.paidCount) + " قسط - " + money(s.paidAmount))
                    SummaryLine("باقیمانده", num(s.remainingCount) + " قسط - " + money(s.remainingAmount))
                    SummaryLine(
                        "قسط بعدی",
                        if (row.loan.isClosed || s.nextDue == null) {
                            "تسویه شد"
                        } else {
                            AccountingFormat.dayHeader(s.nextDue.dueDate)
                        },
                    )
                }
            }
            item(key = "label") { SectionLabel("اقساط") }
            items(row.installments, key = { it.id }) { inst ->
                InstallmentRow(
                    installment = inst,
                    overdue = !inst.isPaid && inst.dueEpochDay < today,
                    onPay = {
                        payFundId = null
                        payTarget = inst
                    },
                    onUndo = { undoTarget = inst },
                )
            }
            item(key = "delete") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DeleteIconButton(onClick = { confirmDelete = true }, contentDescription = "حذف وام")
                    Text(
                        text = "تراکنش‌های ثبت‌شده حذف نمی‌شوند",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    val target = payTarget
    if (target != null) {
        val fundId = payFundId
        if (fundId == null) {
            AccountPickerDialog(
                title = "پرداخت از حساب",
                accounts = accounts,
                allowed = { a ->
                    val kind = AccountKind.fromCode(a.kindCode)
                    a.isEnabled && a.parentId != null && (kind == AccountKind.FUND || kind == AccountKind.BANK)
                },
                onDismiss = { payTarget = null },
                onPick = { payFundId = it.id },
            )
        } else {
            ShamsiDatePickerDialog(
                initial = LocalDate.now(),
                onDismiss = {
                    payTarget = null
                    payFundId = null
                },
                onPick = { picked ->
                    onPay(target.id, fundId, AccountingFormat.todayJalali(picked))
                    payTarget = null
                    payFundId = null
                },
            )
        }
    }

    val undo = undoTarget
    if (undo != null) {
        AlertDialog(
            onDismissRequest = { undoTarget = null },
            title = { Text("برگرداندن پرداخت") },
            text = { Text("پرداخت این قسط برگردانده شود؟ تراکنش آن حذف می‌شود.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUnpay(undo.id)
                        undoTarget = null
                    },
                ) { Text("برگردان") }
            },
            dismissButton = { TextButton(onClick = { undoTarget = null }) { Text("انصراف") } },
        )
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            itemName = row.loan.title,
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
    }
}

@Composable
private fun InstallmentRow(
    installment: InstallmentEntity,
    overdue: Boolean,
    onPay: () -> Unit,
    onUndo: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val textColor = if (overdue) scheme.error else scheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = num(installment.seq),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = AccountingFormat.dayHeader(installment.dueDate)
                    .ifEmpty { AccountingFormat.digits(installment.dueDate) },
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            Text(
                text = money(installment.amount),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
            )
        }
        if (installment.isPaid) {
            Text(
                text = "پرداخت شد",
                style = MaterialTheme.typography.labelLarge,
                color = txTypeColor(TxType.RECEIVE),
            )
            IconButton(onClick = onUndo) {
                TbIcon(R.drawable.ic_tb_repeat, "برگرداندن پرداخت", tint = scheme.onSurfaceVariant)
            }
        } else {
            PillButton(
                text = "پرداخت",
                onClick = onPay,
                modifier = Modifier.height(40.dp),
            )
        }
    }
}
