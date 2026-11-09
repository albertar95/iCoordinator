package com.icoordinator.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParmisMapperTest {

    private fun acc(id: Long, parent: Long, balance: Double? = 0.0, enabled: Int? = null, icon: String? = null) =
        ParmisAccountRow(id, parent, "t$id", balance, enabled, icon)

    private val baseAccounts = listOf(
        acc(1, 0), acc(3, 0), acc(4, 0), acc(99, 0), acc(161, 0),
        acc(10, 1), acc(11, 10),
        acc(30, 3, balance = 1000.4), acc(40, 4),
    )

    private fun act(id: Long, pay: String, recv: String, date: String = "1405/02/07", time: String? = "10:00", amount: Double = 100.0, info: String? = null) =
        ParmisActivityRow(id, date, time, amount, pay, recv, info)

    private fun data(
        activities: List<ParmisActivityRow> = emptyList(),
        loans: List<ParmisLoanRow> = emptyList(),
        installments: List<ParmisInstallmentRow> = emptyList(),
        accounts: List<ParmisAccountRow> = baseAccounts,
    ) = ParmisData(accounts, activities, loans, installments)

    private fun loan(id: Long, begin: String? = "1405/03/01", recv: String? = "1405/01/01", account: Long? = 40, desc: String? = null) =
        ParmisLoanRow(id, "loan$id", 1000.0, begin, recv, account, desc)

    private fun inst(id: Long, loanId: Long, date: String, status: Int = 0, tx: Long? = null) =
        ParmisInstallmentRow(id, loanId, date, 50.0, status, tx)

    @Test
    fun leafIdExamples() {
        assertEquals(195L, ParmisMapper.leafId("3,195,-1"))
        assertEquals(132L, ParmisMapper.leafId("2,36,132"))
        assertEquals(208L, ParmisMapper.leafId("8-167-208"))
        assertEquals(198L, ParmisMapper.leafId("3-198-198"))
        assertNull(ParmisMapper.leafId(""))
    }

    @Test
    fun leafIdIgnoresEmptyPartsAndGarbage() {
        assertEquals(5L, ParmisMapper.leafId("3,5,"))
        assertNull(ParmisMapper.leafId("abc"))
    }

    @Test
    fun rootKindsAreMappedById() {
        val r = ParmisMapper.map(data())
        val kinds = r.accounts.associate { it.id to it.kindCode }
        assertEquals(AccountKind.EXPENSE.code, kinds[1])
        assertEquals(AccountKind.FUND.code, kinds[3])
        assertEquals(AccountKind.BANK.code, kinds[4])
        assertEquals(AccountKind.PERSON.code, kinds[161])
        assertEquals(AccountKind.OTHER.code, kinds[99])
    }

    @Test
    fun rootKindPropagatesToDepthTwoAccount() {
        val r = ParmisMapper.map(data()).accounts.associateBy { it.id }
        assertEquals(AccountKind.EXPENSE.code, r.getValue(11).kindCode)
        assertEquals(10L, r.getValue(11).parentId)
        assertNull(r.getValue(1).parentId)
    }

    @Test
    fun accountFieldsAreConverted() {
        val a = ParmisMapper.map(data(accounts = listOf(acc(3, 0, balance = 1000.6, enabled = 0, icon = null), acc(30, 3, balance = null, enabled = 1, icon = "x"))))
            .accounts.associateBy { it.id }
        assertEquals(0L, a.getValue(3).openingBalance)
        assertFalse(a.getValue(3).isEnabled)
        assertEquals("", a.getValue(3).icon)
        assertEquals(0L, a.getValue(30).openingBalance)
        assertTrue(a.getValue(30).isEnabled)
        assertEquals("x", a.getValue(30).icon)
    }

    @Test
    fun parentCycleDoesNotLoop() {
        val r = ParmisMapper.map(data(accounts = listOf(acc(50, 51), acc(51, 50))))
        assertEquals(2, r.accounts.size)
        assertEquals(AccountKind.OTHER.code, r.accounts[0].kindCode)
    }

    @Test
    fun transactionIsMappedWithPaddedTimeAndNormalizedDate() {
        val r = ParmisMapper.map(data(activities = listOf(act(7, "3,30,-1", "1,10,11", date = "1405/2/7", time = "9:05", amount = 1500.4, info = " خرید "))))
        assertTrue(r.warnings.isEmpty())
        val t = r.transactions.single()
        assertEquals(7L, t.id)
        assertEquals("09:05", t.time)
        assertEquals("1405/02/07", t.date)
        assertEquals(AccountingMath.epochDayOf("1405/02/07"), t.epochDay)
        assertEquals(30L, t.debitAccountId)
        assertEquals(11L, t.creditAccountId)
        assertEquals(1500L, t.amount)
        assertEquals("خرید", t.note)
    }

    @Test
    fun timeNullOrMalformedBecomesMidnight() {
        val r = ParmisMapper.map(
            data(activities = listOf(act(1, "3,30", "1,10", time = null), act(2, "3,30", "1,10", time = "abc"), act(3, "3,30", "1,10", time = "23:59"), act(4, "3,30", "1,10", time = "")))
        )
        assertEquals(listOf("00:00", "00:00", "23:59", "00:00"), r.transactions.map { it.time })
    }

    @Test
    fun transactionWithUnknownAccountIsSkippedWithWarning() {
        val r = ParmisMapper.map(data(activities = listOf(act(42, "3,30", "1,777"), act(43, "3,30", "1,10"))))
        assertEquals(listOf(43L), r.transactions.map { it.id })
        assertEquals(1, r.warnings.size)
        assertTrue(r.warnings[0].contains("42"))
    }

    @Test
    fun transactionSkippedForBadDateAmountOrMissingLeaf() {
        val r = ParmisMapper.map(
            data(activities = listOf(
                act(1, "3,30", "1,10", date = "1405/13/01"),
                act(2, "3,30", "1,10", amount = 0.4),
                act(3, "", "1,10"),
                act(4, "3,30", "1,10"),
            )),
        )
        assertEquals(listOf(4L), r.transactions.map { it.id })
        assertEquals(3, r.warnings.size)
        assertTrue(r.warnings.zip(listOf("1", "2", "3")).all { (w, id) -> w.contains(id) })
    }

    @Test
    fun installmentSeqIsOrderedByDueDateThenId() {
        val r = ParmisMapper.map(
            data(
                loans = listOf(loan(1), loan(2)),
                installments = listOf(
                    inst(10, 1, "1405/05/01"),
                    inst(11, 1, "1405/03/01"),
                    inst(12, 1, "1405/03/01"),
                    inst(20, 2, "1405/01/01"),
                ),
            ),
        )
        val seq = r.installments.associate { it.id to it.seq }
        assertEquals(mapOf(10L to 3, 11L to 1, 12L to 2, 20L to 1), seq)
    }

    @Test
    fun isPaidFollowsStatus() {
        val r = ParmisMapper.map(
            data(loans = listOf(loan(1)), installments = listOf(inst(1, 1, "1405/01/01", status = 0), inst(2, 1, "1405/02/01", status = 1), inst(3, 1, "1405/03/01", status = 3))),
        )
        assertEquals(listOf(false, true, true), r.installments.sortedBy { it.id }.map { it.isPaid })
    }

    @Test
    fun loanIsClosedOnlyWhenItHasInstallmentsAndAllArePaid() {
        val r = ParmisMapper.map(
            data(
                loans = listOf(loan(1), loan(2), loan(3)),
                installments = listOf(
                    inst(1, 1, "1405/01/01", status = 1), inst(2, 1, "1405/02/01", status = 3),
                    inst(3, 2, "1405/01/01", status = 1), inst(4, 2, "1405/02/01", status = 0),
                ),
            ),
        )
        assertEquals(listOf(true, false, false), r.loans.sortedBy { it.id }.map { it.isClosed })
    }

    @Test
    fun loanFieldsAndDateFallbacks() {
        val r = ParmisMapper.map(
            data(loans = listOf(
                loan(1, begin = "1405/3/1", recv = null, account = 40, desc = " d "),
                loan(2, begin = null, recv = null, account = 777),
                loan(3, begin = "bad", recv = "1404/1/5", account = 0),
            )),
        ).loans.associateBy { it.id }
        assertEquals("1405/03/01", r.getValue(1).receiveDate)
        assertEquals("1405/03/01", r.getValue(1).firstDueDate)
        assertEquals(40L, r.getValue(1).accountId)
        assertEquals("d", r.getValue(1).note)
        assertEquals("", r.getValue(2).receiveDate)
        assertEquals("", r.getValue(2).firstDueDate)
        assertNull(r.getValue(2).accountId)
        assertEquals("1404/01/05", r.getValue(3).receiveDate)
        assertEquals("", r.getValue(3).firstDueDate)
        assertNull(r.getValue(3).accountId)
    }

    @Test
    fun paidTransactionIdIsDroppedWhenTransactionIsMissing() {
        val r = ParmisMapper.map(
            data(
                activities = listOf(act(5, "3,30", "1,10")),
                loans = listOf(loan(1)),
                installments = listOf(inst(1, 1, "1405/01/01", status = 1, tx = 5), inst(2, 1, "1405/02/01", status = 1, tx = 6), inst(3, 1, "1405/03/01", status = 1, tx = 0)),
            ),
        )
        val byId = r.installments.associateBy { it.id }
        assertEquals(5L, byId.getValue(1).paidTransactionId)
        assertNull(byId.getValue(2).paidTransactionId)
        assertNull(byId.getValue(3).paidTransactionId)
    }

    @Test
    fun installmentWithUnknownLoanOrBadDateIsSkippedWithWarning() {
        val r = ParmisMapper.map(
            data(loans = listOf(loan(1)), installments = listOf(inst(1, 9, "1405/01/01"), inst(2, 1, "1405/14/01"), inst(3, 1, "1405/01/01"))),
        )
        assertEquals(listOf(3L), r.installments.map { it.id })
        assertEquals(1, r.installments.single().seq)
        assertEquals(2, r.warnings.size)
        assertTrue(r.warnings[0].contains("1"))
        assertTrue(r.warnings[1].contains("2"))
    }

    @Test
    fun parentBalanceIsNotCountedTwice() {
        val accounts = listOf(acc(3, 0, balance = 300.0), acc(30, 3, balance = 100.0), acc(31, 3, balance = 200.0))
        val mapped = ParmisMapper.map(data(accounts = accounts)).accounts
        val byId = mapped.associateBy { it.id }
        assertEquals(0L, byId.getValue(3).openingBalance)
        val rolled = AccountingMath.rolledUp(mapped, AccountingMath.balances(mapped, emptyList()))
        assertEquals(300L, rolled.getValue(3))
    }

    @Test
    fun leafKeepsItsBalance() {
        val accounts = listOf(acc(3, 0, balance = 300.0), acc(30, 3, balance = 100.0), acc(31, 3, balance = 200.4), acc(4, 0, balance = 55.0))
        val byId = ParmisMapper.map(data(accounts = accounts)).accounts.associateBy { it.id }
        assertEquals(100L, byId.getValue(30).openingBalance)
        assertEquals(200L, byId.getValue(31).openingBalance)
        assertEquals(55L, byId.getValue(4).openingBalance)
    }
}
