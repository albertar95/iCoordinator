package com.icoordinator.security

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * قفل برنامه با رمز ۴ رقمی. رمز هرگز ذخیره نمی‌شود؛ فقط salt و هش PBKDF2 (Base64) در
 * SharedPreferences با نام "app_lock" نگه‌داری می‌شود. توابع این کلاس ممکن است مسدودکننده باشند
 * و باید خارج از رشته‌ی اصلی صدا زده شوند.
 */
class LockManager(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(KEY_SALT) && prefs.contains(KEY_HASH)

    /** رمز جدید را ذخیره می‌کند (فقط هش) و شمارنده‌ی تلاش و قفل موقت را صفر می‌کند. */
    @Synchronized
    fun setPin(pin: String) {
        require(isValidPin(pin)) { "PIN must be exactly $PIN_LENGTH ASCII digits" }
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .commit()
    }

    /**
     * رمز را بررسی می‌کند. در زمان قفل موقت بدون هش کردن false برمی‌گرداند.
     * پس از ۵ رمز اشتباه پشت‌سرهم، ورود ۳۰ ثانیه غیرفعال می‌شود.
     */
    @Synchronized
    fun verify(pin: String): Boolean {
        if (System.currentTimeMillis() < lockedOutUntil()) return false
        val salt = decode(KEY_SALT)
        val expected = decode(KEY_HASH)
        if (salt == null || expected == null) return false
        val ok = isValidPin(pin) && MessageDigest.isEqual(hashPin(pin, salt), expected)
        if (ok) {
            prefs.edit()
                .putInt(KEY_FAILED, 0)
                .putLong(KEY_LOCKED_UNTIL, 0L)
                .commit()
        } else {
            registerFailure()
        }
        return ok
    }

    /** تعداد رمزهای اشتباه پشت‌سرهم از آخرین قفل موقت یا ورود موفق. */
    fun failedAttempts(): Int = prefs.getInt(KEY_FAILED, 0)

    /** زمان پایان قفل موقت (میلی‌ثانیه، مثل System.currentTimeMillis)؛ صفر یعنی قفلی نیست. */
    fun lockedOutUntil(): Long = prefs.getLong(KEY_LOCKED_UNTIL, 0L)

    /** همه‌ی داده‌های برنامه (پایگاه داده، تنظیمات و آلارم‌ها) را پاک می‌کند و برنامه بسته می‌شود. */
    fun eraseAllData() {
        val manager = appContext.getSystemService(ActivityManager::class.java)
        manager?.clearApplicationUserData()
    }

    private fun registerFailure() {
        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        val editor = prefs.edit()
        if (failed >= MAX_ATTEMPTS) {
            editor.putInt(KEY_FAILED, 0)
            editor.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + LOCKOUT_MS)
        } else {
            editor.putInt(KEY_FAILED, failed)
        }
        editor.commit()
    }

    private fun decode(key: String): ByteArray? {
        val text = prefs.getString(key, null) ?: return null
        return try {
            Base64.decode(text, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val PIN_LENGTH = 4
        const val BACKGROUND_TIMEOUT_MS = 60_000L

        private const val PREFS_NAME = "app_lock"
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
        private const val KEY_FAILED = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 60_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L

        /** دقیقاً ۴ رقم ASCII (۰ تا ۹). */
        fun isValidPin(pin: String): Boolean =
            pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
    }
}

/** وضعیت قفل در سطح کل پردازش؛ چرخش صفحه قفل نمی‌کند و راه‌اندازی دوباره‌ی پردازش همیشه قفل است. */
object LockState {
    @Volatile var unlocked: Boolean = false
    @Volatile var backgroundAt: Long = 0L
}
