package com.icoordinator.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TaskRulesTest {

    private val sat = LocalDate.of(2026, 11, 7)

    @Test
    fun weeklyMultiDay_returnsEverySelectedDayInRange() {
        val dates = TaskRules.occurrenceDates(
            start = sat,
            repeat = RepeatType.WEEKLY,
            until = LocalDate.of(2026, 11, 21),
            weekDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY),
        )
        val expected = listOf(
            LocalDate.of(2026, 11, 7),
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 14),
            LocalDate.of(2026, 11, 16),
            LocalDate.of(2026, 11, 21),
        )
        assertEquals(expected, dates)
    }

    @Test
    fun weeklyWithEmptyWeekDays_equalsOldWeeklyResult() {
        val until = LocalDate.of(2026, 12, 5)
        val old = TaskRules.occurrenceDates(sat, RepeatType.WEEKLY, until)
        val new = TaskRules.occurrenceDates(sat, RepeatType.WEEKLY, until, emptySet())
        assertEquals(old, new)
        assertEquals(5, new.size)
    }

    @Test
    fun none_returnsOnlyStart() {
        val dates = TaskRules.occurrenceDates(
            start = sat,
            repeat = RepeatType.NONE,
            until = LocalDate.of(2026, 12, 31),
            weekDays = setOf(DayOfWeek.MONDAY),
        )
        assertEquals(listOf(sat), dates)
    }

    @Test
    fun noUntil_returnsOnlyStart() {
        val dates = TaskRules.occurrenceDates(sat, RepeatType.DAILY, null, emptySet())
        assertEquals(listOf(sat), dates)
    }

    @Test
    fun daily_ignoresWeekDays() {
        val dates = TaskRules.occurrenceDates(
            start = sat,
            repeat = RepeatType.DAILY,
            until = LocalDate.of(2026, 11, 9),
            weekDays = setOf(DayOfWeek.MONDAY),
        )
        assertEquals(3, dates.size)
    }

    @Test
    fun weeklyMultiDay_isCappedAtMaxOccurrences() {
        val dates = TaskRules.occurrenceDates(
            start = sat,
            repeat = RepeatType.WEEKLY,
            until = sat.plusYears(10),
            weekDays = DayOfWeek.values().toSet(),
        )
        assertEquals(TaskRules.MAX_OCCURRENCES, dates.size)
    }

    @Test
    fun dailyThreeArgument_isCappedAtMaxOccurrences() {
        val dates = TaskRules.occurrenceDates(sat, RepeatType.DAILY, sat.plusYears(10))
        assertEquals(TaskRules.MAX_OCCURRENCES, dates.size)
    }
}
