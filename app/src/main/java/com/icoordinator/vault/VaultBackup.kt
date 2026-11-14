package com.icoordinator.vault

import android.net.Uri
import com.icoordinator.ICoordinatorApp
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Arrays
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * پشتیبان رمزگذاری‌شده‌ی گاوصندوق رمزها (D76): فایل با رمز اصلی (PBKDF2 + AES-GCM) ساخته می‌شود، نه با کلید Keystore،
 * تا روی گوشی جدید هم قابل بازیابی باشد. رمز اصلی هرگز ذخیره نمی‌شود؛ نسخه‌ی CharArray آن بعد از استفاده پاک می‌شود (متن رمز در رابط فقط رها می‌شود، نه پاک).
 */
class VaultBackup(private val app: ICoordinatorApp) {

    /** Number of vault entries the last export could not read and so left out of the file (0 when none). */
    var lastExportSkipped: Int = 0
        private set

    fun suggestedFileName(now: LocalDateTime = LocalDateTime.now()): String =
        "iCoordinator-vault-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".icvault"

    /** null یعنی موفق؛ در غیر این صورت پیام خطای فارسی. آرایه‌ی [password] در پایان پاک می‌شود. */
    suspend fun export(target: Uri, password: CharArray): String? = try {
        // the user already confirmed: finish the write even if the screen is left or rotated
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                lastExportSkipped = 0
                val data = app.vaultRepository.exportData()
                val skipped = maxOf(0, app.vaultRepository.count() - data.entries.size)
                val bytes = withContext(Dispatchers.Default) { VaultBackupFormat.write(data, password) }
                val output = app.contentResolver.openOutputStream(target, "w")
                    ?: return@withContext "ساخت فایل پشتیبان ممکن نشد."
                output.use { it.write(bytes) }
                lastExportSkipped = skipped
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "پشتیبان‌گیری رمزها ناموفق بود."
            }
        }
    } finally {
        Arrays.fill(password, '\u0000')
    }

    /** (داده، null) یا (null، پیام خطای فارسی). آرایه‌ی [password] در پایان پاک می‌شود. */
    suspend fun readFile(source: Uri, password: CharArray): Pair<VaultBackupData?, String?> = try {
        withContext(Dispatchers.IO) {
            try {
                val input = app.contentResolver.openInputStream(source)
                    ?: return@withContext Pair<VaultBackupData?, String?>(null, MSG_READ)
                val bytes = input.use { readLimited(it, MAX_BYTES + 1) }
                if (bytes.size > MAX_BYTES) return@withContext Pair<VaultBackupData?, String?>(null, MSG_CORRUPT)
                val result = withContext(Dispatchers.Default) { VaultBackupFormat.read(bytes, password) }
                when (result) {
                    is BackupReadResult.Ok -> Pair<VaultBackupData?, String?>(result.data, null)
                    BackupReadResult.WrongPassword -> Pair<VaultBackupData?, String?>(null, "رمز اصلی اشتباه است")
                    BackupReadResult.NotAVaultFile -> Pair<VaultBackupData?, String?>(null, "این فایل پشتیبان رمزها نیست")
                    BackupReadResult.Corrupt -> Pair<VaultBackupData?, String?>(null, MSG_CORRUPT)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Pair<VaultBackupData?, String?>(null, MSG_READ)
            }
        }
    } finally {
        Arrays.fill(password, '\u0000')
    }

    /** null یعنی موفق؛ همه‌ی رمزهای فعلی با محتوای فایل جایگزین می‌شوند. */
    suspend fun restore(data: VaultBackupData): String? = withContext(NonCancellable + Dispatchers.IO) {
        try {
            app.vaultRepository.replaceAll(data, System.currentTimeMillis())
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "بازیابی رمزها ناموفق بود."
        }
    }

    /** Reads at most [limit] bytes (so an oversized file is detected without loading all of it). */
    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val n = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
        const val MSG_CORRUPT = "فایل پشتیبان خراب است"
        const val MSG_READ = "خواندن فایل ممکن نشد"
    }
}
