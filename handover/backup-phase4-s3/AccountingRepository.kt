package com.icoordinator.accounting

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Single entry point of the accounting section; wraps [AccountingDao] and applies [AccountingMath] rules. */
class AccountingRepository(
    private val db: AccountingDatabase,
    private val dao: AccountingDao = db.accountingDao(),
) {

    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()

    fun observeTransactions(): Flow<List<TransactionEntity>> = dao.observeTransactions()

    fun observeLoans(): Flow<List<LoanEntity>> = dao.observeLoans()

    fun observeInstallments(): Flow<List<InstallmentEntity>> = dao.observeInstallments()

    /** Balance of every account (D52): openingBalance + debits - credits. */
    fun observeBalances(): Flow<Map<Long, Long>> =
        combine(dao.observeAccounts(), dao.observeTransactions()) { accounts, txs ->
            AccountingMath.balances(accounts, txs)
        }

    /** Validates, normalizes date and epochDay, saves. Returns the new id. */
    suspend fun addTransaction(tx: TransactionEntity): Long = dao.insertTransaction(validated(tx))

    suspend fun updateTransaction(tx: TransactionEntity) = dao.updateTransaction(validated(tx))

    /** Deletes the transaction; if it paid an installment, that installment becomes unpaid again. */
    suspend fun deleteTransaction(id: Long) {
        db.withTransaction {
            val tx = dao.getTransaction(id)
            val installmentId = tx?.installmentId
            if (installmentId != null) {
                dao.getInstallment(installmentId)?.let {
                    dao.updateInstallment(it.copy(isPaid = false, paidTransactionId = null))
                    dao.getLoan(it.loanId)?.let { loan -> dao.upsertLoan(loan.copy(isClosed = false)) } // D61
                }
            }
            dao.deleteTransaction(id)
        }
    }

    suspend fun saveAccount(account: AccountEntity): Long = dao.upsertAccount(account)

    /** Saves the loan and replaces its installments (ids reset, seq renumbered by due day then given order). Returns the loan id. */
    suspend fun saveLoan(loan: LoanEntity, installments: List<InstallmentEntity>): Long =
        db.withTransaction {
            val loanId = dao.upsertLoan(loan)
            dao.deleteInstallments(loanId)
            val renumbered = installments
                .sortedBy { it.dueEpochDay }
                .mapIndexed { index, item -> item.copy(id = 0, loanId = loanId, seq = index + 1) }
            dao.insertInstallments(renumbered)
            loanId
        }

    /** Deletes the loan and its installments; transactions stay. */
    suspend fun deleteLoan(loanId: Long) {
        db.withTransaction {
            dao.deleteInstallments(loanId)
            dao.deleteLoan(loanId)
        }
    }

    /** Pays an installment from [fromAccountId]. Returns the id of the created transaction. */
    suspend fun payInstallment(installmentId: Long, fromAccountId: Long, date: String, time: String): Long =
        db.withTransaction {
            val installment = dao.getInstallment(installmentId)
                ?: throw IllegalArgumentException("قسط پیدا نشد")
            if (installment.isPaid) return@withTransaction installment.paidTransactionId ?: 0L
            val loan = dao.getLoan(installment.loanId)
                ?: throw IllegalArgumentException("وام پیدا نشد")
            val debitAccountId = loan.accountId
                ?: throw IllegalArgumentException("برای این وام حساب بدهی تعریف نشده")
            val tx = validated(
                TransactionEntity(
                    date = date,
                    time = time,
                    epochDay = 0,
                    amount = installment.amount,
                    debitAccountId = debitAccountId,
                    creditAccountId = fromAccountId,
                    note = "قسط " + installment.seq + " " + loan.title,
                    installmentId = installment.id,
                ),
            )
            val txId = dao.insertTransaction(tx)
            dao.updateInstallment(installment.copy(isPaid = true, paidTransactionId = txId))
            if (dao.getInstallments(loan.id).all { it.isPaid }) {
                dao.upsertLoan(loan.copy(isClosed = true))
            }
            txId
        }

    /** Reverts a payment: deletes the linked transaction, marks the installment unpaid and reopens the loan. */
    suspend fun unpayInstallment(installmentId: Long) {
        db.withTransaction {
            val installment = dao.getInstallment(installmentId)
                ?: throw IllegalArgumentException("قسط پیدا نشد")
            installment.paidTransactionId?.let { dao.deleteTransaction(it) }
            dao.updateInstallment(installment.copy(isPaid = false, paidTransactionId = null))
            dao.getLoan(installment.loanId)?.let { dao.upsertLoan(it.copy(isClosed = false)) }
        }
    }

    /** Replaces the whole database content with an import result (ids kept). */
    suspend fun replaceAll(result: ImportResult) {
        db.withTransaction {
            dao.clearInstallments()
            dao.clearLoans()
            dao.clearTransactions()
            dao.clearAccounts()
            dao.insertAccounts(result.accounts)
            dao.insertTransactions(result.transactions)
            dao.insertLoans(result.loans)
            dao.insertInstallments(result.installments)
        }
    }

    /** Checks the transaction rules and returns a copy with normalized date and matching epochDay. */
    private fun validated(tx: TransactionEntity): TransactionEntity {
        require(tx.amount > 0) { "مبلغ باید بیشتر از صفر باشد" }
        require(tx.debitAccountId != tx.creditAccountId) { "حساب مبدا و مقصد نباید یکی باشند" }
        val date = AccountingMath.normalizeDate(tx.date)
        val epochDay = date?.let { AccountingMath.epochDayOf(it) }
        require(date != null && epochDay != null) { "تاریخ نامعتبر است" }
        return tx.copy(date = date, epochDay = epochDay)
    }
}
