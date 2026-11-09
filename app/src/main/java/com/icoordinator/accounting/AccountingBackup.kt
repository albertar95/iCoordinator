package com.icoordinator.accounting

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.icoordinator.ICoordinatorApp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * پشتیبان‌گیری و بازیابی دیتابیس حسابداری (accounting.db) با کپی‌ی مستقیم فایل، جدا از پشتیبان برنامه (D66).
 * به دیتابیس تسک‌ها و آلارم‌های تسک دست نمی‌زند.
 */
class AccountingBackup(private val app: ICoordinatorApp) {

    /** true once the accounting database was closed; the app must then be restarted (even if the restore failed). */
    @Volatile
    var needsRestart: Boolean = false
        private set

    fun suggestedFileName(now: LocalDateTime = LocalDateTime.now()): String =
        "iCoordinator-accounting-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".db"

    /** null یعنی موفق؛ در غیر این صورت پیام خطای فارسی. */
    suspend fun export(target: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // move the WAL content into the main file so the copy is complete
            app.accountingDatabase.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)")
                .use { it.moveToFirst() }
            val dbFile = app.getDatabasePath(AccountingDatabase.DB_NAME)
            val output = app.contentResolver.openOutputStream(target, "w")
                ?: return@withContext "ساخت فایل پشتیبان ممکن نشد."
            output.use { out ->
                dbFile.inputStream().use { input -> input.copyTo(out) }
            }
            null
        } catch (e: Exception) {
            "پشتیبان‌گیری حسابداری ناموفق بود."
        }
    }

    /** null یعنی موفق (فراخوانی‌کننده باید بعد از آن BackupManager.restartApp را صدا بزند). */
    suspend fun restore(source: Uri): String? = withContext(Dispatchers.IO) {
        val temp = File(app.cacheDir, "accounting-restore-temp.db")
        try {
            temp.delete()
            val input = app.contentResolver.openInputStream(source)
                ?: return@withContext "خواندن فایل انتخاب‌شده ممکن نشد."
            input.use { inp -> temp.outputStream().use { out -> inp.copyTo(out) } }

            // validate BEFORE touching the live database
            val problem = validate(temp)
            if (problem != null) return@withContext problem

            val dbFile = app.getDatabasePath(AccountingDatabase.DB_NAME)
            val dir = dbFile.parentFile ?: return@withContext "مسیر پایگاه داده پیدا نشد."
            val backupCopy = File(dir, "$DB_NAME.before-restore")
            val newFile = File(dir, "$DB_NAME.new")
            val wal = File(dir, "$DB_NAME-wal")
            val shm = File(dir, "$DB_NAME-shm")
            val journal = File(dir, "$DB_NAME-journal")

            // the new file is prepared next to the live one and synced to disk first
            newFile.delete()
            temp.inputStream().use { inp ->
                FileOutputStream(newFile).use { out ->
                    inp.copyTo(out)
                    out.flush()
                    out.fd.sync()
                }
            }

            app.accountingDatabase.close()
            needsRestart = true

            dbFile.inputStream().use { inp -> backupCopy.outputStream().use { out -> inp.copyTo(out) } }
            wal.delete()
            shm.delete()
            journal.delete()
            dbFile.delete()
            if (!newFile.renameTo(dbFile)) {
                // replacing failed: put the previous content back
                backupCopy.inputStream().use { inp -> dbFile.outputStream().use { out -> inp.copyTo(out) } }
                throw IOException("rename failed")
            }
            null
        } catch (e: Exception) {
            if (needsRestart) {
                "بازیابی ناموفق بود؛ داده‌ی قبلی برگردانده شد و برنامه دوباره باز می‌شود."
            } else {
                "بازیابی ناموفق بود؛ داده‌ی فعلی تغییری نکرده است."
            }
        } finally {
            temp.delete()
            File(app.getDatabasePath(AccountingDatabase.DB_NAME).parentFile, "$DB_NAME.new").delete()
        }
    }

    /** Persian error message, or null when the file is a valid accounting backup. */
    private fun validate(file: File): String? {
        val invalid = "فایل انتخاب‌شده پشتیبان حسابداری iCoordinator نیست."
        try {
            val header = ByteArray(16)
            val read = file.inputStream().use { it.read(header) }
            if (read != 16 || String(header, Charsets.ISO_8859_1) != SQLITE_HEADER) return invalid

            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val ok = db.rawQuery("PRAGMA quick_check", null).use { c ->
                    c.moveToFirst() && c.getString(0) == "ok"
                }
                if (!ok) return "فایل پشتیبان آسیب دیده است."
                val tables = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                        "('acc_account','acc_transaction','acc_loan','acc_installment')",
                    null,
                ).use { it.count }
                if (tables != 4) return invalid
                val version = db.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else -1
                }
                if (version != 1) return "نسخه‌ی این پشتیبان پشتیبانی نمی‌شود."
            }
        } catch (e: Exception) {
            return invalid
        }
        return null
    }

    private companion object {
        const val DB_NAME = AccountingDatabase.DB_NAME
        const val SQLITE_HEADER = "SQLite format 3\u0000"
    }
}
