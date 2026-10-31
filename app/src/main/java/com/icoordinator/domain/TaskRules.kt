package com.icoordinator.domain

import java.time.LocalDate

enum class RepeatType(val label: String) {
    NONE("بدون تکرار"),
    DAILY("روزانه"),
    WEEKLY("هفتگی"),
}

/** offsetMinutes = null یعنی بدون آلارم */
data class AlarmOption(val label: String, val offsetMinutes: Int?)

val ALARM_OPTIONS = listOf(
    AlarmOption("بدون آلارم", null),
    AlarmOption("لحظه‌ی رسیدن", 0),
    AlarmOption("۵ دقیقه قبل", 5),
    AlarmOption("۱۰ دقیقه قبل", 10),
    AlarmOption("۱۵ دقیقه قبل", 15),
    AlarmOption("۳۰ دقیقه قبل", 30),
    AlarmOption("۱ ساعت قبل", 60),
)

object TaskRules {

    /** سقف تعداد تسک‌هایی که از یک تکرار ساخته می‌شود */
    const val MAX_OCCURRENCES = 366

    /** تاریخ‌های یک تسک تکرارشونده از start تا until (شامل هر دو). */
    fun occurrenceDates(start: LocalDate, repeat: RepeatType, until: LocalDate?): List<LocalDate> {
        if (repeat == RepeatType.NONE || until == null) return listOf(start)
        val step = if (repeat == RepeatType.DAILY) 1L else 7L
        val dates = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(until) && dates.size < MAX_OCCURRENCES) {
            dates.add(d)
            d = d.plusDays(step)
        }
        return dates
    }
}
