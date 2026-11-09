package com.icoordinator.accounting

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** In-memory AccountingDao; ids are auto-assigned when 0. */
class FakeAccountingDao : AccountingDao {
    val accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val loans = MutableStateFlow<List<LoanEntity>>(emptyList())
    val installments = MutableStateFlow<List<InstallmentEntity>>(emptyList())
    var writes = 0
    private var nextId = 1L

    private fun id(given: Long): Long = if (given != 0L) { if (given >= nextId) nextId = given + 1; given } else nextId++

    override fun observeAccounts(): Flow<List<AccountEntity>> = accounts
    override suspend fun getAccounts(): List<AccountEntity> = accounts.value
    override suspend fun upsertAccount(account: AccountEntity): Long {
        writes++
        val e = account.copy(id = id(account.id))
        accounts.value = accounts.value.filter { it.id != e.id } + e
        return e.id
    }
    override suspend fun insertAccounts(accounts: List<AccountEntity>) { accounts.forEach { upsertAccount(it) } }

    override fun observeTransactions(): Flow<List<TransactionEntity>> = transactions
    override suspend fun getTransactions(): List<TransactionEntity> = transactions.value
    override suspend fun getTransaction(id: Long): TransactionEntity? = transactions.value.firstOrNull { it.id == id }
    override suspend fun insertTransaction(tx: TransactionEntity): Long {
        writes++
        val e = tx.copy(id = id(tx.id))
        transactions.value = transactions.value + e
        return e.id
    }
    override suspend fun insertTransactions(txs: List<TransactionEntity>) { txs.forEach { insertTransaction(it) } }
    override suspend fun updateTransaction(tx: TransactionEntity) {
        writes++
        transactions.value = transactions.value.map { if (it.id == tx.id) tx else it }
    }
    override suspend fun deleteTransaction(id: Long) {
        writes++
        transactions.value = transactions.value.filter { it.id != id }
    }

    override fun observeLoans(): Flow<List<LoanEntity>> = loans
    override suspend fun getLoan(id: Long): LoanEntity? = loans.value.firstOrNull { it.id == id }
    override suspend fun upsertLoan(loan: LoanEntity): Long {
        writes++
        val e = loan.copy(id = id(loan.id))
        loans.value = loans.value.filter { it.id != e.id } + e
        return e.id
    }
    override suspend fun insertLoans(loans: List<LoanEntity>) { loans.forEach { upsertLoan(it) } }
    override suspend fun deleteLoan(id: Long) {
        writes++
        loans.value = loans.value.filter { it.id != id }
    }

    override fun observeInstallments(): Flow<List<InstallmentEntity>> = installments
    override suspend fun getInstallments(loanId: Long): List<InstallmentEntity> =
        installments.value.filter { it.loanId == loanId }.sortedBy { it.seq }
    override suspend fun getInstallment(id: Long): InstallmentEntity? = installments.value.firstOrNull { it.id == id }
    override suspend fun insertInstallments(installments: List<InstallmentEntity>) {
        writes++
        this.installments.value = this.installments.value + installments.map { it.copy(id = id(it.id)) }
    }
    override suspend fun updateInstallment(installment: InstallmentEntity) {
        writes++
        installments.value = installments.value.map { if (it.id == installment.id) installment else it }
    }
    override suspend fun deleteInstallments(loanId: Long) {
        writes++
        installments.value = installments.value.filter { it.loanId != loanId }
    }

    override suspend fun clearInstallments() { installments.value = emptyList() }
    override suspend fun clearLoans() { loans.value = emptyList() }
    override suspend fun clearTransactions() { transactions.value = emptyList() }
    override suspend fun clearAccounts() { accounts.value = emptyList() }
}

/** Runs the block directly (no real database in JVM unit tests). */
object DirectTransactionRunner : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = block()
}

class AccountingRepositoryTest {
    private val dao = FakeAccountingDao()
    private val repo = AccountingRepository(dao, DirectTransactionRunner)

    private fun newFund(): Long = runBlocking {
        val root = dao.upsertAccount(AccountEntity(parentId = null, title = "صندوق", kindCode = AccountKind.FUND.code))
        dao.upsertAccount(AccountEntity(parentId = root, title = "کیف پول", kindCode = AccountKind.FUND.code))
    }

    private fun balances() = AccountingMath.balances(dao.accounts.value, dao.transactions.value)

    private fun payAll(loanId: Long, fundId: Long) = runBlocking {
        for (i in dao.getInstallments(loanId)) repo.payInstallment(i.id, fundId, "1405/09/05", "09:00")
    }

    private fun expectRejected(message: String, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals(message, e.message)
        }
    }

    @Test
    fun loanWithFundCreatesReceiptFeeAndScheduleAndEndsAtZero() = runBlocking {
        val fund = newFund()
        val id = repo.createLoan("وام", 100_000_000, 3_030_000, 36, "1405/09/01", "1405/08/18", "10:00", fund, " یادداشت ")
        val loan = dao.getLoan(id)!!
        assertEquals("یادداشت", loan.note)
        assertEquals(36, dao.getInstallments(id).size)
        val receipt = dao.transactions.value.single { it.note == "دریافت وام وام" }
        assertEquals(100_000_000L, receipt.amount)
        assertEquals(fund, receipt.debitAccountId)
        assertEquals(loan.accountId, receipt.creditAccountId)
        val fee = dao.transactions.value.single { it.note == "سود و کارمزد وام وام" }
        assertEquals(9_080_000L, fee.amount)
        assertEquals(0L, dao.accounts.value.first { it.id == loan.accountId }.openingBalance)
        payAll(id, fund)
        assertEquals(0L, balances().getValue(loan.accountId!!))
        assertTrue(dao.getLoan(id)!!.isClosed)
    }

    @Test
    fun loanWithoutFundStartsAtMinusTotalAndEndsAtZero() = runBlocking {
        val fund = newFund()
        val id = repo.createLoan("وام ب", 1_000_000, 100_000, 12, "1405/09/01", "1405/08/18", "10:00", null, "")
        val loan = dao.getLoan(id)!!
        assertEquals(-1_200_000L, dao.accounts.value.first { it.id == loan.accountId }.openingBalance)
        assertTrue(dao.transactions.value.isEmpty())
        assertEquals(-1_200_000L, balances().getValue(loan.accountId!!))
        payAll(id, fund)
        assertEquals(0L, balances().getValue(loan.accountId!!))
    }

    @Test
    fun totalLessThanPrincipalIsRejectedWithoutWrites() {
        expectRejected("جمع اقساط نباید کمتر از مبلغ وام باشد") {
            repo.createLoan("وام", 1_000_000, 10_000, 12, "1405/09/01", "1405/08/18", "10:00", null, "")
        }
        assertEquals(0, dao.writes)
        assertTrue(dao.accounts.value.isEmpty() && dao.loans.value.isEmpty() && dao.installments.value.isEmpty())
    }

    @Test
    fun otherValidationErrorsWriteNothing() {
        expectRejected("عنوان وام را وارد کنید") { repo.createLoan("  ", 1, 1, 1, "1405/09/01", "1405/08/18", "10:00", null, "") }
        expectRejected("مبلغ نامعتبر است") { repo.createLoan("a", 0, 1, 1, "1405/09/01", "1405/08/18", "10:00", null, "") }
        expectRejected("تعداد اقساط نامعتبر است") { repo.createLoan("a", 1, 1, 361, "1405/09/01", "1405/08/18", "10:00", null, "") }
        expectRejected("تاریخ اولین قسط نامعتبر است") { repo.createLoan("a", 1, 1, 1, "bad", "1405/08/18", "10:00", null, "") }
        expectRejected("تاریخ دریافت نامعتبر است") { repo.createLoan("a", 1, 1, 1, "1405/09/01", "bad", "10:00", null, "") }
        expectRejected("صندوق پیدا نشد") { repo.createLoan("a", 1, 1, 1, "1405/09/01", "1405/08/18", "10:00", 999L, "") }
        assertEquals(0, dao.writes)
    }

    @Test
    fun existingLiabilityRootIsReused() = runBlocking {
        val root = dao.upsertAccount(AccountEntity(parentId = null, title = "بدهی ها", kindCode = AccountKind.LIABILITY.code))
        val id = repo.createLoan("وام", 1_000_000, 100_000, 12, "1405/09/01", "1405/08/18", "10:00", null, "")
        val loanAccount = dao.accounts.value.first { it.id == dao.getLoan(id)!!.accountId }
        assertEquals(root, loanAccount.parentId)
        assertEquals(1, dao.accounts.value.count { it.parentId == null && it.kindCode == AccountKind.LIABILITY.code })
    }

    @Test
    fun deletingAPaymentReopensInstallmentAndLoan() = runBlocking {
        val fund = newFund()
        val id = repo.createLoan("وام", 1_000_000, 100_000, 12, "1405/09/01", "1405/08/18", "10:00", fund, "")
        payAll(id, fund)
        assertTrue(dao.getLoan(id)!!.isClosed)
        val first = dao.getInstallments(id).first()
        val txId = first.paidTransactionId
        assertNotNull(txId)
        repo.deleteTransaction(txId!!)
        val reopened = dao.getInstallment(first.id)!!
        assertFalse(reopened.isPaid)
        assertEquals(null, reopened.paidTransactionId)
        assertFalse(dao.getLoan(id)!!.isClosed)
    }

    @Test
    fun payingTheSameInstallmentTwiceReturnsTheSameTransaction() = runBlocking {
        val fund = newFund()
        val id = repo.createLoan("وام", 1_000_000, 100_000, 12, "1405/09/01", "1405/08/18", "10:00", fund, "")
        val installment = dao.getInstallments(id).first()
        val first = repo.payInstallment(installment.id, fund, "1405/09/05", "09:00")
        val txCount = dao.transactions.value.size
        val second = repo.payInstallment(installment.id, fund, "1405/09/06", "09:00")
        assertEquals(first, second)
        assertEquals(txCount, dao.transactions.value.size)
    }
}
