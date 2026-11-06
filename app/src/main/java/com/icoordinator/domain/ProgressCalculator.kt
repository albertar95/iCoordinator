package com.icoordinator.domain

import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.TaskEntity

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

    /** D25: هدف انجام‌شده ۱۰۰؛ وگرنه جمع سهم تسک‌های انجام‌شده‌ی همین هدف، حداکثر ۱۰۰. */
    fun goalProgress(goal: GoalEntity, tasks: List<TaskEntity>): Int {
        if (goal.isDone) return MAX_TOTAL
        val sum = tasks
            .filter { it.goalId == goal.id && it.isDone }
            .sumOf { it.goalContribution ?: 0 }
        return minOf(MAX_TOTAL, sum)
    }

    /** D19: جمع (وزن هدف × پیشرفت هدف) / ۱۰۰، بین ۰ تا ۱۰۰. */
    fun projectProgress(goals: List<GoalEntity>, tasks: List<TaskEntity>): Int =
        (goals.sumOf { it.weightPercent * goalProgress(it, tasks) } / 100).coerceIn(0, MAX_TOTAL)
}
