package com.icoordinator.accounting

import com.icoordinator.domain.PersianCalendar
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountingFormatTest {
    private fun acc(id: Long, parent: Long?, title: String) =
        AccountEntity(id = id, parentId = parent, title = title, kindCode = 1)

    @Test fun digitsConvertsLatinOnly() {
        assertEquals("۱۴۰۵/۰۸ ab:۲۳", AccountingFormat.digits("1405/08 ab:23"))
        assertEquals("", AccountingFormat.digits(""))
    }

    @Test fun weekdayNamesCoverAllDays() {
        val sat = LocalDate.of(2026, 11, 7) // a Saturday
        val names = (0..6).map { AccountingFormat.weekdayName(sat.plusDays(it.toLong())) }
        assertEquals(listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"), names)
    }

    @Test fun weekdayOfKnownJalaliDate() {
        val d = PersianCalendar.toGregorian(1405, 8, 15)
        assertEquals("جمعه", AccountingFormat.weekdayName(d))
    }

    @Test fun dayHeaderExample() {
        assertEquals("جمعه - ۱۵ آبان ۱۴۰۵", AccountingFormat.dayHeader("1405/08/15"))
    }

    @Test fun dayHeaderNoLeadingZero() {
        assertEquals(true, AccountingFormat.dayHeader("1405/09/01").endsWith(" - ۱ آذر ۱۴۰۵"))
    }

    @Test fun dayHeaderInvalid() {
        assertEquals("", AccountingFormat.dayHeader("abc"))
        assertEquals("", AccountingFormat.dayHeader("1405/13/01"))
        assertEquals("", AccountingFormat.dayHeader(""))
    }

    @Test fun longDateTimeExample() {
        assertEquals("یکشنبه ۱۷ آبان ۱۴۰۵، ساعت ۲۳:۱۳", AccountingFormat.longDateTime("1405/08/17", "23:13"))
    }

    @Test fun longDateTimeInvalid() {
        assertEquals("", AccountingFormat.longDateTime("1405/00/17", "23:13"))
    }

    @Test fun monthTitleExample() {
        assertEquals("آبان ۱۴۰۵", AccountingFormat.monthTitle(1405, 8))
    }

    @Test fun todayJalaliZeroPaddedLatin() {
        val g = PersianCalendar.toGregorian(1405, 2, 7)
        assertEquals("1405/02/07", AccountingFormat.todayJalali(g))
    }

    @Test fun currentMonthPair() {
        val g = PersianCalendar.toGregorian(1405, 12, 29)
        assertEquals(1405 to 12, AccountingFormat.currentMonth(g))
    }

    @Test fun monthRangeCoversMonth() {
        val r = AccountingFormat.monthRange(1405, 12)
        assertEquals(PersianCalendar.monthLength(1405, 12).toLong(), r.last - r.first + 1)
        assertEquals(AccountingMath.epochDayOf("1405/12/01"), r.first)
        assertEquals(AccountingMath.epochDayOf("1405/12/${PersianCalendar.monthLength(1405, 12)}"), r.last)
        val m1 = AccountingFormat.monthRange(1405, 1)
        assertEquals(31L, m1.last - m1.first + 1)
    }

    @Test fun accountPathThreeLevels() {
        val m = listOf(acc(1, null, "هزینه ها"), acc(2, 1, "خوراک"), acc(3, 2, "نان")).associateBy { it.id }
        assertEquals("هزینه ها - خوراک - نان", AccountingFormat.accountPath(3, m))
        assertEquals("هزینه ها", AccountingFormat.accountPath(1, m))
        assertEquals("", AccountingFormat.accountPath(99, m))
    }

    @Test fun accountPathCycleSafe() {
        val m = listOf(acc(1, 2, "A"), acc(2, 1, "B")).associateBy { it.id }
        assertEquals("B - A", AccountingFormat.accountPath(1, m))
    }

    @Test fun rootTitleWalksUp() {
        val m = listOf(acc(1, null, "ریشه"), acc(2, 1, "میانی"), acc(3, 2, "برگ")).associateBy { it.id }
        assertEquals("ریشه", AccountingFormat.rootTitle(3, m))
        assertEquals("ریشه", AccountingFormat.rootTitle(1, m))
        assertEquals("", AccountingFormat.rootTitle(7, m))
    }

    @Test fun evaluateExamples() {
        assertEquals(1400000L, AmountExpression.evaluate("1400000"))
        assertEquals(400000L, AmountExpression.evaluate("۲۵۰۰۰۰+۱۵۰۰۰۰"))
        assertEquals(2500L, AmountExpression.evaluate("1000×3-500"))
        assertEquals(3L, AmountExpression.evaluate("10÷4"))
    }

    @Test fun evaluateNullCases() {
        assertNull(AmountExpression.evaluate(""))
        assertNull(AmountExpression.evaluate("5-10"))
        assertNull(AmountExpression.evaluate("7÷0"))
        assertNull(AmountExpression.evaluate("3+"))
        assertNull(AmountExpression.evaluate("+3"))
        assertNull(AmountExpression.evaluate("3++4"))
        assertNull(AmountExpression.evaluate("abc"))
        assertNull(AmountExpression.evaluate("9223372036854775807+1"))
    }

    @Test fun evaluateAlternateSymbolsAndSeparators() {
        assertEquals(6L, AmountExpression.evaluate("2*3"))
        assertEquals(5L, AmountExpression.evaluate("10/2"))
        assertEquals(1500000L, AmountExpression.evaluate("1,000,000+۵۰۰٬۰۰۰"))
        assertEquals(3L, AmountExpression.evaluate("2٫5"))
        assertEquals(14L, AmountExpression.evaluate("2+3×4"))
        assertEquals(1L, AmountExpression.evaluate("0.5"))
    }
}
