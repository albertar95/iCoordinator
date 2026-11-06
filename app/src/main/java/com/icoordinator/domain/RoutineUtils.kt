package com.icoordinator.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** ابزارهای روتین: تبدیل روزهای هفته به ماسک و محاسبه‌ی زمان بعدی. */
object RoutineUtils {

    /** روزهای انتخاب‌شده را به ماسک بیتی تبدیل می‌کند. */
    fun maskOf(days: Collection<DayOfWeek>): Int =
        days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

    fun daysOf(mask: Int): List<DayOfWeek> =
        DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }

    /** برچسب فارسی روزها به ترتیب شنبه تا جمعه. */
    fun daysLabel(mask: Int): String =
        daysOf(mask)
            .sortedBy { (it.value - 6 + 7) % 7 }
            .joinToString("، ") { WeekUtils.persianDayName(it) }

    /** نزدیک‌ترین زمان آینده‌ی این روز هفته و ساعت (به میلی‌ثانیه). */
    fun nextOccurrence(day: DayOfWeek, time: LocalTime, now: LocalDateTime = LocalDateTime.now()): Long {
        val date: LocalDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day))
        var candidate = LocalDateTime.of(date, time)
        if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1)
        return WeekUtils.toMillis(candidate)
    }

    /**
     * زمان فعال‌شدن آلارم روتین (میلی‌ثانیه) = نوبت - offsetMinutes، برای اولین نوبتِ این روز هفته و ساعت
     * (در/بعد از startDate) که زمان آلارمش بعد از now باشد. در عمل همیشه مقدار برمی‌گرداند.
     */
    fun nextAlarm(day: DayOfWeek, time: LocalTime, offsetMinutes: Int, now: LocalDateTime, startDate: LocalDate): Long? {
        val base = if (startDate.isAfter(now.toLocalDate())) startDate else now.toLocalDate()
        var date: LocalDate = base.with(TemporalAdjusters.nextOrSame(day))
        repeat(8) {
            val trigger = LocalDateTime.of(date, time).minusMinutes(offsetMinutes.toLong())
            if (trigger.isAfter(now)) return WeekUtils.toMillis(trigger)
            date = date.plusWeeks(1)
        }
        return null
    }
}
