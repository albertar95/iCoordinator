package com.icoordinator.accounting

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, TransactionEntity::class, LoanEntity::class, InstallmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AccountingDatabase : RoomDatabase() {
    abstract fun accountingDao(): AccountingDao
    companion object { const val DB_NAME = "accounting.db" }
}
