package com.icoordinator.accounting

data class DueTotals(val paid: Long, val unpaid: Long) { val total: Long get() = paid + unpaid }

data class LoanReportRow(
    val loanId: Long, val title: String,
    val installmentAmount: Long,
    val remainingCount: Int, val totalCount: Int,
    val remainingAmount: Long,
    val nextDueDate: String?,
    val endDate: String?,
    val endEpochDay: Long?,
    val overdue: Boolean,
)

object InstallmentStats {
    /** Installments with fromEpochDay <= dueEpochDay <= toEpochDay: sum of paid and of unpaid amounts. */
    fun dueInRange(installments: List<InstallmentEntity>, fromEpochDay: Long, toEpochDay: Long): DueTotals {
        var paid = 0L
        var unpaid = 0L
        for (i in installments) {
            if (i.dueEpochDay < fromEpochDay || i.dueEpochDay > toEpochDay) continue
            if (i.isPaid) paid += i.amount else unpaid += i.amount
        }
        return DueTotals(paid, unpaid)
    }

    /** One row per loan with isClosed == false, sorted by endEpochDay asc (null last), then title. */
    fun openLoanRows(loans: List<LoanEntity>, installments: List<InstallmentEntity>, todayEpochDay: Long): List<LoanReportRow> {
        val byLoan = installments.groupBy { it.loanId }
        return loans.filter { !it.isClosed }.map { loan ->
            val list = (byLoan[loan.id] ?: emptyList()).sortedWith(compareBy({ it.dueEpochDay }, { it.seq }, { it.id }))
            val unpaid = list.filter { !it.isPaid }
            val next = unpaid.firstOrNull()
            val last = list.lastOrNull()
            LoanReportRow(
                loanId = loan.id,
                title = loan.title,
                installmentAmount = next?.amount ?: last?.amount ?: 0L,
                remainingCount = unpaid.size,
                totalCount = list.size,
                remainingAmount = unpaid.sumOf { it.amount },
                nextDueDate = next?.dueDate,
                endDate = last?.dueDate,
                endEpochDay = last?.dueEpochDay,
                overdue = unpaid.any { it.dueEpochDay < todayEpochDay },
            )
        }.sortedWith(compareBy<LoanReportRow, Long?>(nullsLast()) { it.endEpochDay }.thenBy { it.title })
    }
}
