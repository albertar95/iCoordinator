package com.icoordinator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
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

    @Test
    fun nextAlarm_offsetZeroEqualsNextOccurrence() {
        val now = LocalDateTime.of(2026, 10, 30, 23, 0)
        val time = LocalTime.of(9, 0)
        val start = LocalDate.of(2026, 1, 1)
        assertEquals(
            RoutineUtils.nextOccurrence(DayOfWeek.FRIDAY, time, now),
            RoutineUtils.nextAlarm(DayOfWeek.FRIDAY, time, 0, now, start),
        )
    }

    @Test
    fun nextAlarm_offsetPassedTodayGoesToNextWeek() {
        // 2026-10-31 شنبه است؛ نوبت ۰۸:۰۰، یادآوری ۳۰ دقیقه قبل (۰۷:۳۰) و now ساعت ۰۷:۵۰ است
        val now = LocalDateTime.of(2026, 10, 31, 7, 50)
        val expected = WeekUtils.toMillis(LocalDateTime.of(2026, 11, 7, 7, 30))
        assertEquals(expected, RoutineUtils.nextAlarm(DayOfWeek.SATURDAY, LocalTime.of(8, 0), 30, now, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun nextAlarm_offsetStillAheadTodayFiresToday() {
        val now = LocalDateTime.of(2026, 10, 31, 7, 0)
        val expected = WeekUtils.toMillis(LocalDateTime.of(2026, 10, 31, 7, 45))
        assertEquals(expected, RoutineUtils.nextAlarm(DayOfWeek.SATURDAY, LocalTime.of(8, 0), 15, now, LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun nextAlarm_respectsFutureStartDate() {
        // 2026-11-16 دوشنبه است؛ اولین چهارشنبه‌ی بعد از آن 2026-11-18 است
        val now = LocalDateTime.of(2026, 10, 30, 12, 0)
        val expected = WeekUtils.toMillis(LocalDateTime.of(2026, 11, 18, 8, 0))
        assertEquals(expected, RoutineUtils.nextAlarm(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), 60, now, LocalDate.of(2026, 11, 16)))
    }
}
