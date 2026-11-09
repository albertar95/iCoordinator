package com.icoordinator.ui.accounting

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.AccountingRepository
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.accounting.TransactionEntity
import com.icoordinator.accounting.TxType
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.TagText
import com.icoordinator.ui.components.TbIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A transaction with its derived type and display data. */
data class TxItem(
    val tx: TransactionEntity,
    val type: TxType,
    val debitKind: AccountKind,
    val creditKind: AccountKind,
    val debitPath: String,
    val creditPath: String,
    @DrawableRes val debitIcon: Int,
    @DrawableRes val creditIcon: Int,
)

class TransactionListViewModel(private val repo: AccountingRepository) : ViewModel() {

    val items: StateFlow<List<TxItem>> =
        combine(repo.observeTransactions(), repo.observeAccounts()) { txs, accounts ->
            val byId = accounts.associateBy { it.id }
            val kinds = AccountingMath.kindsById(accounts)
            txs.sortedWith(
                compareByDescending<TransactionEntity> { it.epochDay }
                    .thenByDescending { it.time }
                    .thenByDescending { it.id },
            ).map { t ->
                val dk = kinds[t.debitAccountId] ?: AccountKind.OTHER
                val ck = kinds[t.creditAccountId] ?: AccountKind.OTHER
                val known = t.debitAccountId in kinds && t.creditAccountId in kinds
                TxItem(
                    tx = t,
                    type = if (known) AccountingMath.classify(dk, ck) else TxType.OTHER,
                    debitKind = dk,
                    creditKind = ck,
                    debitPath = AccountingFormat.accountPath(t.debitAccountId, byId),
                    creditPath = AccountingFormat.accountPath(t.creditAccountId, byId),
                    debitIcon = accountIcon(t.debitAccountId, byId),
                    creditIcon = accountIcon(t.creditAccountId, byId),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            try {
                repo.deleteTransaction(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "حذف تراکنش ممکن نشد"
            }
        }
    }
}

private sealed interface ListEntry {
    data class Header(val epochDay: Long, val title: String, val net: Long) : ListEntry
    data class Card(val item: TxItem) : ListEntry
}

private val TAB_TITLES = listOf("همه", "پرداخت‌ها", "دریافت‌ها", "انتقال‌ها")

private fun tabType(index: Int): TxType? = when (index) {
    1 -> TxType.PAY
    2 -> TxType.RECEIVE
    3 -> TxType.TRANSFER
    else -> null
}

/** Labels of the debit line and the credit line. */
private fun lineLabels(type: TxType): Pair<String, String> = when (type) {
    TxType.PAY -> "پرداخت به :" to "از حساب :"
    TxType.RECEIVE -> "دریافت در :" to "از محل :"
    TxType.TRANSFER, TxType.OTHER -> "به حساب :" to "از حساب :"
}

@Composable
fun TransactionListScreen(onEdit: (Long) -> Unit) {
    val vm: TransactionListViewModel = viewModel(key = "acc_tx_list") {
        TransactionListViewModel(ICoordinatorApp.instance.accountingRepository)
    }
    val all by vm.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedNotes by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<TxItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbar.showSnackbar(text)
            vm.consumeMessage()
        }
    }

    val entries = remember(all, tab, query, searching) {
        val type = tabType(tab)
        val q = if (searching) query.trim() else ""
        val filtered = all.filter { item ->
            (type == null || item.type == type) &&
                (
                    q.isEmpty() ||
                        item.tx.note.contains(q, ignoreCase = true) ||
                        item.debitPath.contains(q, ignoreCase = true) ||
                        item.creditPath.contains(q, ignoreCase = true)
                    )
        }
        buildList<ListEntry> {
            // `all` is already sorted newest first, so groupBy keeps days in that order
            for ((day, group) in filtered.groupBy { it.tx.epochDay }) {
                val net = group.sumOf {
                    when (it.type) {
                        TxType.RECEIVE -> it.tx.amount
                        TxType.PAY -> -it.tx.amount
                        else -> 0L
                    }
                }
                val first = group.first().tx.date
                val title = AccountingFormat.dayHeader(first).ifEmpty { AccountingFormat.digits(first) }
                add(ListEntry.Header(day, title, net))
                group.forEach { add(ListEntry.Card(it)) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(
                title = "تراکنش‌ها",
                actionIcon = R.drawable.ic_tb_search,
                actionDescription = "جستجو",
                onAction = {
                    searching = !searching
                    if (!searching) query = ""
                },
            )
            if (searching) {
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "جستجو در یادداشت و حساب‌ها",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TAB_TITLES.forEachIndexed { index, title ->
                    TabChip(title = title, selected = tab == index, onClick = { tab = index }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_tb_list_details,
                    title = "تراکنشی نیست",
                    message = "با دکمه‌ی + تراکنش ثبت کنید",
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
                                is ListEntry.Header -> "d" + entry.epochDay
                                is ListEntry.Card -> entry.item.tx.id
                            }
                        },
                    ) { entry ->
                        when (entry) {
                            is ListEntry.Header -> DayHeader(entry)
                            is ListEntry.Card -> TxCard(
                                item = entry.item,
                                noteExpanded = entry.item.tx.id in expandedNotes,
                                onToggleNote = {
                                    val id = entry.item.tx.id
                                    expandedNotes = if (id in expandedNotes) expandedNotes - id else expandedNotes + id
                                },
                                onEdit = { onEdit(entry.item.tx.id) },
                                onDelete = { pendingDelete = entry.item },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDeleteDialog(
            itemName = MoneyFormat.format(toDelete.tx.amount) + " تومان",
            onConfirm = {
                vm.deleteTransaction(toDelete.tx.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun TabChip(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) scheme.primary else scheme.surfaceContainer, shape)
            .then(if (selected) Modifier else Modifier.border(1.dp, scheme.outlineVariant, shape))
            .clickable(role = Role.Tab, onClick = onClick),
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
private fun DayHeader(header: ListEntry.Header) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = header.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (header.net != 0L) {
            Text(
                text = (if (header.net > 0) "+" else "") + MoneyFormat.format(header.net),
                style = MaterialTheme.typography.titleSmall,
                color = txTypeColor(if (header.net > 0) TxType.RECEIVE else TxType.PAY),
            )
        }
    }
}

@Composable
private fun TxCard(
    item: TxItem,
    noteExpanded: Boolean,
    onToggleNote: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val (debitLabel, creditLabel) = lineLabels(item.type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(16.dp),
    ) {
        AccountLine(item.debitIcon, debitLabel, item.debitPath)
        Spacer(Modifier.height(8.dp))
        AccountLine(item.creditIcon, creditLabel, item.creditPath)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MoneyFormat.format(item.tx.amount) + " تومان",
                style = MaterialTheme.typography.titleMedium,
                color = txTypeColor(item.type),
                modifier = Modifier.weight(1f),
            )
            TagText(
                text = AccountingFormat.digits(item.tx.time),
                icon = R.drawable.ic_tb_clock,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEdit) {
                TbIcon(R.drawable.ic_tb_pencil, null, size = 18.dp, tint = scheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("ویرایش", color = scheme.primary)
            }
            Spacer(Modifier.weight(1f))
            DeleteIconButton(onClick = onDelete)
        }
        if (item.tx.note.isNotBlank()) {
            Text(
                text = "جزئیات بیشتر",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onToggleNote)
                    .padding(vertical = 4.dp),
            )
            if (noteExpanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.tx.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccountLine(icon: Int, label: String, path: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon = icon, size = 36.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(
                text = path.ifEmpty { "نامشخص" },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }
    }
}
