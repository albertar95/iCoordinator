package com.icoordinator.accounting

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Reads a Parmis backup (.par, a SQLite file) into plain rows. Mapping is done by [ParmisMapper]. */
object ParmisReader {
    private val REQUIRED_TABLES = listOf("accounts", "Activity", "Installment", "InstDetails")

    fun read(file: File): ParmisData {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            if (!REQUIRED_TABLES.all { hasTable(db, it) }) {
                throw IllegalArgumentException("فایل پشتیبان پارمیس معتبر نیست")
            }
            val accounts = db.rawQuery(
                "SELECT Ac_id, Ac_parent_id, Ac_title, Ac_balance, Ac_enable, ac_icon FROM accounts", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            ParmisAccountRow(
                                id = c.getLong(0),
                                parentId = c.getLong(1),
                                title = c.str(2) ?: "",
                                balance = c.dbl(3),
                                enabled = c.int(4),
                                icon = c.str(5),
                            ),
                        )
                    }
                }
            }
            val activities = db.rawQuery(
                "SELECT Act_id, Act_date, Act_time, Act_amount, PayPath, RecivePath, Act_info FROM Activity", null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            ParmisActivityRow(
                                id = c.getLong(0),
                                date = c.str(1) ?: "",
                                time = c.str(2),
                                amount = c.dbl(3) ?: 0.0,
                                payPath = c.str(4) ?: "",
                                receivePath = c.str(5) ?: "",
                                info = c.str(6),
                            ),
                        )
                    }
                }
            }
            val loans = db.rawQuery(
                "SELECT inst_id, inst_title, inst_amount, inst_dateBegin, inst_DateRecive, inst_pay_accSubChildId, inst_description FROM Installment",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            ParmisLoanRow(
                                id = c.getLong(0),
                                title = c.str(1) ?: "",
                                amount = c.dbl(2) ?: 0.0,
                                dateBegin = c.str(3),
                                dateReceive = c.str(4),
                                payAccountId = c.lng(5),
                                description = c.str(6),
                            ),
                        )
                    }
                }
            }
            val installments = db.rawQuery(
                "SELECT Details_id, Details_instId, Details_date, Details_amount, Details_status, Details_transactionId_pay FROM InstDetails",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            ParmisInstallmentRow(
                                id = c.getLong(0),
                                loanId = c.getLong(1),
                                date = c.str(2) ?: "",
                                amount = c.dbl(3) ?: 0.0,
                                status = c.int(4) ?: 0,
                                payTransactionId = c.lng(5),
                            ),
                        )
                    }
                }
            }
            return ParmisData(accounts, activities, loans, installments)
        } finally {
            db.close()
        }
    }

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { it.moveToFirst() }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
    private fun Cursor.dbl(i: Int): Double? = if (isNull(i)) null else getDouble(i)
    private fun Cursor.int(i: Int): Int? = if (isNull(i)) null else getInt(i)
    private fun Cursor.lng(i: Int): Long? = if (isNull(i)) null else getLong(i)
}
