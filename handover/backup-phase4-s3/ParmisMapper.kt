package com.icoordinator.accounting

data class ParmisAccountRow(val id: Long, val parentId: Long, val title: String, val balance: Double?, val enabled: Int?, val icon: String?)
data class ParmisActivityRow(val id: Long, val date: String, val time: String?, val amount: Double, val payPath: String, val receivePath: String, val info: String?)
data class ParmisLoanRow(val id: Long, val title: String, val amount: Double, val dateBegin: String?, val dateReceive: String?, val payAccountId: Long?, val description: String?)
data class ParmisInstallmentRow(val id: Long, val loanId: Long, val date: String, val amount: Double, val status: Int, val payTransactionId: Long?)
data class ParmisData(
    val accounts: List<ParmisAccountRow>,
    val activities: List<ParmisActivityRow>,
    val loans: List<ParmisLoanRow>,
    val installments: List<ParmisInstallmentRow>,
)
data class ImportResult(
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val loans: List<LoanEntity>,
    val installments: List<InstallmentEntity>,
    val warnings: List<String>,
)

/** Pure mapper from Parmis backup rows to accounting entities. Budgets, patterns, SMS and cheques are not imported. */
object ParmisMapper {
    private val TIME_REGEX = Regex("""^(\d{1,2}):(\d{2})$""")

    /** Parmis root account id -> kind. Any other root is OTHER. */
    private val ROOT_KINDS = mapOf(
        1L to AccountKind.EXPENSE, 2L to AccountKind.INCOME, 3L to AccountKind.FUND, 4L to AccountKind.BANK,
        5L to AccountKind.DEBTOR, 6L to AccountKind.CREDITOR, 7L to AccountKind.ASSET, 8L to AccountKind.LIABILITY,
        10L to AccountKind.OTHER, 161L to AccountKind.PERSON, 162L to AccountKind.CAPITAL,
    )

    /** Last account id in a Parmis path. Separator: ',' if the path contains ',', otherwise '-'.
     *  Parts that are empty or "-1" are dropped. "3,195,-1" -> 195; "2,36,132" -> 132; "8-167-208" -> 208; "3-198-198" -> 198; "" -> null. */
    fun leafId(path: String): Long? {
        if (path.trim() == "-1") return null // D61: lone "-1" means no account
        val separator = if (path.contains(',')) ',' else '-'
        return path.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "-1" }
            .lastOrNull()
            ?.toLongOrNull()
    }

    fun map(data: ParmisData): ImportResult {
        val warnings = mutableListOf<String>()

        // Accounts
        val rowsById = data.accounts.associateBy { it.id }
        val accounts = data.accounts.map { row ->
            val parent = if (row.parentId > 0 && rowsById.containsKey(row.parentId)) row.parentId else null
            AccountEntity(
                id = row.id,
                parentId = parent,
                title = row.title,
                kindCode = kindOf(row, rowsById).code,
                openingBalance = Math.round(row.balance ?: 0.0),
                icon = row.icon ?: "",
                isEnabled = row.enabled == null || row.enabled != 0,
                sortOrder = 0,
            )
        }
        val accountIds = accounts.map { it.id }.toHashSet()

        // Transactions
        val transactions = mutableListOf<TransactionEntity>()
        for (a in data.activities) {
            val debit = leafId(a.payPath)
            val credit = leafId(a.receivePath)
            val date = AccountingMath.normalizeDate(a.date)
            val epochDay = date?.let { AccountingMath.epochDayOf(it) }
            val amount = Math.round(a.amount)
            val reason = when {
                debit == null || credit == null -> "حساب مبدا یا مقصد مشخص نیست"
                debit !in accountIds || credit !in accountIds -> "حساب پیدا نشد"
                date == null || epochDay == null -> "تاریخ نامعتبر است"
                amount <= 0 -> "مبلغ نامعتبر است"
                else -> null
            }
            if (reason != null || debit == null || credit == null || date == null || epochDay == null) {
                warnings.add("تراکنش ${a.id} وارد نشد: $reason")
                continue
            }
            transactions.add(
                TransactionEntity(
                    id = a.id,
                    date = date,
                    time = normalizeTime(a.time),
                    epochDay = epochDay,
                    amount = amount,
                    debitAccountId = debit,
                    creditAccountId = credit,
                    note = a.info?.trim() ?: "",
                ),
            )
        }
        val transactionIds = transactions.map { it.id }.toHashSet()

        // Loans
        val installmentsByLoan = data.installments.groupBy { it.loanId }
        val loans = data.loans.map { row ->
            val all = installmentsByLoan[row.id].orEmpty()
            val begin = row.dateBegin?.let { AccountingMath.normalizeDate(it) }
            val receive = row.dateReceive?.let { AccountingMath.normalizeDate(it) }
            LoanEntity(
                id = row.id,
                title = row.title,
                principal = Math.round(row.amount),
                receiveDate = receive ?: begin ?: "",
                firstDueDate = begin ?: "",
                accountId = row.payAccountId?.takeIf { it > 0 && it in accountIds },
                note = row.description?.trim() ?: "",
                isClosed = all.isNotEmpty() && all.all { it.status != 0 },
            )
        }
        val loanIds = loans.map { it.id }.toHashSet()

        // Installments
        class Pending(val row: ParmisInstallmentRow, val date: String, val epochDay: Long)
        val pending = mutableListOf<Pending>()
        for (r in data.installments) {
            val date = AccountingMath.normalizeDate(r.date)
            val epochDay = date?.let { AccountingMath.epochDayOf(it) }
            if (r.loanId !in loanIds) {
                warnings.add("قسط ${r.id} وارد نشد: وام پیدا نشد")
            } else if (date == null || epochDay == null) {
                warnings.add("قسط ${r.id} وارد نشد: تاریخ نامعتبر است")
            } else {
                pending.add(Pending(r, date, epochDay))
            }
        }
        val seqById = HashMap<Long, Int>()
        pending.groupBy { it.row.loanId }.values.forEach { group ->
            group.sortedWith(compareBy<Pending> { it.epochDay }.thenBy { it.row.id })
                .forEachIndexed { index, p -> seqById[p.row.id] = index + 1 }
        }
        val installments = pending.map { p ->
            InstallmentEntity(
                id = p.row.id,
                loanId = p.row.loanId,
                seq = seqById[p.row.id] ?: 1,
                dueDate = p.date,
                dueEpochDay = p.epochDay,
                amount = Math.round(p.row.amount),
                isPaid = p.row.status != 0,
                paidTransactionId = p.row.payTransactionId?.takeIf { it > 0 && it in transactionIds },
            )
        }

        // D61: link imported payment transactions back to their installment
        // only 1:1 links; a transaction that paid several installments at once stays unlinked
        val installmentByTx = installments.filter { it.paidTransactionId != null }.groupBy { it.paidTransactionId!! }
            .filterValues { it.size == 1 }.mapValues { it.value.single().id }
        val linkedTransactions = transactions.map { tx -> installmentByTx[tx.id]?.let { tx.copy(installmentId = it) } ?: tx }
        return ImportResult(accounts, linkedTransactions, loans, installments, warnings)
    }

    /** Kind of the root reached by walking parentId up; OTHER if the chain loops. */
    private fun kindOf(row: ParmisAccountRow, rowsById: Map<Long, ParmisAccountRow>): AccountKind {
        var cur = row
        val visited = HashSet<Long>()
        while (visited.add(cur.id)) {
            val parent = if (cur.parentId > 0) rowsById[cur.parentId] else null
            if (parent == null) return ROOT_KINDS[cur.id] ?: AccountKind.OTHER
            cur = parent
        }
        return AccountKind.OTHER
    }

    /** "H:mm" or "HH:mm" (hour 0..23, minute 0..59) -> zero-padded "HH:mm"; anything else -> "00:00". */
    private fun normalizeTime(time: String?): String {
        val m = TIME_REGEX.matchEntire(time?.trim() ?: return "00:00") ?: return "00:00"
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return "00:00"
        return "%02d:%02d".format(java.util.Locale.ROOT, h, min)
    }
}
