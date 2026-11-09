package com.icoordinator.accounting

import org.junit.Assert.*
import org.junit.Test

class InstallmentStatsTest {
    private fun inst(loan: Long, seq: Int, day: Long, amount: Long, paid: Boolean) =
        InstallmentEntity(id = loan * 100 + seq, loanId = loan, seq = seq, dueDate = "1405/01/%02d".format(seq), dueEpochDay = day, amount = amount, isPaid = paid)
    private fun loan(id: Long, title: String, closed: Boolean = false) =
        LoanEntity(id = id, title = title, principal = 0, receiveDate = "", firstDueDate = "", isClosed = closed)

    @Test fun paidUnpaidSplit() {
        val l = listOf(inst(1, 1, 10, 3030000, true), inst(1, 2, 40, 3030000, false))
        assertEquals(DueTotals(3030000, 3030000), InstallmentStats.dueInRange(l, 0, 40))
        assertEquals(6060000L, InstallmentStats.dueInRange(l, 0, 40).total)
    }
    @Test fun rangeEdgesInclusive() {
        val l = listOf(inst(1, 1, 10, 5, true), inst(1, 2, 20, 7, false))
        assertEquals(DueTotals(5, 7), InstallmentStats.dueInRange(l, 10, 20))
        assertEquals(DueTotals(0, 0), InstallmentStats.dueInRange(l, 11, 19))
        assertEquals(DueTotals(5, 0), InstallmentStats.dueInRange(l, 0, 10))
        assertEquals(DueTotals(0, 7), InstallmentStats.dueInRange(l, 20, 30))
    }
    @Test fun emptyList() {
        assertEquals(DueTotals(0, 0), InstallmentStats.dueInRange(listOf<InstallmentEntity>(), 0, 100))
    }
    @Test fun closedLoansExcluded() {
        val rows = InstallmentStats.openLoanRows(listOf(loan(1, "a"), loan(2, "b", true)), listOf(inst(1, 1, 10, 5, false), inst(2, 1, 10, 5, false)), 0)
        assertEquals(listOf(1L), rows.map { it.loanId })
    }
    @Test fun rowFields() {
        val ins = listOf(inst(1, 1, 10, 100, true), inst(1, 2, 20, 110, false), inst(1, 3, 30, 120, false))
        val r = InstallmentStats.openLoanRows(listOf(loan(1, "a")), ins, 5).single()
        assertEquals(110L, r.installmentAmount)
        assertEquals(2, r.remainingCount); assertEquals(3, r.totalCount)
        assertEquals(230L, r.remainingAmount)
        assertEquals("1405/01/02", r.nextDueDate)
        assertEquals("1405/01/03", r.endDate); assertEquals(30L, r.endEpochDay)
        assertFalse(r.overdue)
    }
    @Test fun overdueFlag() {
        val ins = listOf(inst(1, 1, 10, 100, false), inst(1, 2, 30, 100, false))
        assertTrue(InstallmentStats.openLoanRows(listOf(loan(1, "a")), ins, 11).single().overdue)
        assertFalse(InstallmentStats.openLoanRows(listOf(loan(1, "a")), ins, 10).single().overdue)
        val paidOld = listOf(inst(1, 1, 10, 100, true), inst(1, 2, 30, 100, false))
        assertFalse(InstallmentStats.openLoanRows(listOf(loan(1, "a")), paidOld, 20).single().overdue)
    }
    @Test fun sortByEndThenTitleNullLast() {
        val loans = listOf(loan(1, "z"), loan(2, "b"), loan(3, "a"), loan(4, "none"))
        val ins = listOf(inst(1, 1, 50, 1, false), inst(2, 1, 20, 1, false), inst(3, 1, 20, 1, false))
        val rows = InstallmentStats.openLoanRows(loans, ins, 0)
        assertEquals(listOf("a", "b", "z", "none"), rows.map { it.title })
    }
    @Test fun loanWithoutInstallments() {
        val r = InstallmentStats.openLoanRows(listOf(loan(1, "a")), listOf<InstallmentEntity>(), 0).single()
        assertEquals(0L, r.installmentAmount); assertEquals(0, r.totalCount); assertEquals(0, r.remainingCount)
        assertEquals(0L, r.remainingAmount)
        assertNull(r.nextDueDate); assertNull(r.endDate); assertNull(r.endEpochDay); assertFalse(r.overdue)
    }
    @Test fun allPaidUsesLastAmount() {
        val ins = listOf(inst(1, 1, 10, 100, true), inst(1, 2, 20, 150, true))
        val r = InstallmentStats.openLoanRows(listOf(loan(1, "a")), ins, 50).single()
        assertEquals(150L, r.installmentAmount); assertEquals(0, r.remainingCount); assertNull(r.nextDueDate)
        assertEquals(20L, r.endEpochDay)
    }
}
