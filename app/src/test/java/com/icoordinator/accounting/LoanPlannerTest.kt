package com.icoordinator.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanPlannerTest {
    private fun dates(first: String, n: Int) = LoanPlanner.monthlySchedule(first, n, 1000).map { it.dueDate }

    @Test fun clampsDay31() {
        assertEquals(listOf("1405/06/31", "1405/07/30", "1405/08/30"), dates("1405/06/31", 3))
    }

    @Test fun crossesYear() {
        assertEquals(listOf("1405/11/05", "1405/12/05", "1406/01/05"), dates("1405/11/05", 3))
    }

    @Test fun exampleFromContract() {
        assertEquals(listOf("1405/08/01", "1405/09/01"), LoanPlanner.monthlySchedule("1405/08/01", 2, 3030000).map { it.dueDate })
    }

    @Test fun seqAmountsAndDefaults() {
        val s = LoanPlanner.monthlySchedule("1405/01/10", 36, 3030000)
        assertEquals(36, s.size)
        assertEquals((1..36).toList(), s.map { it.seq })
        assertTrue(s.all { it.amount == 3030000L && !it.isPaid && it.id == 0L && it.loanId == 0L })
        assertEquals(36L * 3030000L - 100000000L, 9080000L)
    }

    @Test fun epochDayMatchesDate() {
        for (i in LoanPlanner.monthlySchedule("1405/12/29", 14, 5)) {
            assertEquals(AccountingMath.epochDayOf(i.dueDate), i.dueEpochDay)
        }
    }

    @Test fun epochDaysIncrease() {
        val s = LoanPlanner.monthlySchedule("1405/01/31", 24, 5)
        for (k in 1 until s.size) assertTrue(s[k].dueEpochDay > s[k - 1].dueEpochDay)
    }

    @Test fun invalidInputsGiveEmpty() {
        assertTrue(LoanPlanner.monthlySchedule("garbage", 3, 100).isEmpty())
        assertTrue(LoanPlanner.monthlySchedule("1405/13/01", 3, 100).isEmpty())
        assertTrue(LoanPlanner.monthlySchedule("1405/01/01", 0, 100).isEmpty())
        assertTrue(LoanPlanner.monthlySchedule("1405/01/01", 3, 0).isEmpty())
        assertTrue(LoanPlanner.monthlySchedule("1405/01/01", 3, -5).isEmpty())
    }

    @Test fun acceptsPersianDigits() {
        assertEquals(listOf("1405/02/07"), dates("۱۴۰۵/۲/۷", 1))
    }

    private fun inst(id: Long, loan: Long, seq: Int, day: Long, paid: Boolean = false) =
        InstallmentEntity(id = id, loanId = loan, seq = seq, dueDate = "x", dueEpochDay = day, amount = 10, isPaid = paid)

    @Test fun nextDueIgnoresClosedAndPaid() {
        val loans = listOf(LoanEntity(id = 1, title = "a", principal = 1, receiveDate = "", firstDueDate = ""),
            LoanEntity(id = 2, title = "b", principal = 1, receiveDate = "", firstDueDate = "", isClosed = true),
            LoanEntity(id = 3, title = "c", principal = 1, receiveDate = "", firstDueDate = ""))
        val ins = listOf(inst(1, 1, 1, 100, paid = true), inst(2, 1, 2, 200), inst(3, 1, 3, 300),
            inst(4, 2, 1, 50), inst(5, 3, 1, 10, paid = true))
        val r = LoanPlanner.nextDuePerLoan(loans, ins)
        assertEquals(setOf(1L), r.keys)
        assertEquals(2L, r[1L]!!.id)
    }

    @Test fun nextDueTieBreaksBySeq() {
        val loans = listOf(LoanEntity(id = 1, title = "a", principal = 1, receiveDate = "", firstDueDate = ""))
        val ins = listOf(inst(10, 1, 3, 100), inst(11, 1, 2, 100), inst(12, 1, 4, 100))
        assertEquals(11L, LoanPlanner.nextDuePerLoan(loans, ins)[1L]!!.id)
    }

    @Test fun nextDueSmallestDayNotListOrder() {
        val loans = listOf(LoanEntity(id = 1, title = "a", principal = 1, receiveDate = "", firstDueDate = ""))
        val ins = listOf(inst(1, 1, 2, 300), inst(2, 1, 1, 100))
        assertEquals(2L, LoanPlanner.nextDuePerLoan(loans, ins)[1L]!!.id)
    }

    private val accts = listOf(
        AccountEntity(id = 1, parentId = null, title = "هزینه ها", kindCode = 1),
        AccountEntity(id = 2, parentId = 1, title = "خوراک", kindCode = 1),
        AccountEntity(id = 3, parentId = 2, title = "نان", kindCode = 1),
        AccountEntity(id = 4, parentId = null, title = "درآمد", kindCode = 2),
        AccountEntity(id = 5, parentId = 4, title = "حقوق", kindCode = 2),
        AccountEntity(id = 6, parentId = null, title = "صندوق", kindCode = 3),
    )

    private fun tx(id: Long, debit: Long, credit: Long, amount: Long, day: Long) =
        TransactionEntity(id = id, date = "d", time = "00:00", epochDay = day, amount = amount, debitAccountId = debit, creditAccountId = credit)

    @Test fun leafTotalsExpenseSignAndRange() {
        val txs = listOf(tx(1, 3, 6, 100, 10), tx(2, 2, 6, 300, 11), tx(3, 6, 3, 30, 12), tx(4, 3, 6, 999, 99), tx(5, 3, 6, 5, 9))
        val r = ReportMath.leafTotals(accts, txs, AccountKind.EXPENSE, 10, 12)
        assertEquals(listOf(CategoryTotal(2, "خوراک", 300), CategoryTotal(3, "نان", 70)), r)
    }

    @Test fun leafTotalsIncomeSignAndDropZero() {
        val txs = listOf(tx(1, 6, 5, 500, 10), tx(2, 5, 6, 200, 10), tx(3, 6, 4, 50, 10), tx(4, 4, 6, 50, 10))
        val r = ReportMath.leafTotals(accts, txs, AccountKind.INCOME, 0, 100)
        assertEquals(listOf(CategoryTotal(5, "حقوق", 300)), r)
    }

    @Test fun leafTotalsOtherKindsEmptyAndTieOrder() {
        assertTrue(ReportMath.leafTotals(accts, listOf(tx(1, 6, 5, 5, 1)), AccountKind.FUND, 0, 9).isEmpty())
        val txs = listOf(tx(1, 3, 6, 100, 1), tx(2, 2, 6, 100, 1))
        assertEquals(listOf(2L, 3L), ReportMath.leafTotals(accts, txs, AccountKind.EXPENSE, 0, 9).map { it.accountId })
    }

    @Test fun sharesSumToHundredAndExample() {
        val t = listOf(CategoryTotal(1, "a", 2400000), CategoryTotal(2, "b", 500000), CategoryTotal(3, "c", 500000))
        val s = ReportMath.shares(t)
        assertEquals(70.588, s[0], 0.001)
        assertEquals(14.706, s[1], 0.001)
        assertEquals(100.0, s.sum(), 1e-9)
    }

    @Test fun sharesZeroSum() {
        assertEquals(listOf(0.0, 0.0), ReportMath.shares(listOf(CategoryTotal(1, "a", 0), CategoryTotal(2, "b", 0))))
        assertTrue(ReportMath.shares(emptyList()).isEmpty())
    }

    @Test fun topWithOthersMerges() {
        val t = (1..8).map { CategoryTotal(it.toLong(), "t$it", (100 - it).toLong()) }
        val r = ReportMath.topWithOthers(t, 6)
        assertEquals(6, r.size)
        assertEquals(t.take(5), r.take(5))
        assertEquals(CategoryTotal(-1, "سایر موارد", t.drop(5).sumOf { it.amount }), r[5])
    }

    @Test fun topWithOthersUnchangedWhenSmall() {
        val t = (1..6).map { CategoryTotal(it.toLong(), "t$it", 10) }
        assertEquals(t, ReportMath.topWithOthers(t, 6))
        assertEquals(emptyList<CategoryTotal>(), ReportMath.topWithOthers(emptyList(), 2))
    }
}
