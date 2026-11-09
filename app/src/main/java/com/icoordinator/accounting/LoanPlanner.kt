package com.icoordinator.accounting

import com.icoordinator.domain.PersianCalendar

object LoanPlanner {
    /** count monthly installments on the Jalali calendar. Installment k (1-based) is due in the month that is
     *  (k - 1) months after firstDueDate's month, on firstDueDate's day clamped to that month's length. */
    fun monthlySchedule(firstDueDate: String, count: Int, amount: Long): List<InstallmentEntity> {
        if (count < 1 || amount <= 0L) return emptyList()
        val normalized = AccountingMath.normalizeDate(firstDueDate) ?: return emptyList()
        val parts = normalized.split('/')
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val startIndex = year * 12 + (month - 1)
        val result = ArrayList<InstallmentEntity>(count)
        for (k in 1..count) {
            val index = startIndex + (k - 1)
            val y = Math.floorDiv(index, 12)
            val m = Math.floorMod(index, 12) + 1
            val d = minOf(day, PersianCalendar.monthLength(y, m))
            val text = String.format(java.util.Locale.ROOT, "%04d/%02d/%02d", y, m, d)
            val epoch = PersianCalendar.toGregorian(y, m, d).toEpochDay()
            result.add(
                InstallmentEntity(
                    id = 0, loanId = 0, seq = k, dueDate = text,
                    dueEpochDay = epoch, amount = amount, isPaid = false,
                ),
            )
        }
        return result
    }

    /** For every open loan: its unpaid installment with the smallest dueEpochDay (ties: smallest seq). */
    fun nextDuePerLoan(loans: List<LoanEntity>, installments: List<InstallmentEntity>): Map<Long, InstallmentEntity> {
        val open = loans.filter { !it.isClosed }.map { it.id }.toHashSet()
        val best = HashMap<Long, InstallmentEntity>()
        for (i in installments) {
            if (i.isPaid || i.loanId !in open) continue
            val cur = best[i.loanId]
            if (cur == null || i.dueEpochDay < cur.dueEpochDay ||
                (i.dueEpochDay == cur.dueEpochDay && i.seq < cur.seq)
            ) best[i.loanId] = i
        }
        return best
    }
}

object ReportMath {
    /** Net amount per exact account of the given kind in the range; zero dropped; amount desc, accountId asc. */
    fun leafTotals(
        accounts: List<AccountEntity>, txs: List<TransactionEntity>, kind: AccountKind,
        fromEpochDay: Long, toEpochDay: Long,
    ): List<CategoryTotal> {
        if (kind != AccountKind.EXPENSE && kind != AccountKind.INCOME) return emptyList()
        val kinds = AccountingMath.kindsById(accounts)
        val byId = accounts.associateBy { it.id }
        val totals = HashMap<Long, Long>()
        val addOnDebit = kind == AccountKind.EXPENSE
        for (t in txs) {
            if (t.epochDay < fromEpochDay || t.epochDay > toEpochDay) continue
            if (kinds[t.debitAccountId] == kind) {
                totals[t.debitAccountId] = (totals[t.debitAccountId] ?: 0L) + (if (addOnDebit) t.amount else -t.amount)
            }
            if (kinds[t.creditAccountId] == kind) {
                totals[t.creditAccountId] = (totals[t.creditAccountId] ?: 0L) + (if (addOnDebit) -t.amount else t.amount)
            }
        }
        return totals.entries
            .filter { it.value != 0L }
            .map { CategoryTotal(it.key, byId[it.key]?.title ?: "", it.value) }
            .sortedWith(compareByDescending<CategoryTotal> { it.amount }.thenBy { it.accountId })
    }

    /** Share of each item in percent of the sum of amounts (sum <= 0 -> all 0.0), same order. */
    fun shares(totals: List<CategoryTotal>): List<Double> {
        val sum = totals.sumOf { it.amount }
        if (sum <= 0L) return totals.map { 0.0 }
        return totals.map { it.amount * 100.0 / sum }
    }

    /** Keeps the first maxSlices - 1 items and merges the rest into "سایر موارد" when totals.size > maxSlices. */
    fun topWithOthers(totals: List<CategoryTotal>, maxSlices: Int): List<CategoryTotal> {
        if (totals.size <= maxSlices) return totals
        val keep = (maxSlices - 1).coerceAtLeast(1)
        val rest = totals.drop(keep)
        return totals.take(keep) + CategoryTotal(-1L, "سایر موارد", rest.sumOf { it.amount })
    }
}
