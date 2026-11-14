package com.icoordinator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.icoordinator.data.BackupManager
import com.icoordinator.security.LockManager
import com.icoordinator.security.LockState
import com.icoordinator.settings.AppSettings
import com.icoordinator.settings.ThemeMode
import com.icoordinator.ui.RootNavHost
import com.icoordinator.ui.lock.LockScreen
import com.icoordinator.ui.theme.ICoordinatorTheme
import com.icoordinator.vault.SecureScreen
import com.icoordinator.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* نتیجه در آلارم بررسی می‌شود */ }

    // وضعیت قفل در LockState نگه داشته می‌شود تا چرخش صفحه دوباره قفل نکند
    private var locked by mutableStateOf(!LockState.unlocked)

    private lateinit var lockManager: LockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(applicationContext)

        // اگر اسکرین‌شات مجاز نباشد، محتوا در اسکرین‌شات و لیست برنامه‌های اخیر دیده نمی‌شود
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(AppSettings.allowScreenshots, SecureScreen.forced) { allow, forced -> allow && !forced }.collect { allow ->
                    if (allow) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        lockManager = LockManager(applicationContext)

        // ثبت دوباره‌ی آلارم‌ها اگر بعد از بازیابی پشتیبان ناتمام مانده باشد
        val app = application as ICoordinatorApp
        lifecycleScope.launch(Dispatchers.IO) { BackupManager.runPendingReschedule(app) }

        // اندروید ۱۳ به بعد اجازه‌ی نوتیفیکیشن را جداگانه می‌خواهد
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val mode by AppSettings.themeMode.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ICoordinatorTheme(darkTheme = dark) {
                // رابط کاربری فارسی و راست‌به‌چپ
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // صفحه‌ها همیشه در کامپوزیشن می‌مانند تا بعد از باز شدن قفل، کاربر در همان صفحه بماند
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                if (locked) Modifier.fillMaxSize().clearAndSetSemantics { }
                                else Modifier.fillMaxSize()
                            ) {
                                RootNavHost()
                            }
                            if (locked) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope { while (true) awaitPointerEvent() }
                                        },
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    LockScreen(lockManager) {
                                        LockState.unlocked = true
                                        locked = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val backgroundAt = LockState.backgroundAt
        if (LockState.skipNextLock) {
            // برگشت از انتخابگر فایل: فقط یک بار قفل نشود
            LockState.skipNextLock = false
        } else if (LockState.unlocked && backgroundAt > 0L &&
            SystemClock.elapsedRealtime() - backgroundAt >= lockManager.backgroundTimeoutMs()
        ) {
            LockState.unlocked = false
            locked = true
        }
        // زمان قدیمی نباید در چرخش‌های بعدی صفحه باعث قفل شود
        LockState.backgroundAt = 0L
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            LockState.backgroundAt = SystemClock.elapsedRealtime()
            // a system picker opened from the vault (backup) must not lock it (R1-01)
            if (!LockState.skipNextLock) VaultSession.lock()
        }
    }
}
