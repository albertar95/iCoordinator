package com.icoordinator.ui.accounting

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.accounting.AccountEntity
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.AccountingRepository
import com.icoordinator.accounting.CategoryTotal
import com.icoordinator.accounting.ImportResult
import com.icoordinator.accounting.ParmisMapper
import com.icoordinator.accounting.ParmisReader
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A fund or bank account (leaf) with its balance. */
data class FundRow(val account: AccountEntity, val kind: AccountKind, val balance: Long)

data class DashboardState(
    val loaded: Boolean = false,
    val hasAccounts: Boolean = false,
    val year: Int = 0,
    val month: Int = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val funds: List<FundRow> = emptyList(),
    val fundsTotal: Long = 0,
    val expenseCategories: List<CategoryTotal> = emptyList(),
    val incomeCategories: List<CategoryTotal> = emptyList(),
)

/** A parsed Parmis backup waiting for the user's confirmation. */
data class PendingImport(val result: ImportResult, val replacesExisting: Boolean)

/**
 * Dashboard state and the Parmis import flow. [context] is an application context (ICoordinatorApp.instance),
 * used only to read the picked file and for the cache directory.
 */
class AccountingViewModel(
    private val repo: AccountingRepository,
    private val context: Context,
) : ViewModel() {

    private val month = MutableStateFlow(AccountingFormat.currentMonth())

    val state: StateFlow<DashboardState> =
        combine(repo.observeAccounts(), repo.observeTransactions(), month) { accounts, txs, ym ->
            val (year, mon) = ym
            val range = AccountingFormat.monthRange(year, mon)
            val totals = AccountingMath.periodTotals(accounts, txs, range.first, range.last)
            val kinds = AccountingMath.kindsById(accounts)
            val balances = AccountingMath.balances(accounts, txs)
            val parents = accounts.mapNotNull { it.parentId }.toSet()
            val funds = accounts
                .filter { a ->
                    val kind = kinds[a.id]
                    (kind == AccountKind.FUND || kind == AccountKind.BANK) && a.id !in parents
                }
                .map { a -> FundRow(a, kinds[a.id] ?: AccountKind.FUND, balances[a.id] ?: 0L) }
            DashboardState(
                loaded = true,
                hasAccounts = accounts.isNotEmpty(),
                year = year,
                month = mon,
                income = totals.income,
                expense = totals.expense,
                funds = funds,
                fundsTotal = funds.sumOf { it.balance },
                expenseCategories = AccountingMath.categoryTotals(accounts, txs, AccountKind.EXPENSE, range.first, range.last),
                incomeCategories = AccountingMath.categoryTotals(accounts, txs, AccountKind.INCOME, range.first, range.last),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AccountingFormat.currentMonth().let { DashboardState(year = it.first, month = it.second) },
        )

    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** delta = -1 previous month, +1 next month. */
    fun shiftMonth(delta: Int) {
        val (y, m) = month.value
        val index = y * 12 + (m - 1) + delta
        month.value = (index / 12) to (index % 12 + 1)
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun cancelImport() {
        _pending.value = null
    }

    /** Reads the picked .par file and prepares the confirmation dialog. */
    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val result = withContext(Dispatchers.IO) { readParmis(uri) }
                val hasAccounts = repo.observeAccounts().first().isNotEmpty()
                _pending.value = PendingImport(result, hasAccounts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "خواندن فایل ممکن نشد"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun readParmis(uri: Uri): ImportResult {
        val file = File(context.cacheDir, "parmis-import.par")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("خواندن فایل ممکن نشد")
            input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
            return ParmisMapper.map(ParmisReader.read(file))
        } finally {
            file.delete()
        }
    }

    fun confirmImport() {
        val p = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            _busy.value = true
            try {
                repo.replaceAll(p.result)
                _message.value = "اطلاعات پارمیس وارد شد"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "خواندن فایل ممکن نشد"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Creates roots هزینه ها / درآمد ها / صندوق ها and one child under each. */
    fun createStarterAccounts() {
        viewModelScope.launch {
            try {
                val roots = listOf(
                    Triple("هزینه ها", AccountKind.EXPENSE, "سایر"),
                    Triple("درآمد ها", AccountKind.INCOME, "حقوق"),
                    Triple("صندوق ها", AccountKind.FUND, "کیف پول"),
                )
                roots.forEachIndexed { index, (rootTitle, kind, childTitle) ->
                    val rootId = repo.saveAccount(
                        AccountEntity(parentId = null, title = rootTitle, kindCode = kind.code, sortOrder = index),
                    )
                    repo.saveAccount(
                        AccountEntity(parentId = rootId, title = childTitle, kindCode = kind.code, sortOrder = 0),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "ساخت حساب‌ها ممکن نشد"
            }
        }
    }
}
