package com.icoordinator.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class JalaliDate(val year: Int, val month: Int, val day: Int)

/** تبدیل تاریخ میلادی به شمسی و برعکس، بدون وابستگی به کتابخانه‌ی خارجی. */
object PersianCalendar {

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    fun toJalali(date: LocalDate): JalaliDate {
        val gy = date.year
        val gm = date.monthValue
        val gd = date.dayOfMonth
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd +
            intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)[gm - 1]

        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }

        return if (days < 186) {
            JalaliDate(jy, 1 + days / 31, 1 + days % 31)
        } else {
            JalaliDate(jy, 7 + (days - 186) / 30, 1 + (days - 186) % 30)
        }
    }

    /** تاریخ میلادی روز مشخص شمسی */
    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        val target = ord(jy, 1, 1)
        // نوروز تقریباً ۲۱ مارس است؛ از آنجا به اول فروردین همان سال می‌رسیم
        var d = LocalDate.of(jy + 621, 3, 21)
        while (ord(toJalali(d)) > target) d = d.minusDays(1)
        while (ord(toJalali(d)) < target) d = d.plusDays(1)

        val offset = if (jm <= 6) (jm - 1) * 31 + (jd - 1) else 186 + (jm - 7) * 30 + (jd - 1)
        return d.plusDays(offset.toLong())
    }

    fun format(date: LocalDate): String {
        val j = toJalali(date)
        return "%04d/%02d/%02d".format(j.year, j.month, j.day)
    }

    /** ورودی به شکل ۱۴۰۵/۰۸/۱۰؛ اگر نامعتبر باشد null. */
    fun parse(text: String): LocalDate? {
        val parts = text.trim().split("/")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d < 1 || d > monthLength(y, m)) return null
        return toGregorian(y, m, d)
    }

    fun monthStart(jy: Int, jm: Int): LocalDate = toGregorian(jy, jm, 1)

    fun monthLength(jy: Int, jm: Int): Int {
        val start = monthStart(jy, jm)
        val next = if (jm == 12) monthStart(jy + 1, 1) else monthStart(jy, jm + 1)
        return ChronoUnit.DAYS.between(start, next).toInt()
    }

    fun monthName(jm: Int): String = MONTH_NAMES[jm - 1]

    private fun ord(j: JalaliDate): Int = ord(j.year, j.month, j.day)

    private fun ord(y: Int, m: Int, d: Int): Int = y * 400 + m * 31 + d
}
