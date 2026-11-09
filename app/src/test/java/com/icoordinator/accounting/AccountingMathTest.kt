package com.icoordinator.accounting

import com.icoordinator.domain.PersianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingMathTest {

    // Roots: 1 expense, 2 income, 3 fund root, 4 bank root, 8 liability root
    private val accounts = listOf(
        AccountEntity(id = 1, parentId = null, title = "هزینه", kindCode = 1),
        AccountEntity(id = 10, parentId = 1, title = "خوراک", kindCode = 1),
        AccountEntity(id = 11, parentId = 10, title = "رستوران", kindCode = 1),
        AccountEntity(id = 12, parentId = 1, title = "حمل و نقل", kindCode = 1),
        AccountEntity(id = 2, parentId = null, title = "درآمد", kindCode = 2),
        AccountEntity(id = 20, parentId = 2, title = "حقوق", kindCode = 2),
        AccountEntity(id = 3, parentId = null, title = "صندوق", kindCode = 3),
        AccountEntity(id = 30, parentId = 3, title = "نقد", kindCode = 3, openingBalance = 110_000_000),
        AccountEntity(id = 31, parentId = 3, title = "بانک", kindCode = 4, openingBalance = 5_000),
        AccountEntity(id = 8, parentId = null, title = "بدهی", kindCode = 8),
        AccountEntity(id = 80, parentId = 8, title = "وام", kindCode = 8),
    )

    private fun tx(id: Long, epochDay: Long, amount: Long, debit: Long, credit: Long) = TransactionEntity(
        id = id, date = "1405/01/01", time = "00:00", epochDay = epochDay,
        amount = amount, debitAccountId = debit, creditAccountId = credit,
    )

    private fun inst(id: Long, seq: Int, day: Long, amount: Long, paid: Boolean) = InstallmentEntity(
        id = id, loanId = 1, seq = seq, dueDate = "1405/01/01", dueEpochDay = day, amount = amount, isPaid = paid,
    )

    @Test
    fun fundWithOpeningBalanceThatPaidHasRemainingBalance() {
        val b = AccountingMath.balances(accounts, listOf(tx(1, 100, 103_400_000, debit = 10, credit = 30)))
        assertEquals(6_600_000L, b[30])
        assertEquals(103_400_000L, b[10])
    }

    @Test
    fun balancesCoverEveryAccountAndIgnoreUnknownIds() {
        val b = AccountingMath.balances(accounts, listOf(tx(1, 100, 50, debit = 999, credit = 30)))
        assertEquals(accounts.size, b.size)
        assertEquals(0L, b[1])
        assertEquals(110_000_000L - 50, b[30])
        assertNull(b[999])
    }

    @Test
    fun transferChangesBothFundsAndNoIncomeOrExpense() {
        val t = tx(1, 100, 1_000, debit = 31, credit = 30)
        val b = AccountingMath.balances(accounts, listOf(t))
        assertEquals(110_000_000L - 1_000, b[30])
        assertEquals(5_000L + 1_000, b[31])
        val p = AccountingMath.periodTotals(accounts, listOf(t), 0, 1000)
        assertEquals(PeriodTotals(0, 0), p)
    }

    @Test
    fun periodTotalsCountsIncomeAndExpenseWithinRangeOnly() {
        val txs = listOf(
            tx(1, 100, 700, debit = 30, credit = 20),   // income 700
            tx(2, 101, 300, debit = 11, credit = 30),   // expense 300
            tx(3, 500, 999, debit = 11, credit = 30),   // outside range
        )
        assertEquals(PeriodTotals(700, 300), AccountingMath.periodTotals(accounts, txs, 100, 101))
    }

    @Test
    fun refundToExpenseAccountReducesExpense() {
        val txs = listOf(
            tx(1, 100, 1_000, debit = 10, credit = 30),
            tx(2, 101, 400, debit = 30, credit = 10),   // refund
        )
        assertEquals(600L, AccountingMath.periodTotals(accounts, txs, 0, 1000).expense)
    }

    @Test
    fun categoryTotalsGroupsDepthTwoUnderDepthOneParent() {
        val txs = listOf(
            tx(1, 100, 500, debit = 11, credit = 30),   // depth 2 under 10
            tx(2, 100, 200, debit = 10, credit = 30),   // depth 1 itself
            tx(3, 100, 900, debit = 12, credit = 30),
            tx(4, 100, 50, debit = 1, credit = 30),     // posted to the root
        )
        val r = AccountingMath.categoryTotals(accounts, txs, AccountKind.EXPENSE, 0, 1000)
        assertEquals(
            listOf(
                CategoryTotal(12, "حمل و نقل", 900),
                CategoryTotal(10, "خوراک", 700),
                CategoryTotal(1, "هزینه", 50),
            ),
            r,
        )
    }

    @Test
    fun categoryTotalsIncomeDropsZeroAndRejectsOtherKinds() {
        val txs = listOf(
            tx(1, 100, 500, debit = 30, credit = 20),
            tx(2, 100, 500, debit = 20, credit = 30),   // reversal -> net 0
        )
        assertTrue(AccountingMath.categoryTotals(accounts, txs, AccountKind.INCOME, 0, 1000).isEmpty())
        assertTrue(AccountingMath.categoryTotals(accounts, txs, AccountKind.FUND, 0, 1000).isEmpty())
    }

    @Test
    fun categoryTotalsTieBreaksByAccountId() {
        val txs = listOf(
            tx(1, 100, 100, debit = 12, credit = 30),
            tx(2, 100, 100, debit = 10, credit = 30),
        )
        val r = AccountingMath.categoryTotals(accounts, txs, AccountKind.EXPENSE, 0, 1000)
        assertEquals(listOf(10L, 12L), r.map { it.accountId })
    }

    @Test
    fun rolledUpSumsDescendants() {
        val own = mapOf(1L to 1L, 10L to 10L, 11L to 100L, 12L to 1000L)
        val r = AccountingMath.rolledUp(accounts, own)
        assertEquals(1111L, r[1])
        assertEquals(110L, r[10])
        assertEquals(100L, r[11])
        assertEquals(0L, r[2])
    }

    @Test
    fun rolledUpSurvivesParentCycle() {
        val cyc = listOf(
            AccountEntity(id = 1, parentId = 2, title = "a", kindCode = 1),
            AccountEntity(id = 2, parentId = 1, title = "b", kindCode = 1),
            AccountEntity(id = 3, parentId = 3, title = "self", kindCode = 1),
        )
        val r = AccountingMath.rolledUp(cyc, mapOf(1L to 5L, 2L to 7L, 3L to 9L))
        assertEquals(12L, r[1])
        assertEquals(12L, r[2])
        assertEquals(9L, r[3])
    }

    @Test
    fun classifyCoversAllCombinations() {
        assertEquals(TxType.PAY, AccountingMath.classify(AccountKind.EXPENSE, AccountKind.FUND))
        assertEquals(TxType.RECEIVE, AccountingMath.classify(AccountKind.BANK, AccountKind.INCOME))
        assertEquals(TxType.TRANSFER, AccountingMath.classify(AccountKind.BANK, AccountKind.FUND))
        assertEquals(TxType.OTHER, AccountingMath.classify(AccountKind.LIABILITY, AccountKind.EXPENSE))
        assertTrue(AccountingMath.isCash(AccountKind.FUND))
        assertTrue(!AccountingMath.isCash(AccountKind.DEBTOR))
    }

    @Test
    fun kindFromCodeAndKindsById() {
        assertEquals(AccountKind.FUND, AccountKind.fromCode(3))
        assertEquals(AccountKind.OTHER, AccountKind.fromCode(99))
        val k = AccountingMath.kindsById(accounts)
        assertEquals(AccountKind.BANK, k[31])
        assertEquals(accounts.size, k.size)
    }

    @Test
    fun monthlySeriesCrossesJalaliYearBoundary() {
        val s = AccountingMath.monthlySeries(accounts, emptyList(), 1405, 2, 3)
        assertEquals(listOf(1404 to 12, 1405 to 1, 1405 to 2), s.map { it.year to it.month })
    }

    @Test
    fun monthlySeriesAssignsTransactionsToTheirMonth() {
        val d12 = PersianCalendar.toGregorian(1404, 12, 29).toEpochDay()
        val d01 = PersianCalendar.toGregorian(1405, 1, 1).toEpochDay()
        val txs = listOf(
            tx(1, d12, 40, debit = 10, credit = 30),
            tx(2, d01, 70, debit = 30, credit = 20),
        )
        val s = AccountingMath.monthlySeries(accounts, txs, 1405, 1, 2)
        assertEquals(MonthTotals(1404, 12, 0, 40), s[0])
        assertEquals(MonthTotals(1405, 1, 70, 0), s[1])
        assertEquals(1, AccountingMath.monthlySeries(accounts, txs, 1405, 1, 1).size)
    }

    @Test
    fun loanSummaryNextDueAndTotals() {
        val list = listOf(
            inst(1, 1, 10, 100, true),
            inst(2, 3, 30, 100, false),
            inst(3, 2, 20, 150, false),
            inst(4, 4, 20, 50, false),
        )
        val s = AccountingMath.loanSummary(list)
        assertEquals(100L, s.paidAmount)
        assertEquals(1, s.paidCount)
        assertEquals(300L, s.remainingAmount)
        assertEquals(3, s.remainingCount)
        assertEquals(3L, s.nextDue?.id)   // day 20, smallest seq among ties (seq 2)
    }

    @Test
    fun loanSummaryAllPaidHasNoNextDue() {
        val s = AccountingMath.loanSummary(listOf(inst(1, 1, 10, 100, true)))
        assertNull(s.nextDue)
        assertEquals(0L, s.remainingAmount)
        assertNull(AccountingMath.loanSummary(emptyList()).nextDue)
    }

    @Test
    fun moneyFormatExamples() {
        assertEquals("۱,۴۰۰,۰۰۰", MoneyFormat.format(1_400_000))
        assertEquals("۰", MoneyFormat.format(0))
        assertEquals("-۳,۴۰۰,۰۰۰", MoneyFormat.format(-3_400_000))
        assertEquals("۱۲۳", MoneyFormat.format(123))
        assertEquals("۱,۰۰۰", MoneyFormat.format(1000))
    }

    @Test
    fun moneyFormatHandlesLongExtremes() {
        assertEquals(Long.MIN_VALUE, MoneyFormat.parse(MoneyFormat.format(Long.MIN_VALUE)))
        assertEquals(Long.MAX_VALUE, MoneyFormat.parse(MoneyFormat.format(Long.MAX_VALUE)))
    }

    @Test
    fun moneyParseAcceptsDigitSystemsAndSeparators() {
        assertEquals(1_400_000L, MoneyFormat.parse("۱,۴۰۰,۰۰۰"))
        assertEquals(1_400_000L, MoneyFormat.parse("١٬٤٠٠٬٠٠٠"))
        assertEquals(1_400_000L, MoneyFormat.parse(" 1 400 000 "))
        assertEquals(-250L, MoneyFormat.parse("-۲۵۰"))
    }

    @Test
    fun moneyParseRejectsInvalid() {
        assertNull(MoneyFormat.parse(""))
        assertNull(MoneyFormat.parse("   "))
        assertNull(MoneyFormat.parse("-"))
        assertNull(MoneyFormat.parse("12a"))
        assertNull(MoneyFormat.parse("1-2"))
        assertNull(MoneyFormat.parse("99999999999999999999"))
    }

    @Test
    fun epochDayOfMatchesPersianCalendar() {
        assertEquals(PersianCalendar.toGregorian(1405, 1, 1).toEpochDay(), AccountingMath.epochDayOf("1405/1/1"))
        assertEquals(PersianCalendar.toGregorian(1405, 2, 7).toEpochDay(), AccountingMath.epochDayOf("۱۴۰۵/۰۲/۰۷"))
        assertEquals(PersianCalendar.toGregorian(1405, 2, 7).toEpochDay(), AccountingMath.epochDayOf("١٤٠٥/٢/٧"))
        assertNull(AccountingMath.epochDayOf("1405/13/01"))
    }

    @Test
    fun normalizeDateExamples() {
        assertEquals("1405/02/07", AccountingMath.normalizeDate("1405/2/7"))
        assertEquals("1405/02/07", AccountingMath.normalizeDate("۱۴۰۵/۲/۷"))
        assertEquals("1405/02/07", AccountingMath.normalizeDate(" 1405/02/07 "))
        assertNull(AccountingMath.normalizeDate("1405/13/01"))
        assertNull(AccountingMath.normalizeDate("1405/00/10"))
        assertNull(AccountingMath.normalizeDate("1405/07/31"))   // Mehr has 30 days
        assertNull(AccountingMath.normalizeDate("1405-02-07"))
        assertNull(AccountingMath.normalizeDate(""))
        assertNull(AccountingMath.normalizeDate("1405/2"))
        assertNull(AccountingMath.normalizeDate("1405/+2/7"))
    }
}
