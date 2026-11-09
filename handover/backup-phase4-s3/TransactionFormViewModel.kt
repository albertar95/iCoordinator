package com.icoordinator.ui.accounting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.accounting.AccountEntity
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.AccountingRepository
import com.icoordinator.accounting.TransactionEntity
import com.icoordinator.accounting.TxType
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TxFormState(
    val loaded: Boolean = false,
    val type: TxType = TxType.PAY,
    val date: String = "",          // Jalali "yyyy/MM/dd"
    val time: String = "",          // "HH:mm"
    val amount: Long? = null,
    val debitId: Long? = null,      // "به حساب"
    val creditId: Long? = null,     // "از حساب"
    val note: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

class TransactionFormViewModel(private val repo: AccountingRepository) : ViewModel() {

    private val _state = MutableStateFlow(TxFormState())
    val state: StateFlow<TxFormState> = _state

    val accounts: StateFlow<List<AccountEntity>> =
        repo.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var started = false
    private var original: TransactionEntity? = null

    /** Prepares the form once: defaults for a new transaction, or loads transaction [txId] for editing. */
    fun start(type: TxType, txId: Long?) {
        if (started) return
        started = true
        if (txId == null) {
            val now = LocalTime.now()
            _state.value = TxFormState(
                loaded = true,
                type = type,
                date = AccountingFormat.todayJalali(),
                time = String.format(Locale.ROOT, "%02d:%02d", now.hour, now.minute),
            )
            return
        }
        viewModelScope.launch {
            val tx = repo.observeTransactions().first().firstOrNull { it.id == txId }
            if (tx == null) {
                _state.value = TxFormState(loaded = true, type = type, error = "تراکنش پیدا نشد")
                return@launch
            }
            val kinds = AccountingMath.kindsById(repo.observeAccounts().first())
            original = tx
            _state.value = TxFormState(
                loaded = true,
                type = AccountingMath.classify(
                    kinds[tx.debitAccountId] ?: AccountKind.OTHER,
                    kinds[tx.creditAccountId] ?: AccountKind.OTHER,
                ),
                date = tx.date,
                time = tx.time,
                amount = tx.amount,
                debitId = tx.debitAccountId,
                creditId = tx.creditAccountId,
                note = tx.note,
            )
        }
    }

    fun setDate(date: String) = update { it.copy(date = date, error = null) }
    fun setTime(time: String) = update { it.copy(time = time, error = null) }
    fun setAmount(amount: Long?) = update { it.copy(amount = amount, error = null) }
    fun setDebit(id: Long) = update { it.copy(debitId = id, error = null) }
    fun setCredit(id: Long) = update { it.copy(creditId = id, error = null) }
    fun setNote(note: String) = update { it.copy(note = note, error = null) }

    private fun update(block: (TxFormState) -> TxFormState) {
        _state.value = block(_state.value)
    }

    /** Whether [account] can be chosen on the debit ("به حساب") or credit ("از حساب") side for [type]. */
    fun isAllowed(type: TxType, debitSide: Boolean, account: AccountEntity): Boolean {
        if (account.parentId == null || !account.isEnabled) return false
        val cash = AccountingMath.isCash(AccountKind.fromCode(account.kindCode))
        return when (type) {
            TxType.PAY -> if (debitSide) !cash else cash
            TxType.RECEIVE -> if (debitSide) cash else !cash
            TxType.TRANSFER -> cash
            TxType.OTHER -> true
        }
    }

    /** Validates and saves; onDone runs only after a successful save. Errors are shown in the state. */
    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        val amount = s.amount
        if (amount == null || amount <= 0L) {
            _state.value = s.copy(error = "مبلغ را وارد کنید")
            return
        }
        val debit = s.debitId
        val credit = s.creditId
        if (debit == null || credit == null) {
            _state.value = s.copy(error = "حساب را انتخاب کنید")
            return
        }
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val base = original
                val epochDay = AccountingMath.epochDayOf(s.date) ?: 0L
                if (base == null) {
                    repo.addTransaction(
                        TransactionEntity(
                            date = s.date, time = s.time, epochDay = epochDay, amount = amount,
                            debitAccountId = debit, creditAccountId = credit, note = s.note.trim(),
                        ),
                    )
                } else {
                    repo.updateTransaction(
                        base.copy(
                            date = s.date, time = s.time, epochDay = epochDay, amount = amount,
                            debitAccountId = debit, creditAccountId = credit, note = s.note.trim(),
                        ),
                    )
                }
                _state.value = _state.value.copy(saving = false)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.value = _state.value.copy(saving = false, error = e.message ?: "ذخیره ممکن نشد")
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = e.message ?: "ذخیره ممکن نشد")
            }
        }
    }
}
