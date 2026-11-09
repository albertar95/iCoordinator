package com.icoordinator.ui.accounting

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.accounting.TxType
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.ShamsiDatePickerDialog
import com.icoordinator.ui.components.TbIcon
import com.icoordinator.ui.components.TimePickerDialog24
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** txId == null: new transaction of [type]. txId != null: edit that transaction (type is then derived from it). */
@Composable
fun TransactionFormScreen(type: TxType, txId: Long?, onDone: () -> Unit) {
    val vm: TransactionFormViewModel = viewModel(key = "tx_form_" + (txId ?: ("new_" + type.name))) {
        TransactionFormViewModel(ICoordinatorApp.instance.accountingRepository)
    }
    LaunchedEffect(type, txId) { vm.start(type, txId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val byId = remember(accounts) { accounts.associateBy { it.id } }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var showDebitPicker by remember { mutableStateOf(false) }
    var showCreditPicker by remember { mutableStateOf(false) }

    val title = if (txId != null) {
        "ویرایش تراکنش"
    } else {
        when (type) {
            TxType.PAY -> "پرداخت جدید"
            TxType.RECEIVE -> "دریافت جدید"
            TxType.TRANSFER -> "انتقال جدید"
            TxType.OTHER -> "تراکنش جدید"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        PageHeader(title = title, onBack = onDone)
        if (state.loaded) {
            val scheme = MaterialTheme.colorScheme
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                val dateText = AccountingFormat.longDateTime(state.date, state.time)
                val today = state.date == AccountingFormat.todayJalali()
                FormRow(
                    icon = R.drawable.ic_tb_calendar_event,
                    label = "تاریخ و ساعت",
                    value = (if (today) "امروز " else "") + dateText,
                    onClick = { showDate = true },
                )
                HorizontalDivider(color = scheme.outlineVariant)
                FormRow(
                    icon = R.drawable.ic_tb_calculator,
                    label = "مبلغ:",
                    value = state.amount?.let { MoneyFormat.format(it) + " تومان" } ?: "مبلغ را وارد کنید",
                    big = true,
                    onClick = { showKeypad = true },
                )
                HorizontalDivider(color = scheme.outlineVariant)
                FormRow(
                    icon = R.drawable.ic_tb_arrow_down,
                    label = "به حساب",
                    value = state.debitId?.let { AccountingFormat.accountPath(it, byId) }
                        ?.takeIf { it.isNotEmpty() } ?: "انتخاب کنید",
                    onClick = { showDebitPicker = true },
                )
                HorizontalDivider(color = scheme.outlineVariant)
                FormRow(
                    icon = R.drawable.ic_tb_arrow_up,
                    label = "از حساب",
                    value = state.creditId?.let { AccountingFormat.accountPath(it, byId) }
                        ?.takeIf { it.isNotEmpty() } ?: "انتخاب کنید",
                    onClick = { showCreditPicker = true },
                )
                HorizontalDivider(color = scheme.outlineVariant)
                AppTextField(
                    value = state.note,
                    onValueChange = vm::setNote,
                    label = "یادداشت",
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                )
                val error = state.error
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            PillButton(
                text = "ثبت",
                onClick = { vm.save(onDone) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .height(52.dp),
            )
        }
    }

    if (showDate) {
        val initial = AccountingMath.epochDayOf(state.date)?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        ShamsiDatePickerDialog(
            initial = initial,
            onDismiss = { showDate = false },
            onPick = { picked ->
                vm.setDate(AccountingFormat.todayJalali(picked))
                showDate = false
                showTime = true
            },
        )
    }
    if (showTime) {
        val parts = state.time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()
        val m = parts.getOrNull(1)?.toIntOrNull()
        val initial = if (h != null && m != null && h in 0..23 && m in 0..59) LocalTime.of(h, m) else LocalTime.now()
        TimePickerDialog24(
            initial = initial,
            onDismiss = { showTime = false },
            onPick = { picked ->
                vm.setTime(String.format(Locale.ROOT, "%02d:%02d", picked.hour, picked.minute))
                showTime = false
            },
        )
    }
    if (showKeypad) {
        AmountKeypadSheet(
            initial = state.amount,
            onDismiss = { showKeypad = false },
            onConfirm = { value ->
                vm.setAmount(value)
                showKeypad = false
            },
        )
    }
    if (showDebitPicker) {
        AccountPickerDialog(
            title = "انتخاب حساب",
            accounts = accounts,
            allowed = { vm.isAllowed(state.type, true, it) },
            onDismiss = { showDebitPicker = false },
            onPick = {
                vm.setDebit(it.id)
                showDebitPicker = false
            },
        )
    }
    if (showCreditPicker) {
        AccountPickerDialog(
            title = "انتخاب حساب",
            accounts = accounts,
            allowed = { vm.isAllowed(state.type, false, it) },
            onDismiss = { showCreditPicker = false },
            onPick = {
                vm.setCredit(it.id)
                showCreditPicker = false
            },
        )
    }
}

@Composable
private fun FormRow(
    @DrawableRes icon: Int,
    label: String,
    value: String,
    onClick: () -> Unit,
    big: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon = icon, size = 44.dp, iconSize = 24.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(
                text = value,
                style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
        }
        TbIcon(R.drawable.ic_tb_chevron_left, null, tint = scheme.onSurfaceVariant)
    }
}
