package com.icoordinator.domain

import com.icoordinator.data.entity.GoalEntity

/** منطق خالص محاسبه‌ی پیشرفت و اعتبارسنجی درصد اهداف (بدون وابستگی به اندروید). */
object ProgressCalculator {

    const val MAX_TOTAL = 100

    /** پیشرفت پروژه = مجموع درصد اهداف تیک‌خورده (بین ۰ تا ۱۰۰). */
    fun progress(goals: List<GoalEntity>): Int =
        goals.filter { it.isDone }.sumOf { it.weightPercent }.coerceIn(0, MAX_TOTAL)

    /** درصد باقی‌مانده‌ای که هنوز می‌شود به اهداف اختصاص داد. */
    fun remaining(currentTotal: Int): Int = (MAX_TOTAL - currentTotal).coerceAtLeast(0)

    /** اگر مقدار نامعتبر باشد پیام خطا برمی‌گرداند، وگرنه null. */
    fun validateNewWeight(currentTotal: Int, newWeight: Int): String? = when {
        newWeight !in 1..MAX_TOTAL -> "درصد باید بین ۱ تا ۱۰۰ باشد"
        newWeight > remaining(currentTotal) ->
            "مجموع درصد اهداف از ۱۰۰ بیشتر می‌شود. باقی‌مانده: ${remaining(currentTotal)}٪"
        else -> null
    }
}
