package com.icoordinator.accounting

import com.icoordinator.domain.PersianCalendar
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

object AccountingFormat {
    /** Latin digits 0-9 -> Persian digits U+06F0..U+06F9; everything else unchanged. */
    fun digits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
        return sb.toString()
    }

    fun weekdayName(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> "شنبه"
        DayOfWeek.SUNDAY -> "یکشنبه"
        DayOfWeek.MONDAY -> "دوشنبه"
        DayOfWeek.TUESDAY -> "سه‌شنبه"
        DayOfWeek.WEDNESDAY -> "چهارشنبه"
        DayOfWeek.THURSDAY -> "پنجشنبه"
        DayOfWeek.FRIDAY -> "جمعه"
    }

    /** "1405/08/15" -> "جمعه - ۱۵ آبان ۱۴۰۵". Invalid -> "". */
    fun dayHeader(jalaliDate: String): String {
        val core = dayCore(jalaliDate) ?: return ""
        return core.first + " - " + core.second
    }

    /** ("1405/08/17", "23:13") -> "یکشنبه ۱۷ آبان ۱۴۰۵، ساعت ۲۳:۱۳". Invalid date -> "". */
    fun longDateTime(jalaliDate: String, time: String): String {
        val core = dayCore(jalaliDate) ?: return ""
        return core.first + " " + core.second + "، ساعت " + digits(time)
    }

    /** Pair(weekday name, "day monthName year" in Persian digits), or null when the date is invalid. */
    private fun dayCore(jalaliDate: String): Pair<String, String>? {
        val norm = AccountingMath.normalizeDate(jalaliDate) ?: return null
        val epoch = AccountingMath.epochDayOf(norm) ?: return null
        val parts = norm.split('/')
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        val weekday = weekdayName(LocalDate.ofEpochDay(epoch))
        return weekday to digits("$d ${PersianCalendar.monthName(m)} $y")
    }

    /** (1405, 8) -> "آبان ۱۴۰۵". */
    fun monthTitle(year: Int, month: Int): String =
        PersianCalendar.monthName(month) + " " + digits(year.toString())

    fun todayJalali(today: LocalDate = LocalDate.now()): String {
        val j = PersianCalendar.toJalali(today)
        return String.format(Locale.ROOT, "%04d/%02d/%02d", j.year, j.month, j.day)
    }

    fun currentMonth(today: LocalDate = LocalDate.now()): Pair<Int, Int> {
        val j = PersianCalendar.toJalali(today)
        return j.year to j.month
    }

    fun monthRange(year: Int, month: Int): LongRange {
        val first = PersianCalendar.monthStart(year, month).toEpochDay()
        return first..(first + PersianCalendar.monthLength(year, month) - 1)
    }

    /** Titles from the root down to the account joined by " - ". Unknown id -> "". Cycle-safe. */
    fun accountPath(accountId: Long, accountsById: Map<Long, AccountEntity>): String =
        chain(accountId, accountsById).reversed().joinToString(" - ") { it.title }

    /** Title of the top-most ancestor. Unknown -> "". */
    fun rootTitle(accountId: Long, accountsById: Map<Long, AccountEntity>): String =
        chain(accountId, accountsById).lastOrNull()?.title ?: ""

    /** Account, its parent, grandparent ... (stops at a missing parent or a repeated id). */
    private fun chain(accountId: Long, byId: Map<Long, AccountEntity>): List<AccountEntity> {
        val out = ArrayList<AccountEntity>()
        val seen = HashSet<Long>()
        var cur: Long? = accountId
        while (cur != null && seen.add(cur)) {
            val acc = byId[cur] ?: break
            out.add(acc)
            cur = acc.parentId
        }
        return out
    }
}

object AmountExpression {
    private val MC = MathContext(40, RoundingMode.HALF_UP)
    private val LONG_MAX = BigDecimal(Long.MAX_VALUE)

    /** See contract: returns null for empty, malformed, division by zero, negative result or overflow. */
    fun evaluate(expression: String): Long? {
        val tokens = tokenize(expression) ?: return null
        if (tokens.isEmpty()) return null
        // expr := term (('+'|'-') term)* ; term := number (('×'|'÷') number)*
        var pos = 0
        fun number(): BigDecimal? = (tokens.getOrNull(pos) as? BigDecimal)?.also { pos++ }
        fun term(): BigDecimal? {
            var acc = number() ?: return null
            while (pos < tokens.size) {
                val op = tokens[pos] as? Char ?: return null
                if (op != '×' && op != '÷') break
                pos++
                val rhs = number() ?: return null
                if (op == '×') {
                    acc = acc.multiply(rhs, MC)
                } else {
                    if (rhs.signum() == 0) return null
                    acc = acc.divide(rhs, MC)
                }
            }
            return acc
        }
        var total = term() ?: return null
        while (pos < tokens.size) {
            val op = tokens[pos] as? Char ?: return null
            if (op != '+' && op != '-') return null
            pos++
            val rhs = term() ?: return null
            total = if (op == '+') total.add(rhs, MC) else total.subtract(rhs, MC)
        }
        if (total.signum() < 0) return null
        val rounded = total.setScale(0, RoundingMode.HALF_UP)
        if (rounded > LONG_MAX) return null
        return rounded.toLong()
    }

    /** Tokens are BigDecimal numbers or operator Chars '+', '-', '×', '÷'. Null when a character is not allowed. */
    private fun tokenize(text: String): List<Any>? {
        val tokens = ArrayList<Any>()
        val num = StringBuilder()
        fun flush(): Boolean {
            if (num.isEmpty()) return true
            val s = num.toString()
            num.setLength(0)
            if (s.none { it in '0'..'9' } || s.count { it == '.' } > 1) return false
            tokens.add(BigDecimal(if (s.startsWith(".")) "0$s" else s))
            return true
        }
        for (ch in text) {
            when {
                ch in '0'..'9' -> num.append(ch)
                ch in '۰'..'۹' -> num.append('0' + (ch - '۰'))
                ch in '٠'..'٩' -> num.append('0' + (ch - '٠'))
                ch == '.' || ch == '٫' -> num.append('.')
                ch == ',' || ch == '٬' || ch.isWhitespace() -> Unit
                ch == '+' || ch == '-' || ch == '−' -> { if (!flush()) return null; tokens.add(if (ch == '+') '+' else '-') }
                ch == '×' || ch == '*' -> { if (!flush()) return null; tokens.add('×') }
                ch == '÷' || ch == '/' -> { if (!flush()) return null; tokens.add('÷') }
                else -> return null
            }
        }
        if (!flush()) return null
        return tokens
    }
}
