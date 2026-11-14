package com.icoordinator.personal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.icoordinator.ICoordinatorApp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Image files of notes live in filesDir/note_images. */
object NoteImageFiles {
    const val DIR = "note_images"

    /** The images folder; created when missing. */
    fun dir(context: Context): File {
        val d = File(context.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }
}

/** [NoteImageStore] backed by a folder. Names with a path separator or ".." are ignored. */
class FileNoteImageStore(private val dir: File) : NoteImageStore {
    override fun delete(name: String) {
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..")) return
        File(dir, name).delete()
    }
}

/**
 * پشتیبان‌گیری و بازیابی یادداشت‌ها و رکوردها (personal.db) به‌همراه تصویرهای یادداشت‌ها در یک فایل zip.
 * جدا از پشتیبان برنامه و حسابداری است و به آن‌ها دست نمی‌زند (D77).
 */
class PersonalBackup(private val app: ICoordinatorApp) {

    /** true once the personal database was closed; the app must then be restarted (even if the restore failed). */
    @Volatile
    var needsRestart: Boolean = false
        private set

    fun suggestedFileName(now: LocalDateTime = LocalDateTime.now()): String =
        "iCoordinator-notes-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip"

    /** null یعنی موفق؛ در غیر این صورت پیام خطای فارسی. */
    suspend fun export(target: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // move the WAL content into the main file so the copy is complete
            val liveDb = app.personalDatabase.openHelper.writableDatabase
            liveDb.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            val dbFile = app.getDatabasePath(PersonalDatabase.DB_NAME)
            val output = app.contentResolver.openOutputStream(target, "w")
                ?: return@withContext "ساخت فایل پشتیبان ممکن نشد."
            output.use { out ->
                ZipOutputStream(out).use { zip ->
                    // hold a write transaction (never committed) so no writer can checkpoint while the file is copied
                    liveDb.beginTransaction()
                    try {
                        zip.putNextEntry(ZipEntry(ENTRY_DB))
                        dbFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    } finally {
                        liveDb.endTransaction()
                    }
                    val files = NoteImageFiles.dir(app).listFiles()
                    if (files != null) {
                        for (f in files.sortedBy { it.name }) {
                            if (!f.isFile || !isSimpleFileName(f.name)) continue
                            zip.putNextEntry(ZipEntry("$IMAGES_PREFIX${f.name}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "پشتیبان‌گیری یادداشت‌ها ناموفق بود."
        }
    }

    /** null یعنی موفق (فراخوانی‌کننده باید بعد از آن BackupManager.restartApp را صدا بزند). */
    suspend fun restore(source: Uri): String? = withContext(Dispatchers.IO) {
        val tempZip = File(app.cacheDir, "personal-restore-temp.zip")
        val tempDir = File(app.cacheDir, "personal-restore-dir")
        val dbFile = app.getDatabasePath(PersonalDatabase.DB_NAME)
        val dbDir = dbFile.parentFile
        val liveImages = NoteImageFiles.dir(app)
        val filesDir = liveImages.parentFile
        val newDb = File(dbDir, "$DB_NAME.new")
        val dbBackup = File(dbDir, "$DB_NAME.before-restore")
        val newImages = File(filesDir, "${NoteImageFiles.DIR}.new")
        val oldImages = File(filesDir, "${NoteImageFiles.DIR}.before-restore")
        var staged = false
        try {
            if (dbDir == null || filesDir == null) return@withContext "مسیر پایگاه داده پیدا نشد."
            tempZip.delete()
            tempDir.deleteRecursively()

            // 1. copy the picked file into the cache (size limited) and unzip it to a fresh temp dir
            val input = app.contentResolver.openInputStream(source)
                ?: return@withContext "خواندن فایل انتخاب‌شده ممکن نشد."
            val copied = input.use { inp -> tempZip.outputStream().use { out -> copyLimited(inp, out, MAX_FILE_BYTES) } }
            if (!copied) return@withContext "حجم فایل انتخاب‌شده بیش از حد مجاز (۵۰۰ مگابایت) است."
            if (!tempDir.mkdirs()) return@withContext "بازیابی ناموفق بود؛ داده‌ی فعلی تغییری نکرده است."
            val unzipProblem = unzip(tempZip, tempDir)
            if (unzipProblem != null) return@withContext unzipProblem

            // 2. validate BEFORE touching the live data (the live database is still open and unchanged here)
            val extractedDb = File(tempDir, ENTRY_DB)
            val problem = validate(extractedDb, readLiveIdentityHash())
            if (problem != null) return@withContext problem

            // 3. prepare the replacement files next to the live ones
            newDb.delete()
            extractedDb.inputStream().use { inp ->
                FileOutputStream(newDb).use { out ->
                    inp.copyTo(out)
                    out.flush()
                    out.fd.sync()
                }
            }
            newImages.deleteRecursively()
            if (!newImages.mkdirs()) throw IOException("mkdirs failed")
            File(tempDir, DIR_IMAGES).listFiles()?.forEach { f ->
                if (f.isFile) f.copyTo(File(newImages, f.name), overwrite = true)
            }
            staged = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext "بازیابی ناموفق بود؛ داده‌ی فعلی تغییری نکرده است."
        } finally {
            // every early return and failure in steps 1-3 removes the temp zip, the extraction dir and the staged files
            if (!staged) cleanup(tempZip, tempDir, newDb, newImages)
        }

        // 4. point of no return: close the live database, then swap files
        val dbBackupTmp = File(dbDir, "$DB_NAME.before-restore.tmp")
        var liveTouched = false // true once the live file was deleted or replaced
        try {
            app.personalDatabase.close()
            needsRestart = true

            val wal = File(dbDir, "$DB_NAME-wal")
            val shm = File(dbDir, "$DB_NAME-shm")
            val journal = File(dbDir, "$DB_NAME-journal")

            // copy the live database aside first; if that fails the live file is still untouched, so stop here
            dbBackupTmp.delete()
            dbBackup.delete()
            if (dbFile.exists()) {
                try {
                    dbFile.inputStream().use { inp ->
                        FileOutputStream(dbBackupTmp).use { out ->
                            inp.copyTo(out)
                            out.flush()
                            out.fd.sync()
                        }
                    }
                    if (!dbBackupTmp.renameTo(dbBackup)) throw IOException("backup rename failed")
                } catch (e: Exception) {
                    dbBackupTmp.delete()
                    dbBackup.delete()
                    return@withContext "بازیابی ناموفق بود؛ داده‌ی فعلی تغییری نکرده است."
                }
            }

            liveTouched = true
            wal.delete()
            shm.delete()
            journal.delete()
            dbFile.delete()
            if (!newDb.renameTo(dbFile)) throw IOException("db rename failed")

            // images: old folder is moved aside, the prepared one takes its place
            oldImages.deleteRecursively()
            if (liveImages.exists() && !liveImages.renameTo(oldImages)) throw IOException("images move failed")
            if (!newImages.renameTo(liveImages)) {
                oldImages.renameTo(liveImages)
                throw IOException("images rename failed")
            }
            oldImages.deleteRecursively()
            dbBackup.delete()
            null
        } catch (e: Exception) {
            // put the previous database back only when the live file was already replaced or deleted
            var rolledBack = true
            if (liveTouched && dbBackup.exists()) {
                try {
                    File(dbDir, "$DB_NAME-wal").delete()
                    File(dbDir, "$DB_NAME-shm").delete()
                    dbBackup.inputStream().use { inp -> dbFile.outputStream().use { out -> inp.copyTo(out) } }
                    dbBackup.delete()
                } catch (ignored: Exception) {
                    rolledBack = false // the before-restore file is kept for a manual recovery
                }
            } else if (liveTouched) {
                rolledBack = false // nothing to restore from
            }
            if (rolledBack) {
                "بازیابی ناموفق بود؛ داده‌ی قبلی برگردانده شد و برنامه دوباره باز می‌شود."
            } else {
                "بازیابی انجام نشد و داده‌ی قبلی هم برنگشت؛ برنامه را دوباره باز کن"
            }
        } finally {
            dbBackupTmp.delete()
            cleanup(tempZip, tempDir, newDb, newImages)
        }
    }

    private fun cleanup(tempZip: File, tempDir: File, newDb: File, newImages: File) {
        tempZip.delete()
        tempDir.deleteRecursively()
        newDb.delete()
        newImages.deleteRecursively()
    }

    /** Copies at most [limit] bytes; false when the source is larger. */
    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Boolean {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return true
            total += n
            if (total > limit) return false
            output.write(buffer, 0, n)
        }
    }

    /** Safe entry names only: "personal.db" or "note_images/<simple file name>" (zip-slip protection). Returns a Persian error or null. */
    private fun unzip(zipFile: File, destDir: File): String? {
        val invalid = "فایل انتخاب‌شده پشتیبان یادداشت‌های iCoordinator نیست."
        val destCanonical = destDir.canonicalFile
        val seen = HashSet<String>()
        var entries = 0
        var total = 0L
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (++entries > MAX_ENTRIES) return invalid
                    if (name.contains("..") || name.startsWith("/") || name.contains('\\')) return invalid
                    if (entry.isDirectory) {
                        if (name != IMAGES_PREFIX) return invalid
                        continue
                    }
                    val isDb = name == ENTRY_DB
                    val isImage = name.startsWith(IMAGES_PREFIX) && isSimpleFileName(name.removePrefix(IMAGES_PREFIX))
                    if (!isDb && !isImage) return invalid
                    if (!seen.add(name)) return invalid
                    val target = File(destDir, name)
                    val parent = target.parentFile ?: return invalid
                    if (!target.canonicalPath.startsWith(destCanonical.path + File.separator)) return invalid
                    parent.mkdirs()
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > MAX_FILE_BYTES) return "حجم فایل انتخاب‌شده بیش از حد مجاز (۵۰۰ مگابایت) است."
                            out.write(buffer, 0, n)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            return "فایل پشتیبان آسیب دیده است."
        }
        return if (seen.contains(ENTRY_DB)) null else invalid
    }

    private fun isSimpleFileName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.contains("..") && !name.contains('\u0000')

    /** Persian error message, or null when the file is a valid personal.db backup. */
    private fun validate(file: File, liveIdentityHash: String?): String? {
        val invalid = "فایل انتخاب‌شده پشتیبان یادداشت‌های iCoordinator نیست."
        val incompatible = "این فایل با این نسخه‌ی برنامه سازگار نیست"
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
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('notes','note_folders','records')",
                    null,
                ).use { it.count }
                if (tables != 3) return invalid
                val version = db.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else -1
                }
                if (version != 1) return "نسخه‌ی این پشتیبان پشتیبانی نمی‌شود."
                for ((table, columns) in EXPECTED_COLUMNS) {
                    val have = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                        val names = HashSet<String>()
                        while (c.moveToNext()) names.add(c.getString(1))
                        names
                    }
                    if (!have.containsAll(columns)) return incompatible
                }
                // same Room schema? compared only when both databases have the Room master table
                if (liveIdentityHash != null) {
                    val hasMaster = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'",
                        null,
                    ).use { it.count > 0 }
                    if (hasMaster) {
                        val backupHash = db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null)
                            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        if (backupHash != null && backupHash != liveIdentityHash) return incompatible
                    }
                }
            }
        } catch (e: Exception) {
            return invalid
        }
        return null
    }

    /** identity_hash of the live database (read before it is closed); null when it cannot be read. */
    private fun readLiveIdentityHash(): String? = try {
        app.personalDatabase.openHelper.readableDatabase
            .query("SELECT identity_hash FROM room_master_table WHERE id = 42")
            .use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        val EXPECTED_COLUMNS: Map<String, List<String>> = mapOf(
            "notes" to listOf("id", "folderId", "title", "content", "plainText", "pinned", "createdAt", "updatedAt"),
            "note_folders" to listOf("id", "name", "sortOrder"),
            "records" to listOf("id", "title", "target", "current", "previous", "sortOrder", "updatedAt"),
        )
        const val DB_NAME = PersonalDatabase.DB_NAME
        const val ENTRY_DB = "personal.db"
        const val DIR_IMAGES = NoteImageFiles.DIR
        const val IMAGES_PREFIX = NoteImageFiles.DIR + "/"
        const val SQLITE_HEADER = "SQLite format 3\u0000"
        const val MAX_FILE_BYTES = 500L * 1024 * 1024
        const val MAX_ENTRIES = 20_000
    }
}
