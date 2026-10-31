package com.icoordinator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReportTest {

    @Test
    fun score_thresholds() {
        assertEquals(ReportScore.GOOD, ReportScore.of(81))
        assertEquals(ReportScore.MEDIUM, ReportScore.of(80))
        assertEquals(ReportScore.MEDIUM, ReportScore.of(50))
        assertEquals(ReportScore.BAD, ReportScore.of(49))
        assertNull(ReportScore.of(null))
    }

    @Test
    fun buildText_listsUndoneTasksAndScore() {
        val text = WeeklyReport.buildText(
            rangeLabel = "range",
            tasks = listOf("خرید" to true, "ورزش" to false),
            routinesDone = 3,
            routinesTotal = 4,
            percent = 67,
            score = ReportScore.MEDIUM,
        )
        assertTrue(text.contains("تسک‌ها: 1 از 2"))
        assertTrue(text.contains("نمره: متوسط"))
        assertTrue(text.contains("- ورزش"))
    }

    @Test
    fun rangeLabel_usesJalaliDatesAndSpansSevenDays() {
        val start = LocalDate.of(2026, 10, 24)
        val expected = "${PersianCalendar.format(start)} تا ${PersianCalendar.format(start.plusDays(6))}"
        assertEquals(expected, WeeklyReport.rangeLabel(start))
    }
}
