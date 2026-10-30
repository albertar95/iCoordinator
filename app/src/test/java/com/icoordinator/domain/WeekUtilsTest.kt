package com.icoordinator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class WeekUtilsTest {

    @Test
    fun weekStart_isPreviousSaturday() {
        // 2026-10-30 جمعه است؛ شنبه‌ی همان هفته 2026-10-24 است
        assertEquals(LocalDate.of(2026, 10, 24), WeekUtils.weekStartOf(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun weekStart_ofSaturdayIsItself() {
        assertEquals(LocalDate.of(2026, 10, 24), WeekUtils.weekStartOf(LocalDate.of(2026, 10, 24)))
    }

    @Test
    fun weekDays_returnsSevenDaysFromSaturdayToFriday() {
        val days = WeekUtils.weekDays(LocalDate.of(2026, 10, 24))
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 30), days.last())
    }

    @Test
    fun parseTime_acceptsValidAndRejectsInvalid() {
        assertNotNull(WeekUtils.parseTime("09:05"))
        assertNull(WeekUtils.parseTime("25:00"))
        assertNull(WeekUtils.parseTime("abc"))
    }
}
