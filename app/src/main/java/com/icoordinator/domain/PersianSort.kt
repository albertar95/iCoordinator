package com.icoordinator.domain

/**
 * مرتب‌سازی الفبایی فارسی (کد JVM خالص، بدون وابستگی به اندروید).
 * ترتیب: ارقام، حروف فارسی، حروف لاتین (بدون حساسیت به بزرگی و کوچکی)، سپس بقیه به ترتیب کد یونیکد.
 */
object PersianSort {

    private const val ALPHABET = "آابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی"
    private const val ZWNJ = '‌'
    private const val HAMZA = '\u0621'
    private val DIACRITICS = '\u064B'..'\u0652'

    private const val DIGIT_BASE = 0
    private const val PERSIAN_BASE = 100
    private const val LATIN_BASE = 1_000
    private const val OTHER_BASE = 10_000

    private fun normalizeChar(c: Char): Char = when (c) {
        'ك' -> 'ک'
        'ي', 'ى' -> 'ی'
        'أ', 'إ' -> 'ا'
        'ة', 'ۀ' -> 'ه'
        'ئ' -> 'ی'
        'ؤ' -> 'و'
        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
        else -> c
    }

    private fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.trim()) {
            if (c == ZWNJ || c == HAMZA || c in DIACRITICS) continue
            sb.append(normalizeChar(c))
        }
        return sb.toString()
    }

    private fun rank(c: Char): Int {
        if (c in '0'..'9') return DIGIT_BASE + (c - '0')
        if (c in '۰'..'۹') return DIGIT_BASE + (c - '۰')
        val p = ALPHABET.indexOf(c)
        if (p >= 0) return PERSIAN_BASE + p
        if (c in 'a'..'z') return LATIN_BASE + (c - 'a')
        if (c in 'A'..'Z') return LATIN_BASE + (c - 'A')
        return OTHER_BASE + c.code
    }

    fun compare(a: String, b: String): Int {
        val x = normalize(a)
        val y = normalize(b)
        val n = minOf(x.length, y.length)
        for (i in 0 until n) {
            val d = rank(x[i]) - rank(y[i])
            if (d != 0) return if (d < 0) -1 else 1
        }
        return x.length.compareTo(y.length)
    }

    val comparator: Comparator<String> = Comparator { a, b -> compare(a, b) }
}
