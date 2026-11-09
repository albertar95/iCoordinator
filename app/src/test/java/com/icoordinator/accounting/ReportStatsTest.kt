package com.icoordinator.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportStatsTest {
    private fun acc(id: Long, parent: Long?, kind: AccountKind, opening: Long = 0, title: String = "a$id") =
        AccountEntity(id = id, parentId = parent, title = title, kindCode = kind.code, openingBalance = opening)

    private fun day(date: String): Long = AccountingMath.epochDayOf(date)!!

    private var nextId = 1L
    private fun tx(date: String, amount: Long, debit: Long, credit: Long, id: Long = nextId++) =
        TransactionEntity(id = id, date = date, time = "10:00", epochDay = day(date), amount = amount, debitAccountId = debit, creditAccountId = credit)

    private fun inst(id: Long, date: String, amount: Long, paid: Boolean = false) =
        InstallmentEntity(id = id, loanId = 1, seq = id.toInt(), dueDate = date, dueEpochDay = day(date), amount = amount, isPaid = paid)

    // accounts: 1 fund(1000), 2 expense root, 3 expense child, 4 income root
    private val accounts = listOf(
        acc(1, null, AccountKind.FUND, 1000, "صندوق"),
        acc(2, null, AccountKind.EXPENSE, 0, "هزینه"),
        acc(3, 2, AccountKind.EXPENSE, 0, "خوراک"),
        acc(4, null, AccountKind.INCOME, 0, "درآمد"),
    )

    @Test fun accountMonthlyExample() {
        val txs = listOf(tx("1405/02/10", 500, 1, 4), tx("1405/03/05", 200, 2, 1))
        val rows = ReportStats.accountMonthly(accounts, txs, 1, 1405)
        assertEquals(12, rows.size)
        assertEquals(AccountMonthRow(1405, 1, 0, 0, 0, 1000), rows[0])
        assertEquals(AccountMonthRow(1405, 2, 500, 0, 500, 1500), rows[1])
        assertEquals(AccountMonthRow(1405, 3, 0, 200, -200, 1300), rows[2])
        assertEquals(1300L, rows[11].closingBalance)
    }

    @Test fun accountMonthlyIgnoresInternalMoveInSubtree() {
        val txs = listOf(tx("1405/02/10", 70, 3, 2), tx("1405/02/11", 30, 3, 1))
        val rows = ReportStats.accountMonthly(accounts, txs, 2, 1405)
        assertEquals(30L, rows[1].inflow)
        assertEquals(0L, rows[1].outflow)
        assertEquals(30L, rows[1].net)
    }

    @Test fun accountMonthlyClosingIncludesEarlierYears() {
        val txs = listOf(tx("1404/06/01", 300, 1, 4), tx("1405/01/15", 50, 2, 1))
        val rows = ReportStats.accountMonthly(accounts, txs, 1, 1405)
        assertEquals(1250L, rows[0].closingBalance)
        assertEquals(-50L, rows[0].net)
    }

    @Test fun accountMonthlyUnknownAccountGivesZeroRows() {
        val rows = ReportStats.accountMonthly(accounts, emptyList(), 99, 1405)
        assertEquals(12, rows.size)
        assertTrue(rows.all { it.inflow == 0L && it.outflow == 0L && it.net == 0L && it.closingBalance == 0L })
    }

    @Test fun categoryTrendExpenseAndYearBoundary() {
        val txs = listOf(tx("1404/12/10", 100, 3, 1), tx("1405/01/02", 40, 3, 1), tx("1405/01/03", 10, 1, 3), tx("1405/01/04", 9, 3, 2))
        val r = ReportStats.categoryTrend(accounts, txs, 2, 1405, 2, 4)
        assertEquals(listOf(1404 to 11, 1404 to 12, 1405 to 1, 1405 to 2), r.map { it.year to it.month })
        assertEquals(listOf(0L, 100L, 30L, 0L), r.map { it.amount })
    }

    @Test fun categoryTrendIncomeSign() {
        val txs = listOf(tx("1405/01/02", 500, 1, 4), tx("1405/01/03", 20, 4, 1))
        val r = ReportStats.categoryTrend(accounts, txs, 4, 1405, 1, 1)
        assertEquals(480L, r[0].amount)
    }

    @Test fun categoryTrendBackwardsAcrossYears() {
        val r = ReportStats.categoryTrend(accounts, emptyList(), 2, 1405, 2, 14)
        assertEquals(14, r.size)
        assertEquals(1404 to 1, r.first().year to r.first().month)
        assertEquals(1405 to 2, r.last().year to r.last().month)
    }

    @Test fun monthComparisonUnionAndOrder() {
        val txs = listOf(
            tx("1405/08/05", 100, 3, 1),            // current: category 3
            tx("1405/07/05", 400, 3, 1),            // previous: category 3
            tx("1405/07/06", 50, 2, 1),             // previous: category 2 (root, posted directly)
        )
        val r = ReportStats.monthComparison(accounts, txs, AccountKind.EXPENSE, 1405, 8)
        assertEquals(listOf(3L, 2L), r.map { it.accountId })
        assertEquals(CategoryChange(3, "خوراک", 100, 400), r[0])
        assertEquals(CategoryChange(2, "هزینه", 0, 50), r[1])
        assertTrue(ReportStats.monthComparison(accounts, txs, AccountKind.FUND, 1405, 8).isEmpty())
    }

    @Test fun monthComparisonJanuaryUsesPreviousYearEsfand() {
        val txs = listOf(tx("1404/12/20", 70, 3, 1))
        val r = ReportStats.monthComparison(accounts, txs, AccountKind.EXPENSE, 1405, 1)
        assertEquals(listOf(CategoryChange(3, "خوراک", 0, 70)), r)
    }

    @Test fun cashTrendMonthEnd() {
        val txs = listOf(tx("1405/02/10", 500, 1, 4), tx("1405/03/05", 200, 2, 1))
        val r = ReportStats.cashTrend(accounts, txs, 1405, 3, 3)
        assertEquals(listOf(1000L, 1500L, 1300L), r.map { it.amount })
    }

    @Test fun netWorthSignRules() {
        val accs = listOf(
            acc(1, null, AccountKind.FUND, 100),
            acc(2, null, AccountKind.BANK, -30),
            acc(3, null, AccountKind.DEBTOR, 50),
            acc(4, null, AccountKind.DEBTOR, -999),      // negative debtor ignored
            acc(5, null, AccountKind.CREDITOR, -200),    // owed 200
            acc(6, null, AccountKind.CREDITOR, 77),      // positive creditor ignored
            acc(7, null, AccountKind.PERSON, 40),
            acc(8, null, AccountKind.PERSON, -10),
            acc(9, null, AccountKind.EXPENSE, -500),
            acc(10, null, AccountKind.ASSET, 1000),
            acc(11, null, AccountKind.LIABILITY, -300),
        )
        val nw = ReportStats.netWorth(accs, emptyList(), Long.MAX_VALUE)
        assertEquals(100L + 50 + 40 + 1000, nw.assets)
        assertEquals(30L + 200 + 10 + 300, nw.liabilities)
        assertEquals(nw.assets - nw.liabilities, nw.net)
    }

    @Test fun netWorthRespectsUpToDay() {
        val txs = listOf(tx("1405/02/10", 500, 1, 4))
        assertEquals(1000L, ReportStats.netWorth(accounts, txs, day("1405/02/09")).net)
        assertEquals(1500L, ReportStats.netWorth(accounts, txs, day("1405/02/10")).net)
    }

    @Test fun netWorthTrendOldestFirst() {
        val txs = listOf(tx("1405/02/10", 500, 1, 4))
        val r = ReportStats.netWorthTrend(accounts, txs, 1405, 3, 3)
        assertEquals(listOf(1000L, 1500L, 1500L), r.map { it.amount })
        assertEquals(1405 to 1, r[0].year to r[0].month)
    }

    @Test fun installmentForecastSkipsPaidAndCrossesYear() {
        val ins = listOf(
            inst(1, "1405/12/10", 100), inst(2, "1405/12/20", 50, paid = true),
            inst(3, "1406/01/01", 70), inst(4, "1406/03/01", 5),
        )
        val r = ReportStats.installmentForecast(ins, 1405, 12, 3)
        assertEquals(listOf(1405 to 12, 1406 to 1, 1406 to 2), r.map { it.year to it.month })
        assertEquals(listOf(100L, 70L, 0L), r.map { it.amount })
    }

    @Test fun overdueTotalOnlyUnpaidBeforeToday() {
        val ins = listOf(inst(1, "1405/08/01", 10), inst(2, "1405/08/02", 20, paid = true), inst(3, "1405/08/15", 40), inst(4, "1405/09/01", 80))
        assertEquals(10L, ReportStats.overdueTotal(ins, day("1405/08/15")))
        assertEquals(50L, ReportStats.overdueTotal(ins, day("1405/08/16")))
    }

    @Test fun weekdayAveragesKnownFriday() {
        // 1405/08/15 is a Friday. Range 1405/08/09 (Saturday) .. 1405/08/22 (Friday): 14 days, each weekday twice.
        val txs = listOf(tx("1405/08/15", 600, 3, 1), tx("1405/08/22", 200, 3, 1), tx("1405/08/09", 100, 3, 1), tx("1405/08/10", 40, 1, 3))
        val r = ReportStats.weekdayAverages(accounts, txs, day("1405/08/09"), day("1405/08/22"))
        assertEquals(7, r.size)
        assertEquals(400L, r[6])      // Friday: (600 + 200) / 2
        assertEquals(50L, r[0])       // Saturday: 100 / 2
        assertEquals(-20L, r[1])      // Sunday: refund -40 / 2
        assertEquals(0L, r[3])
    }

    @Test fun weekdayAveragesEmptyRangeIsZero() {
        assertEquals(List(7) { 0L }, ReportStats.weekdayAverages(accounts, emptyList(), 10, 5))
    }

    @Test fun topTransactionsLimitAndOrder() {
        val txs = listOf(
            tx("1405/08/01", 100, 3, 1, id = 1), tx("1405/08/02", 300, 3, 1, id = 2),
            tx("1405/08/03", 300, 2, 1, id = 3), tx("1405/08/04", 50, 3, 1, id = 4),
            tx("1405/08/05", 999, 1, 4, id = 5),        // not an expense
            tx("1405/09/01", 700, 3, 1, id = 6),        // out of range
        )
        val r = ReportStats.topTransactions(accounts, txs, AccountKind.EXPENSE, day("1405/08/01"), day("1405/08/30"), 3)
        assertEquals(listOf(3L, 2L, 1L), r.map { it.id })
        val inc = ReportStats.topTransactions(accounts, txs, AccountKind.INCOME, day("1405/08/01"), day("1405/08/30"), 10)
        assertEquals(listOf(5L), inc.map { it.id })
        assertTrue(ReportStats.topTransactions(accounts, txs, AccountKind.FUND, 0, Long.MAX_VALUE, 5).isEmpty())
    }
}
