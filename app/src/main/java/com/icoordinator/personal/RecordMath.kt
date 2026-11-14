package com.icoordinator.personal

import com.icoordinator.accounting.AccountingFormat
import java.math.BigDecimal
import java.math.RoundingMode

/** Pure math/format/validation helpers of the record tracker (all records: higher is better). */
object RecordMath {
    private const val DECIMAL_SEP = '٫'
    private const val THOUSANDS_SEP = '٬'

    /** floor(current/target*100) clamped 0..100; target <= 0 or NaN -> 0. */
    fun percent(current: Double, target: Double): Int {
        if (target.isNaN() || target <= 0.0 || current.isNaN()) return 0
        if (current <= 0.0) return 0
        if (target.isInfinite()) return 0
        if (current.isInfinite()) return 100
        // BigDecimal avoids binary floating point errors such as 29/100*100 = 28.999...
        val p = BigDecimal.valueOf(current).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(target), 0, RoundingMode.FLOOR)
        return p.min(BigDecimal(100)).max(BigDecimal.ZERO).toInt()
    }

    /** Accepts Persian/Arabic/Latin digits, '٫' '.' '/' as decimal point, ',' '٬' as thousands; null when invalid. */
    fun parse(text: String): Double? {
        val sb = StringBuilder()
        var seenDecimal = false
        var digitCount = 0
        for ((i, raw) in text.trim().withIndex()) {
            val ch = when (raw) {
                in '۰'..'۹' -> '0' + (raw - '۰')
                in '٠'..'٩' -> '0' + (raw - '٠')
                else -> raw
            }
            when {
                ch in '0'..'9' -> { sb.append(ch); digitCount++ }
                ch == '.' || ch == DECIMAL_SEP || ch == '/' -> {
                    if (seenDecimal) return null
                    seenDecimal = true
                    sb.append('.')
                }
                ch == ',' || ch == THOUSANDS_SEP -> {}
                ch == '-' && i == 0 -> sb.append('-')
                else -> return null
            }
        }
        if (digitCount == 0) return null
        val v = sb.toString().toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    /** Persian digits, up to 2 decimals (half up), no trailing zeros, '٬' thousands, '٫' decimal point. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return AccountingFormat.digits("0")
        var bd = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
        if (bd.signum() == 0) return AccountingFormat.digits("0")
        bd = bd.stripTrailingZeros()
        val negative = bd.signum() < 0
        val plain = bd.abs().toPlainString()
        val dot = plain.indexOf('.')
        val intPart = if (dot < 0) plain else plain.substring(0, dot)
        val frac = if (dot < 0) "" else plain.substring(dot + 1)
        val grouped = StringBuilder()
        intPart.forEachIndexed { i, c ->
            if (i > 0 && (intPart.length - i) % 3 == 0) grouped.append(THOUSANDS_SEP)
            grouped.append(c)
        }
        val out = StringBuilder()
        if (negative) out.append('-')
        out.append(grouped)
        if (frac.isNotEmpty()) out.append(DECIMAL_SEP).append(frac)
        return AccountingFormat.digits(out.toString())
    }

    fun validateTarget(text: String): String? {
        if (text.isBlank()) return "مقدار هدف را وارد کن"
        val v = parse(text) ?: return "عدد معتبر نیست"
        return if (v <= 0.0) "هدف باید بیشتر از صفر باشد" else null
    }

    fun validateCurrent(text: String): String? {
        if (text.isBlank()) return "مقدار فعلی را وارد کن"
        val v = parse(text) ?: return "عدد معتبر نیست"
        return if (v < 0.0) "مقدار نمی‌تواند منفی باشد" else null
    }
}
