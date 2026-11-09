package com.icoordinator.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** تنظیمات برنامه در SharedPreferences با نام "app_settings". */
object AppSettings {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_LOCK_TIMEOUT_MS = "lock_timeout_ms"
    private const val DEFAULT_LOCK_TIMEOUT_MS = 60_000L

    val LOCK_TIMEOUT_CHOICES: List<Long> = listOf(0L, 60_000L, 300_000L)

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var lockTimeoutMs: Long = DEFAULT_LOCK_TIMEOUT_MS

    /** چندبار صدا زدن بی‌اثر است؛ فقط بار اول مقادیر ذخیره‌شده را می‌خواند. */
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = (context.applicationContext ?: context)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _themeMode.value = when (p.getString(KEY_THEME_MODE, "system")) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        val saved = p.getLong(KEY_LOCK_TIMEOUT_MS, DEFAULT_LOCK_TIMEOUT_MS)
        lockTimeoutMs = if (saved in LOCK_TIMEOUT_CHOICES) saved else DEFAULT_LOCK_TIMEOUT_MS
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        val text = when (mode) {
            ThemeMode.SYSTEM -> "system"
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
        }
        prefs?.edit()?.putString(KEY_THEME_MODE, text)?.apply()
    }

    fun lockTimeoutMs(): Long = lockTimeoutMs

    fun setLockTimeoutMs(ms: Long) {
        if (ms !in LOCK_TIMEOUT_CHOICES) return
        lockTimeoutMs = ms
        prefs?.edit()?.putLong(KEY_LOCK_TIMEOUT_MS, ms)?.apply()
    }
}
