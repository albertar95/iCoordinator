package com.icoordinator.accounting

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Root category of an account. code = value stored in AccountEntity.kindCode. */
enum class AccountKind(val code: Int) {
    EXPENSE(1), INCOME(2), FUND(3), BANK(4), DEBTOR(5), CREDITOR(6),
    ASSET(7), LIABILITY(8), OTHER(10), PERSON(11), CAPITAL(12);

    companion object {
        /** Unknown code -> OTHER. */
        fun fromCode(code: Int): AccountKind = values().firstOrNull { it.code == code } ?: OTHER
    }
}

@Entity(tableName = "acc_account", indices = [Index("parentId")])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,            // null = root account
    val title: String,
    val kindCode: Int,              // AccountKind.code of this account's root
    val openingBalance: Long = 0,   // toman
    val icon: String = "",
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "acc_transaction",
    indices = [Index("epochDay"), Index("debitAccountId"), Index("creditAccountId"), Index("installmentId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,               // Jalali "yyyy/MM/dd", Latin digits, zero-padded, e.g. "1405/02/07"
    val time: String,               // "HH:mm", e.g. "09:05"
    val epochDay: Long,             // java.time.LocalDate.toEpochDay() of the Gregorian equivalent of date
    val amount: Long,               // toman, > 0
    val debitAccountId: Long,       // "به حساب": the side that receives value (expense on a payment, fund on a receipt)
    val creditAccountId: Long,      // "از حساب": the side value leaves
    val note: String = "",
    val installmentId: Long? = null, // set when this transaction pays an installment
)

@Entity(tableName = "acc_loan")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val principal: Long,            // toman
    val receiveDate: String,        // Jalali "yyyy/MM/dd" or "" if unknown
    val firstDueDate: String,       // Jalali "yyyy/MM/dd" or "" if unknown
    val accountId: Long? = null,    // liability account of this loan, if any
    val note: String = "",
    val isClosed: Boolean = false,
)

@Entity(tableName = "acc_installment", indices = [Index("loanId"), Index("dueEpochDay")])
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val seq: Int,                   // 1-based order within the loan, by due date then id
    val dueDate: String,            // Jalali "yyyy/MM/dd"
    val dueEpochDay: Long,
    val amount: Long,               // toman
    val isPaid: Boolean,
    val paidTransactionId: Long? = null,
)
