package com.icoordinator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.icoordinator.security.LockManager
import com.icoordinator.security.LockState
import com.icoordinator.ui.AppNavHost
import com.icoordinator.ui.lock.LockScreen
import com.icoordinator.ui.theme.ICoordinatorTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* نتیجه در آلارم بررسی می‌شود */ }

    // وضعیت قفل در LockState نگه داشته می‌شود تا چرخش صفحه دوباره قفل نکند
    private var locked by mutableStateOf(!LockState.unlocked)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // محتوا در لیست برنامه‌های اخیر و اسکرین‌شات دیده نشود
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val lockManager = LockManager(applicationContext)

        // اندروید ۱۳ به بعد اجازه‌ی نوتیفیکیشن را جداگانه می‌خواهد
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            ICoordinatorTheme {
                // رابط کاربری فارسی و راست‌به‌چپ
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (locked) {
                            LockScreen(lockManager) {
                                LockState.unlocked = true
                                locked = false
                            }
                        } else {
                            AppNavHost()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val backgroundAt = LockState.backgroundAt
        if (LockState.unlocked && backgroundAt > 0L &&
            SystemClock.elapsedRealtime() - backgroundAt >= LockManager.BACKGROUND_TIMEOUT_MS
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
        }
    }
}
