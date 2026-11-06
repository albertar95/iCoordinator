package com.icoordinator.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import com.icoordinator.ICoordinatorApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** پشتیبان‌گیری و بازیابی: کپی‌ی مستقیم فایل پایگاه داده (icoordinator.db). */
class BackupManager(private val app: ICoordinatorApp) {

    /** true وقتی دیتابیس بسته شده است؛ در این حالت برنامه باید دوباره باز شود (حتی اگر بازیابی ناموفق باشد). */
    @Volatile
    var needsRestart: Boolean = false
        private set


    fun suggestedFileName(now: LocalDateTime = LocalDateTime.now()): String =
        "iCoordinator-backup-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".db"

    /** null یعنی موفق؛ در غیر این صورت پیام خطای فارسی. */
    suspend fun export(target: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // محتوای WAL به فایل اصلی منتقل شود تا کپی کامل باشد
            app.database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)")
                .use { it.moveToFirst() }
            val dbFile = app.getDatabasePath(DB_NAME)
            val output = app.contentResolver.openOutputStream(target, "w")
                ?: return@withContext "ساخت فایل پشتیبان ممکن نشد."
            output.use { out ->
                dbFile.inputStream().use { input -> input.copyTo(out) }
            }
            null
        } catch (e: Exception) {
            "پشتیبان‌گیری ناموفق بود."
        }
    }

    /** null یعنی موفق (فراخوانی‌کننده باید بعد از آن restartApp را صدا بزند). */
    suspend fun restore(source: Uri): String? = withContext(Dispatchers.IO) {
        val temp = File(app.cacheDir, "restore-temp.db")
        try {
            temp.delete()
            val input = app.contentResolver.openInputStream(source)
                ?: return@withContext "خواندن فایل انتخاب‌شده ممکن نشد."
            input.use { inp -> temp.outputStream().use { out -> inp.copyTo(out) } }

            val problem = validate(temp)
            if (problem != null) return@withContext problem

            val dbFile = app.getDatabasePath(DB_NAME)
            val dir = dbFile.parentFile ?: return@withContext "مسیر پایگاه داده پیدا نشد."
            val backupCopy = File(dir, "$DB_NAME.before-restore")
            val newFile = File(dir, "$DB_NAME.new")
            val wal = File(dir, "$DB_NAME-wal")
            val shm = File(dir, "$DB_NAME-shm")
            val journal = File(dir, "$DB_NAME-journal")

            // فایل جدید کنار فایل اصلی آماده و روی دیسک ثبت می‌شود (هنوز به دیتابیس فعلی دست نمی‌زنیم)
            newFile.delete()
            temp.inputStream().use { inp ->
                FileOutputStream(newFile).use { out ->
                    inp.copyTo(out)
                    out.flush()
                    out.fd.sync()
                }
            }

            // اگر مرحله‌ای بعد از این شکست بخورد، آلارم‌ها در شروع بعدی دوباره ثبت می‌شوند
            prefs().edit().putBoolean(KEY_RESCHEDULE, true).commit()
            app.alarmRescheduler.cancelAll()
            app.database.close()
            needsRestart = true

            dbFile.inputStream().use { inp -> backupCopy.outputStream().use { out -> inp.copyTo(out) } }
            wal.delete()
            shm.delete()
            journal.delete()
            dbFile.delete()
            if (!newFile.renameTo(dbFile)) {
                // جایگزینی ناموفق: نسخه‌ی قبلی برگردانده می‌شود
                backupCopy.inputStream().use { inp -> dbFile.outputStream().use { out -> inp.copyTo(out) } }
                throw IOException("rename failed")
            }
            null
        } catch (e: Exception) {
            if (needsRestart) "بازیابی ناموفق بود؛ داده‌ی قبلی برگردانده شد و برنامه دوباره باز می‌شود."
            else "بازیابی ناموفق بود؛ داده‌ی فعلی تغییری نکرده است."
        } finally {
            temp.delete()
            File(app.getDatabasePath(DB_NAME).parentFile, "$DB_NAME.new").delete()
        }
    }

    /** پیام خطای فارسی یا null اگر فایل معتبر باشد. */
    private fun validate(file: File): String? {
        val invalid = "فایل انتخاب‌شده پشتیبان معتبر iCoordinator نیست."
        try {
            val header = ByteArray(16)
            val read = file.inputStream().use { it.read(header) }
            if (read != 16 || String(header, Charsets.ISO_8859_1) != SQLITE_HEADER) return invalid

            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val version = db.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else -1
                }
                if (version !in MIN_VERSION..MAX_VERSION) {
                    return "نسخه‌ی این پشتیبان پشتیبانی نمی‌شود."
                }
                val ok = db.rawQuery("PRAGMA quick_check", null).use { c ->
                    c.moveToFirst() && c.getString(0) == "ok"
                }
                if (!ok) return "فایل پشتیبان آسیب دیده است."
                val tables = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                        "('projects','goals','tasks','routines','routine_completions','weekly_reports')",
                    null,
                ).use { it.count }
                if (tables != 6) return invalid
            }
        } catch (e: Exception) {
            return invalid
        }
        return null
    }

    private fun prefs() = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val DB_NAME = "icoordinator.db"
        private const val PREFS_NAME = "app_settings"
        private const val KEY_RESCHEDULE = "reschedule_after_restore"
        private const val SQLITE_HEADER = "SQLite format 3\u0000"
        private const val MIN_VERSION = 5
        private const val MAX_VERSION = 7

        /** برنامه را دوباره باز می‌کند و پردازش فعلی را می‌بندد. */
        fun restartApp(activity: Activity) {
            val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                activity.startActivity(intent)
            }
            Process.killProcess(Process.myPid())
        }

        /** اگر بعد از بازیابی پرچم ثبت شده باشد، آلارم‌ها را دوباره ثبت و پرچم را پاک می‌کند. */
        suspend fun runPendingReschedule(app: ICoordinatorApp) {
            val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_RESCHEDULE, false)) return
            app.alarmRescheduler.rescheduleAll()
            prefs.edit().putBoolean(KEY_RESCHEDULE, false).apply()
        }
    }
}
