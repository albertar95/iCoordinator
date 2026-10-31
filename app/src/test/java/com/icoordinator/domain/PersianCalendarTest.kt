package com.icoordinator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PersianCalendarTest {

    @Test
    fun roundTrip_forManyDates() {
        var d = LocalDate.of(2025, 1, 1)
        repeat(800) {
            val j = PersianCalendar.toJalali(d)
            assertEquals(d, PersianCalendar.toGregorian(j.year, j.month, j.day))
            d = d.plusDays(1)
        }
    }

    @Test
    fun firstDayOfMonth_isDayOne() {
        val start = PersianCalendar.monthStart(1405, 8)
        assertEquals(1, PersianCalendar.toJalali(start).day)
        assertEquals(8, PersianCalendar.toJalali(start).month)
    }

    @Test
    fun monthLength_isBetween29And31() {
        for (m in 1..12) {
            val len = PersianCalendar.monthLength(1405, m)
            assertTrue("month $m length $len", len in 29..31)
        }
    }

    @Test
    fun parse_rejectsInvalidDay() {
        assertNull(PersianCalendar.parse("1405/07/31"))
        assertNull(PersianCalendar.parse("abc"))
    }

    @Test
    fun parse_acceptsValidDate() {
        val parsed = PersianCalendar.parse("1405/08/10")
        assertEquals("1405/08/10", parsed?.let { PersianCalendar.format(it) })
    }
}
