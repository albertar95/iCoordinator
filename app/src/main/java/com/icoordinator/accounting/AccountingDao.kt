package com.icoordinator.accounting

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {
    @Query("SELECT * FROM acc_account ORDER BY sortOrder, id") fun observeAccounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM acc_account ORDER BY sortOrder, id") suspend fun getAccounts(): List<AccountEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAccount(account: AccountEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Query("SELECT * FROM acc_transaction ORDER BY epochDay DESC, time DESC, id DESC") fun observeTransactions(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM acc_transaction ORDER BY epochDay DESC, time DESC, id DESC") suspend fun getTransactions(): List<TransactionEntity>
    @Query("SELECT * FROM acc_transaction WHERE id = :id") suspend fun getTransaction(id: Long): TransactionEntity?
    @Insert suspend fun insertTransaction(tx: TransactionEntity): Long
    @Insert suspend fun insertTransactions(txs: List<TransactionEntity>)
    @Update suspend fun updateTransaction(tx: TransactionEntity)
    @Query("DELETE FROM acc_transaction WHERE id = :id") suspend fun deleteTransaction(id: Long)

    @Query("SELECT * FROM acc_loan ORDER BY isClosed, id") fun observeLoans(): Flow<List<LoanEntity>>
    @Query("SELECT * FROM acc_loan WHERE id = :id") suspend fun getLoan(id: Long): LoanEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLoan(loan: LoanEntity): Long
    @Insert suspend fun insertLoans(loans: List<LoanEntity>)
    @Query("DELETE FROM acc_loan WHERE id = :id") suspend fun deleteLoan(id: Long)

    @Query("SELECT * FROM acc_installment ORDER BY dueEpochDay, seq") fun observeInstallments(): Flow<List<InstallmentEntity>>
    @Query("SELECT * FROM acc_installment WHERE loanId = :loanId ORDER BY seq") suspend fun getInstallments(loanId: Long): List<InstallmentEntity>
    @Query("SELECT * FROM acc_installment WHERE id = :id") suspend fun getInstallment(id: Long): InstallmentEntity?
    @Insert suspend fun insertInstallments(installments: List<InstallmentEntity>)
    @Update suspend fun updateInstallment(installment: InstallmentEntity)
    @Query("DELETE FROM acc_installment WHERE loanId = :loanId") suspend fun deleteInstallments(loanId: Long)

    @Query("DELETE FROM acc_installment") suspend fun clearInstallments()
    @Query("DELETE FROM acc_loan") suspend fun clearLoans()
    @Query("DELETE FROM acc_transaction") suspend fun clearTransactions()
    @Query("DELETE FROM acc_account") suspend fun clearAccounts()
}
