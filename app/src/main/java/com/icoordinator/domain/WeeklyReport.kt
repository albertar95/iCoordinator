package com.icoordinator.domain

import java.time.LocalDate

/** نمره‌ی هفته: بالای ۸۰٪ خوب، ۵۰ تا ۸۰٪ متوسط، زیر ۵۰٪ بد. */
enum class ReportScore(val label: String) {
    GOOD("خوب"),
    MEDIUM("متوسط"),
    BAD("بد");

    companion object {
        fun of(percent: Int?): ReportScore? = when {
            percent == null -> null
            percent > 80 -> GOOD
            percent >= 50 -> MEDIUM
            else -> BAD
        }
    }
}

/** ساخت متن گزارش هفته (بدون اینترنت و بدون مدل زبانی). */
object WeeklyReport {

    fun rangeLabel(weekStart: LocalDate): String {
        val end = weekStart.plusDays(6)
        return "${PersianCalendar.format(weekStart)} تا ${PersianCalendar.format(end)}"
    }

    fun buildText(
        rangeLabel: String,
        tasks: List<Pair<String, Boolean>>,
        routinesDone: Int,
        routinesTotal: Int,
        percent: Int?,
        score: ReportScore?,
    ): String = buildString {
        appendLine("گزارش هفته $rangeLabel")
        appendLine("تسک‌ها: ${tasks.count { it.second }} از ${tasks.size} انجام شد.")
        appendLine("روتین‌ها: $routinesDone از $routinesTotal انجام شد.")

        if (percent == null || score == null) {
            append("این هفته تسک یا روتینی برای ارزیابی ندارد.")
            return@buildString
        }

        appendLine("درصد انجام: $percent٪ - نمره: ${score.label}")

        val undone = tasks.filter { !it.second }.map { it.first }
        if (undone.isNotEmpty()) {
            appendLine("تسک‌های انجام‌نشده:")
            undone.forEach { appendLine("- $it") }
        }

        append(
            when (score) {
                ReportScore.GOOD -> "عالی بود! همین ریتم را ادامه بده."
                ReportScore.MEDIUM -> "خوب پیش می‌روی. تسک‌های باقی‌مانده را در هفته‌ی بعد زودتر شروع کن."
                ReportScore.BAD -> "این هفته کمتر از نیمه انجام شد. تسک‌ها را کوچک‌تر کن و از اول هفته شروع کن."
            },
        )
    }
}
