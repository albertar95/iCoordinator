package com.icoordinator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class RoutineUtilsTest {

    @Test
    fun mask_roundTrips() {
        val days = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY)
        val mask = RoutineUtils.maskOf(days)
        assertEquals(days, RoutineUtils.daysOf(mask).toSet())
    }

    @Test
    fun nextOccurrence_skipsPastTimeTodayAndGoesNextWeek() {
        // 2026-10-30 جمعه ساعت ۲۳:۰۰ است؛ ساعت ۰۹:۰۰ جمعه گذشته، پس هفته‌ی بعد
        val now = LocalDateTime.of(2026, 10, 30, 23, 0)
        val expected = WeekUtils.toMillis(LocalDateTime.of(2026, 11, 6, 9, 0))
        assertEquals(expected, RoutineUtils.nextOccurrence(DayOfWeek.FRIDAY, LocalTime.of(9, 0), now))
    }

    @Test
    fun nextOccurrence_isInFuture() {
        val now = LocalDateTime.now()
        assertTrue(RoutineUtils.nextOccurrence(DayOfWeek.MONDAY, LocalTime.of(7, 0), now) > WeekUtils.toMillis(now))
    }
}
