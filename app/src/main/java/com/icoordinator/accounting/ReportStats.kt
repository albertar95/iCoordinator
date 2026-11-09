package com.icoordinator.accounting

import java.time.LocalDate

data class MonthAmount(val year: Int, val month: Int, val amount: Long)
data class AccountMonthRow(val year: Int, val month: Int, val inflow: Long, val outflow: Long, val net: Long, val closingBalance: Long)
data class CategoryChange(val accountId: Long, val title: String, val current: Long, val previous: Long)
data class NetWorth(val assets: Long, val liabilities: Long, val net: Long)

/** Pure data functions for the statistical report tabs. */
object ReportStats {

    /** Subtree flow per Jalali month of [year]; see contract. Always 12 rows. */
    fun accountMonthly(accounts: List<AccountEntity>, txs: List<TransactionEntity>, accountId: Long, year: Int): List<AccountMonthRow> {
        val subtree = subtreeIds(accounts, accountId)
        val opening = accounts.filter { it.id in subtree }.sumOf { it.openingBalance }
        val sorted = txs.sortedBy { it.epochDay }
        val rows = ArrayList<AccountMonthRow>(12)
        for (m in 1..12) {
            val range = AccountingFormat.monthRange(year, m)
            var inflow = 0L
            var outflow = 0L
            var before = 0L // net of all transactions up to the last day of the month
            if (subtree.isNotEmpty()) {
                for (t in sorted) {
                    if (t.epochDay > range.last) break
                    val d = t.debitAccountId in subtree
                    val c = t.creditAccountId in subtree
                    val signed = if (d && !c) t.amount else if (c && !d) -t.amount else 0L
                    before += signed
                    if (t.epochDay >= range.first) {
                        if (d && !c) inflow += t.amount
                        if (c && !d) outflow += t.amount
                    }
                }
            }
            rows.add(AccountMonthRow(year, m, inflow, outflow, inflow - outflow, opening + before))
        }
        return rows
    }

    /** Monthly amount of an account's subtree, signed by its kind, oldest first. */
    fun categoryTrend(accounts: List<AccountEntity>, txs: List<TransactionEntity>, accountId: Long, endYear: Int, endMonth: Int, months: Int): List<MonthAmount> {
        val root = accounts.firstOrNull { it.id == accountId }
        val subtree = subtreeIds(accounts, accountId)
        val creditPositive = root != null && AccountKind.fromCode(root.kindCode) == AccountKind.INCOME
        return monthSteps(endYear, endMonth, months).map { (y, m) ->
            val range = AccountingFormat.monthRange(y, m)
            var debitSide = 0L
            var creditSide = 0L
            if (subtree.isNotEmpty()) {
                for (t in txs) {
                    if (t.epochDay < range.first || t.epochDay > range.last) continue
                    val d = t.debitAccountId in subtree
                    val c = t.creditAccountId in subtree
                    if (d && !c) debitSide += t.amount
                    if (c && !d) creditSide += t.amount
                }
            }
            MonthAmount(y, m, if (creditPositive) creditSide - debitSide else debitSide - creditSide)
        }
    }

    /** Category totals of this month vs. the previous month, biggest change first. */
    fun monthComparison(accounts: List<AccountEntity>, txs: List<TransactionEntity>, kind: AccountKind, year: Int, month: Int): List<CategoryChange> {
        if (kind != AccountKind.EXPENSE && kind != AccountKind.INCOME) return emptyList()
        val cur = AccountingFormat.monthRange(year, month)
        val prevIndex = year * 12 + (month - 1) - 1
        val prev = AccountingFormat.monthRange(Math.floorDiv(prevIndex, 12), Math.floorMod(prevIndex, 12) + 1)
        val a = AccountingMath.categoryTotals(accounts, txs, kind, cur.first, cur.last)
        val b = AccountingMath.categoryTotals(accounts, txs, kind, prev.first, prev.last)
        val current = a.associate { it.accountId to it.amount }
        val previous = b.associate { it.accountId to it.amount }
        val titles = HashMap<Long, String>()
        a.forEach { titles[it.accountId] = it.title }
        b.forEach { titles.getOrPut(it.accountId) { it.title } }
        return (current.keys + previous.keys)
            .map { id -> CategoryChange(id, titles[id] ?: "", current[id] ?: 0L, previous[id] ?: 0L) }
            .sortedWith(compareByDescending<CategoryChange> { Math.abs(it.current - it.previous) }.thenBy { it.accountId })
    }

    /** Month-end total of own balances of all FUND and BANK accounts. */
    fun cashTrend(accounts: List<AccountEntity>, txs: List<TransactionEntity>, endYear: Int, endMonth: Int, months: Int): List<MonthAmount> {
        val cash = accounts.filter { AccountingMath.isCash(AccountKind.fromCode(it.kindCode)) }
        return monthSteps(endYear, endMonth, months).map { (y, m) ->
            val upTo = AccountingFormat.monthRange(y, m).last
            val bal = AccountingMath.balances(cash, txs.filter { it.epochDay <= upTo })
            MonthAmount(y, m, bal.values.sum())
        }
    }

    /** Assets / liabilities / net from own balances up to [upToEpochDay]. */
    fun netWorth(accounts: List<AccountEntity>, txs: List<TransactionEntity>, upToEpochDay: Long): NetWorth {
        val bal = AccountingMath.balances(accounts, txs.filter { it.epochDay <= upToEpochDay })
        var assets = 0L
        var liabilities = 0L
        for (a in accounts) {
            val b = bal[a.id] ?: 0L
            when (AccountKind.fromCode(a.kindCode)) {
                AccountKind.FUND, AccountKind.BANK, AccountKind.PERSON -> {
                    if (b > 0) assets += b else liabilities += -b
                }
                AccountKind.DEBTOR, AccountKind.ASSET -> if (b > 0) assets += b
                AccountKind.CREDITOR, AccountKind.LIABILITY -> if (b < 0) liabilities += -b
                else -> Unit
            }
        }
        return NetWorth(assets, liabilities, assets - liabilities)
    }

    /** NetWorth.net at each month end, oldest first. */
    fun netWorthTrend(accounts: List<AccountEntity>, txs: List<TransactionEntity>, endYear: Int, endMonth: Int, months: Int): List<MonthAmount> =
        monthSteps(endYear, endMonth, months).map { (y, m) ->
            MonthAmount(y, m, netWorth(accounts, txs, AccountingFormat.monthRange(y, m).last).net)
        }

    /** Unpaid installment amounts per month starting at (fromYear, fromMonth). */
    fun installmentForecast(installments: List<InstallmentEntity>, fromYear: Int, fromMonth: Int, months: Int): List<MonthAmount> {
        if (months < 1) return emptyList()
        val start = fromYear * 12 + (fromMonth - 1)
        val unpaid = installments.filter { !it.isPaid }
        return (0 until months).map { i ->
            val idx = start + i
            val y = Math.floorDiv(idx, 12)
            val m = Math.floorMod(idx, 12) + 1
            val range = AccountingFormat.monthRange(y, m)
            MonthAmount(y, m, unpaid.filter { it.dueEpochDay in range }.sumOf { it.amount })
        }
    }

    /** Sum of unpaid installments due before [todayEpochDay]. */
    fun overdueTotal(installments: List<InstallmentEntity>, todayEpochDay: Long): Long =
        installments.filter { !it.isPaid && it.dueEpochDay < todayEpochDay }.sumOf { it.amount }

    /** Average expense per weekday (index 0 = Saturday ... 6 = Friday) in [from, to]. */
    fun weekdayAverages(accounts: List<AccountEntity>, txs: List<TransactionEntity>, fromEpochDay: Long, toEpochDay: Long): List<Long> {
        val totals = LongArray(7)
        val counts = IntArray(7)
        if (toEpochDay >= fromEpochDay) {
            for (day in fromEpochDay..toEpochDay) counts[weekdayIndex(day)]++
        }
        val kinds = AccountingMath.kindsById(accounts)
        for (t in txs) {
            if (t.epochDay < fromEpochDay || t.epochDay > toEpochDay) continue
            val i = weekdayIndex(t.epochDay)
            if (kinds[t.debitAccountId] == AccountKind.EXPENSE) totals[i] += t.amount
            if (kinds[t.creditAccountId] == AccountKind.EXPENSE) totals[i] -= t.amount
        }
        return (0 until 7).map { if (counts[it] == 0) 0L else totals[it] / counts[it] }
    }

    /** Biggest transactions of the kind in [from, to]; amount desc, id desc, at most [limit]. */
    fun topTransactions(accounts: List<AccountEntity>, txs: List<TransactionEntity>, kind: AccountKind, fromEpochDay: Long, toEpochDay: Long, limit: Int): List<TransactionEntity> {
        if (kind != AccountKind.EXPENSE && kind != AccountKind.INCOME) return emptyList()
        if (limit <= 0) return emptyList()
        val kinds = AccountingMath.kindsById(accounts)
        return txs.filter { t ->
            t.epochDay in fromEpochDay..toEpochDay &&
                (if (kind == AccountKind.EXPENSE) kinds[t.debitAccountId] == kind else kinds[t.creditAccountId] == kind)
        }.sortedWith(compareByDescending<TransactionEntity> { it.amount }.thenByDescending { it.id }).take(limit)
    }

    /** 0 = Saturday ... 6 = Friday. */
    private fun weekdayIndex(epochDay: Long): Int = (LocalDate.ofEpochDay(epochDay).dayOfWeek.value + 1) % 7

    /** (year, month) pairs, oldest first, ending at (endYear, endMonth). */
    private fun monthSteps(endYear: Int, endMonth: Int, months: Int): List<Pair<Int, Int>> {
        if (months < 1) return emptyList()
        val end = endYear * 12 + (endMonth - 1)
        return (months - 1 downTo 0).map { i ->
            val idx = end - i
            Math.floorDiv(idx, 12) to Math.floorMod(idx, 12) + 1
        }
    }

    /** The account and all descendants (cycle-safe); empty when the id is unknown. */
    private fun subtreeIds(accounts: List<AccountEntity>, accountId: Long): Set<Long> {
        if (accounts.none { it.id == accountId }) return emptySet()
        val children = HashMap<Long, MutableList<Long>>()
        for (a in accounts) {
            val p = a.parentId ?: continue
            children.getOrPut(p) { mutableListOf() }.add(a.id)
        }
        val out = HashSet<Long>()
        val stack = ArrayDeque<Long>()
        stack.addLast(accountId)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!out.add(id)) continue
            children[id]?.forEach { stack.addLast(it) }
        }
        return out
    }
}
