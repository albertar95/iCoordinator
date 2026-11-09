package com.icoordinator.ui.accounting

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.icoordinator.R
import com.icoordinator.accounting.AccountEntity
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.LoanEntity
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.DateField
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.ShamsiDatePickerDialog
import com.icoordinator.ui.components.TbIcon
import java.time.LocalDate

private fun toLocalDate(jalali: String): LocalDate? =
    AccountingMath.epochDayOf(jalali)?.let { LocalDate.ofEpochDay(it) }

private fun money(amount: Long): String = MoneyFormat.format(amount) + " تومان"

/** Installment total, or null when it would overflow a Long. */
private fun totalOf(amount: Long, count: Int): Long? =
    try {
        Math.multiplyExact(amount, count.toLong())
    } catch (e: ArithmeticException) {
        null
    }

/** Full-screen form: [row] == null creates a new loan, otherwise only title and note of that loan are editable. */
@Composable
internal fun LoanFormScreen(
    row: LoanRow?,
    accounts: List<AccountEntity>,
    saving: Boolean,
    error: String?,
    onClearError: () -> Unit,
    onCreate: (NewLoanInput) -> Unit,
    onSaveEdit: (LoanEntity, String, String) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val byId = remember(accounts) { accounts.associateBy { it.id } }

    var title by rememberSaveable { mutableStateOf(row?.loan?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(row?.loan?.note ?: "") }
    var principal by rememberSaveable { mutableStateOf<Long?>(null) }
    var installmentAmount by rememberSaveable { mutableStateOf<Long?>(null) }
    var countText by rememberSaveable { mutableStateOf("") }
    var firstDue by rememberSaveable { mutableStateOf("") }
    var receiveDate by rememberSaveable { mutableStateOf(AccountingFormat.todayJalali()) }
    var fundId by rememberSaveable { mutableStateOf<Long?>(null) }
    var recordReceipt by rememberSaveable { mutableStateOf(true) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    var showPrincipal by remember { mutableStateOf(false) }
    var showInstallment by remember { mutableStateOf(false) }
    var showFirstDue by remember { mutableStateOf(false) }
    var showReceive by remember { mutableStateOf(false) }
    var showFund by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onClearError() }

    val count = MoneyFormat.parse(countText)?.takeIf { it in 1L..360L }?.toInt()
    val perInstallment = installmentAmount
    val principalValue = principal
    val total = if (perInstallment != null && count != null) totalOf(perInstallment, count) else null
    val fee = if (total != null && principalValue != null) total - principalValue else null

    fun submitNew() {
        val cleanTitle = title.trim()
        val message: String? = when {
            cleanTitle.isEmpty() -> "عنوان وام را وارد کنید"
            principalValue == null || principalValue <= 0L -> "مبلغ دریافتی را وارد کنید"
            perInstallment == null || perInstallment <= 0L -> "مبلغ هر قسط را وارد کنید"
            count == null -> "تعداد اقساط باید بین ۱ تا ۳۶۰ باشد"
            total == null -> "مبلغ قسط بسیار بزرگ است"
            AccountingMath.normalizeDate(firstDue) == null -> "تاریخ اولین قسط را انتخاب کنید"
            AccountingMath.normalizeDate(receiveDate) == null -> "تاریخ دریافت نامعتبر است"
            recordReceipt && fundId == null -> "صندوق دریافت‌کننده را انتخاب کنید"
            else -> null
        }
        if (message != null || principalValue == null || perInstallment == null || count == null) {
            localError = message
            return
        }
        localError = null
        onCreate(
            NewLoanInput(
                title = cleanTitle,
                principal = principalValue,
                installmentAmount = perInstallment,
                count = count,
                firstDueDate = firstDue,
                receiveDate = receiveDate,
                fundId = fundId,
                note = note,
                recordReceipt = recordReceipt,
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .imePadding(),
    ) {
        PageHeader(title = if (row == null) "وام جدید" else "ویرایش وام", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTextField(
                value = title,
                onValueChange = {
                    title = it
                    localError = null
                },
                label = "عنوان",
                modifier = Modifier.fillMaxWidth(),
            )
            if (row == null) {
                LoanFieldRow(
                    icon = R.drawable.ic_tb_calculator,
                    label = "مبلغ دریافتی",
                    value = principalValue?.let { money(it) } ?: "مبلغ را وارد کنید",
                    onClick = { showPrincipal = true },
                )
                LoanFieldRow(
                    icon = R.drawable.ic_tb_calculator,
                    label = "مبلغ هر قسط",
                    value = perInstallment?.let { money(it) } ?: "مبلغ را وارد کنید",
                    onClick = { showInstallment = true },
                )
                AppTextField(
                    value = countText,
                    onValueChange = {
                        countText = it
                        localError = null
                    },
                    label = "تعداد اقساط (۱ تا ۳۶۰)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "تاریخ اولین قسط",
                    date = toLocalDate(firstDue),
                    onClick = { showFirstDue = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(
                    label = "تاریخ دریافت",
                    date = toLocalDate(receiveDate),
                    onClick = { showReceive = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                LoanFieldRow(
                    icon = R.drawable.ic_tb_wallet,
                    label = "صندوق دریافت‌کننده",
                    value = fundId?.let { AccountingFormat.accountPath(it, byId) }
                        ?.takeIf { it.isNotEmpty() } ?: "انتخاب کنید",
                    onClick = { showFund = true },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Checkbox) { recordReceipt = !recordReceipt },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = recordReceipt, onCheckedChange = { recordReceipt = it })
                    Text(
                        text = "ثبت دریافت وام در صندوق",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                    )
                }
            }
            AppTextField(
                value = note,
                onValueChange = { note = it },
                label = "توضیحات",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (row == null) {
                if (total != null) {
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(scheme.surfaceContainer, shape)
                            .border(1.dp, scheme.outlineVariant, shape)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ReadOnlyLine("جمع اقساط", money(total))
                        if (fee != null && fee > 0L) {
                            HorizontalDivider(color = scheme.outlineVariant)
                            ReadOnlyLine("سود و کارمزد", money(fee))
                        }
                    }
                }
            } else {
                val loan = row.loan
                val shape = RoundedCornerShape(20.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainer, shape)
                        .border(1.dp, scheme.outlineVariant, shape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReadOnlyLine("مبلغ وام", money(loan.principal))
                    ReadOnlyLine(
                        "تعداد اقساط",
                        AccountingFormat.digits(row.installments.size.toString()),
                    )
                    row.installments.firstOrNull()?.let { ReadOnlyLine("مبلغ هر قسط", money(it.amount)) }
                    ReadOnlyLine(
                        "تاریخ اولین قسط",
                        AccountingFormat.dayHeader(loan.firstDueDate).ifEmpty { AccountingFormat.digits(loan.firstDueDate) },
                    )
                    ReadOnlyLine(
                        "تاریخ دریافت",
                        AccountingFormat.dayHeader(loan.receiveDate).ifEmpty { AccountingFormat.digits(loan.receiveDate) },
                    )
                }
                Text(
                    text = "مبلغ، تعداد و تاریخ اقساط بعد از ثبت قابل ویرایش نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            val shownError = localError ?: error
            if (shownError != null) {
                Text(
                    text = shownError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        PillButton(
            text = if (row == null) "ثبت وام" else "ذخیره",
            onClick = {
                if (row == null) {
                    submitNew()
                } else if (title.isBlank()) {
                    localError = "عنوان وام را وارد کنید"
                } else {
                    localError = null
                    onSaveEdit(row.loan, title, note)
                }
            },
            enabled = !saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .height(52.dp),
        )
    }

    if (showPrincipal) {
        AmountKeypadSheet(
            initial = principalValue,
            onDismiss = { showPrincipal = false },
            onConfirm = {
                principal = it
                localError = null
                showPrincipal = false
            },
        )
    }
    if (showInstallment) {
        AmountKeypadSheet(
            initial = perInstallment,
            onDismiss = { showInstallment = false },
            onConfirm = {
                installmentAmount = it
                localError = null
                showInstallment = false
            },
        )
    }
    if (showFirstDue) {
        ShamsiDatePickerDialog(
            initial = toLocalDate(firstDue) ?: LocalDate.now(),
            onDismiss = { showFirstDue = false },
            onPick = {
                firstDue = AccountingFormat.todayJalali(it)
                localError = null
                showFirstDue = false
            },
        )
    }
    if (showReceive) {
        ShamsiDatePickerDialog(
            initial = toLocalDate(receiveDate) ?: LocalDate.now(),
            onDismiss = { showReceive = false },
            onPick = {
                receiveDate = AccountingFormat.todayJalali(it)
                showReceive = false
            },
        )
    }
    if (showFund) {
        AccountPickerDialog(
            title = "صندوق دریافت‌کننده",
            accounts = accounts,
            allowed = { a ->
                val kind = AccountKind.fromCode(a.kindCode)
                a.isEnabled && a.parentId != null && (kind == AccountKind.FUND || kind == AccountKind.BANK)
            },
            onDismiss = { showFund = false },
            onPick = {
                fundId = it.id
                localError = null
                showFund = false
            },
        )
    }
}

@Composable
private fun ReadOnlyLine(label: String, value: String) {
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
private fun LoanFieldRow(icon: Int, label: String, value: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon = icon, size = 40.dp, iconSize = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
        }
        TbIcon(R.drawable.ic_tb_chevron_left, null, tint = scheme.onSurfaceVariant)
    }
}
