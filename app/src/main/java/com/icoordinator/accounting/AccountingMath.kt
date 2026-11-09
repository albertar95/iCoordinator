package com.icoordinator.accounting

import com.icoordinator.domain.PersianCalendar

enum class TxType { PAY, RECEIVE, TRANSFER, OTHER }

data class PeriodTotals(val income: Long, val expense: Long)
data class CategoryTotal(val accountId: Long, val title: String, val amount: Long)
data class MonthTotals(val year: Int, val month: Int, val income: Long, val expense: Long)
data class LoanSummary(
    val paidAmount: Long, val paidCount: Int,
    val remainingAmount: Long, val remainingCount: Int,
    val nextDue: InstallmentEntity?,   // unpaid installment with the smallest dueEpochDay (ties: smallest seq), or null
)

object AccountingMath {
    /** FUND and BANK are cash accounts. */
    fun isCash(kind: AccountKind): Boolean = kind == AccountKind.FUND || kind == AccountKind.BANK

    /** both cash -> TRANSFER; only debit cash -> RECEIVE; only credit cash -> PAY; neither -> OTHER. */
    fun classify(debitKind: AccountKind, creditKind: AccountKind): TxType {
        val d = isCash(debitKind)
        val c = isCash(creditKind)
        return when {
            d && c -> TxType.TRANSFER
            d -> TxType.RECEIVE
            c -> TxType.PAY
            else -> TxType.OTHER
        }
    }

    /** accountId -> AccountKind.fromCode(kindCode). */
    fun kindsById(accounts: List<AccountEntity>): Map<Long, AccountKind> =
        accounts.associate { it.id to AccountKind.fromCode(it.kindCode) }

    /** For EVERY account in accounts: openingBalance + sum(amount where debitAccountId == id) - sum(amount where creditAccountId == id).
     *  Transactions pointing to ids not in accounts are ignored. */
    fun balances(accounts: List<AccountEntity>, txs: List<TransactionEntity>): Map<Long, Long> {
        val result = HashMap<Long, Long>(accounts.size * 2)
        for (a in accounts) result[a.id] = a.openingBalance
        for (t in txs) {
            result[t.debitAccountId]?.let { result[t.debitAccountId] = it + t.amount }
            result[t.creditAccountId]?.let { result[t.creditAccountId] = it - t.amount }
        }
        return result
    }

    /** For every account: its own value plus the values of all its descendants (by parentId). Cycles must not loop forever. */
    fun rolledUp(accounts: List<AccountEntity>, own: Map<Long, Long>): Map<Long, Long> {
        val children = HashMap<Long, MutableList<Long>>()
        for (a in accounts) {
            val p = a.parentId ?: continue
            children.getOrPut(p) { mutableListOf() }.add(a.id)
        }
        val result = LinkedHashMap<Long, Long>(accounts.size * 2)
        for (a in accounts) {
            val visited = HashSet<Long>()
            val stack = ArrayDeque<Long>()
            stack.addLast(a.id)
            var sum = 0L
            while (stack.isNotEmpty()) {
                val id = stack.removeLast()
                if (!visited.add(id)) continue
                sum += own[id] ?: 0L
                children[id]?.let { list -> list.forEach { stack.addLast(it) } }
            }
            result[a.id] = sum
        }
        return result
    }

    /** Only txs with fromEpochDay <= epochDay <= toEpochDay.
     *  income  = sum(amount where credit account kind == INCOME)  - sum(amount where debit account kind == INCOME)
     *  expense = sum(amount where debit account kind == EXPENSE) - sum(amount where credit account kind == EXPENSE) */
    fun periodTotals(accounts: List<AccountEntity>, txs: List<TransactionEntity>, fromEpochDay: Long, toEpochDay: Long): PeriodTotals {
        val kinds = kindsById(accounts)
        var income = 0L
        var expense = 0L
        for (t in txs) {
            if (t.epochDay < fromEpochDay || t.epochDay > toEpochDay) continue
            val d = kinds[t.debitAccountId]
            val c = kinds[t.creditAccountId]
            if (c == AccountKind.INCOME) income += t.amount
            if (d == AccountKind.INCOME) income -= t.amount
            if (d == AccountKind.EXPENSE) expense += t.amount
            if (c == AccountKind.EXPENSE) expense -= t.amount
        }
        return PeriodTotals(income, expense)
    }

    /** kind must be EXPENSE or INCOME (otherwise empty list). Net amount per category in the range, using the same sign rule as periodTotals.
     *  Category = the ancestor whose parent is the root (depth 1); an account that is itself depth 1 is its own category;
     *  transactions posted directly to the root go to the root itself. Title = that account's title.
     *  Drop categories with amount == 0. Sort by amount descending, then accountId ascending. */
    fun categoryTotals(accounts: List<AccountEntity>, txs: List<TransactionEntity>, kind: AccountKind, fromEpochDay: Long, toEpochDay: Long): List<CategoryTotal> {
        if (kind != AccountKind.EXPENSE && kind != AccountKind.INCOME) return emptyList()
        val byId = accounts.associateBy { it.id }
        val kinds = kindsById(accounts)
        val totals = HashMap<Long, Long>()
        // For EXPENSE the debit side adds; for INCOME the credit side adds.
        val addOnDebit = kind == AccountKind.EXPENSE
        for (t in txs) {
            if (t.epochDay < fromEpochDay || t.epochDay > toEpochDay) continue
            if (kinds[t.debitAccountId] == kind) {
                val cat = categoryOf(t.debitAccountId, byId)
                totals[cat] = (totals[cat] ?: 0L) + (if (addOnDebit) t.amount else -t.amount)
            }
            if (kinds[t.creditAccountId] == kind) {
                val cat = categoryOf(t.creditAccountId, byId)
                totals[cat] = (totals[cat] ?: 0L) + (if (addOnDebit) -t.amount else t.amount)
            }
        }
        return totals.entries
            .filter { it.value != 0L }
            .map { CategoryTotal(it.key, byId[it.key]?.title ?: "", it.value) }
            .sortedWith(compareByDescending<CategoryTotal> { it.amount }.thenBy { it.accountId })
    }

    /** Depth-1 ancestor of [accountId]; the account itself if it is a root, an orphan, or part of a parentId cycle. */
    private fun categoryOf(accountId: Long, byId: Map<Long, AccountEntity>): Long {
        var cur = byId[accountId] ?: return accountId
        val visited = HashSet<Long>()
        while (visited.add(cur.id)) {
            val parentId = cur.parentId ?: return cur.id
            val parent = byId[parentId] ?: return cur.id
            if (parent.parentId == null) return cur.id
            cur = parent
        }
        return cur.id
    }

    /** months Jalali months ending at (endYear, endMonth), oldest first; each uses periodTotals over that month's
     *  first..last day (PersianCalendar.monthStart / monthLength from com.icoordinator.domain). months >= 1. */
    fun monthlySeries(accounts: List<AccountEntity>, txs: List<TransactionEntity>, endYear: Int, endMonth: Int, months: Int): List<MonthTotals> {
        if (months < 1) return emptyList()
        val endIndex = endYear * 12 + (endMonth - 1)
        val result = ArrayList<MonthTotals>(months)
        for (i in months - 1 downTo 0) {
            val index = endIndex - i
            val y = Math.floorDiv(index, 12)
            val m = Math.floorMod(index, 12) + 1
            val first = PersianCalendar.monthStart(y, m).toEpochDay()
            val last = first + PersianCalendar.monthLength(y, m) - 1
            val p = periodTotals(accounts, txs, first, last)
            result.add(MonthTotals(y, m, p.income, p.expense))
        }
        return result
    }

    /** Summary of one loan's installments (caller passes only that loan's rows). */
    fun loanSummary(installments: List<InstallmentEntity>): LoanSummary {
        val paid = installments.filter { it.isPaid }
        val unpaid = installments.filter { !it.isPaid }
        return LoanSummary(
            paidAmount = paid.sumOf { it.amount },
            paidCount = paid.size,
            remainingAmount = unpaid.sumOf { it.amount },
            remainingCount = unpaid.size,
            nextDue = unpaid.minWithOrNull(compareBy<InstallmentEntity> { it.dueEpochDay }.thenBy { it.seq }),
        )
    }

    /** Jalali "yyyy/MM/dd" (Latin or Persian digits, 1-2 digit month/day accepted) -> epoch day; null if invalid. */
    fun epochDayOf(jalaliDate: String): Long? {
        val (y, m, d) = parseParts(jalaliDate) ?: return null
        return PersianCalendar.toGregorian(y, m, d).toEpochDay()
    }

    /** Normalizes to zero-padded Latin "yyyy/MM/dd"; null if invalid. */
    fun normalizeDate(jalaliDate: String): String? {
        val (y, m, d) = parseParts(jalaliDate) ?: return null
        return String.format(java.util.Locale.ROOT, "%04d/%02d/%02d", y, m, d)
    }

    private const val MIN_YEAR = 1000
    private const val MAX_YEAR = 3000

    private fun parseParts(text: String): Triple<Int, Int, Int>? {
        val parts = MoneyFormat.toLatinDigits(text.trim()).split('/')
        if (parts.size != 3) return null
        if (parts.any { p -> p.isEmpty() || p.any { it !in '0'..'9' } }) return null
        if (parts[0].length != 4 || parts[1].length > 2 || parts[2].length > 2) return null
        val y = parts[0].toInt()
        val m = parts[1].toInt()
        val d = parts[2].toInt()
        if (y !in MIN_YEAR..MAX_YEAR || m !in 1..12 || d < 1) return null
        if (d > PersianCalendar.monthLength(y, m)) return null
        return Triple(y, m, d)
    }
}

object MoneyFormat {
    /** 1400000 -> "۱,۴۰۰,۰۰۰"; 0 -> "۰"; -3400000 -> "-۳,۴۰۰,۰۰۰". Persian digits, ASCII ',' every 3 digits. */
    fun format(amount: Long): String {
        val raw = amount.toString()
        val negative = raw.startsWith("-")
        val digits = if (negative) raw.substring(1) else raw
        val sb = StringBuilder()
        if (negative) sb.append('-')
        for ((i, ch) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append('۰' + (ch - '0'))
        }
        return sb.toString()
    }

    /** Accepts Persian/Arabic-Indic/Latin digits, ',' '٬' and spaces, optional leading '-'. Empty/invalid/overflow -> null. */
    fun parse(text: String): Long? {
        val cleaned = toLatinDigits(text).filter { it != ',' && it != '٬' && !it.isWhitespace() }
        val body = if (cleaned.startsWith("-")) cleaned.substring(1) else cleaned
        if (body.isEmpty() || body.any { it !in '0'..'9' }) return null
        return cleaned.toLongOrNull()
    }

    /** Maps Persian (U+06F0..U+06F9) and Arabic-Indic (U+0660..U+0669) digits to ASCII digits. */
    internal fun toLatinDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                },
            )
        }
        return sb.toString()
    }
}
