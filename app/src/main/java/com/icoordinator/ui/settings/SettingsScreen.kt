package com.icoordinator.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.icoordinator.ICoordinatorApp
import com.icoordinator.data.BackupManager
import com.icoordinator.security.LockManager
import com.icoordinator.security.LockState
import com.icoordinator.settings.AppSettings
import com.icoordinator.settings.ThemeMode
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val THEME_CHOICES = listOf(
    ThemeMode.SYSTEM to "پیش‌فرض گوشی",
    ThemeMode.LIGHT to "روشن",
    ThemeMode.DARK to "تاریک",
)

private fun lockTimeoutLabel(ms: Long): String = when (ms) {
    0L -> "فوری"
    60_000L -> "۱ دقیقه"
    300_000L -> "۵ دقیقه"
    else -> ""
}

private fun findActivity(context: Context): Activity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }

    val themeMode by AppSettings.themeMode.collectAsState()
    val biometricEnabled by AppSettings.biometricEnabled.collectAsState()
    val allowScreenshots by AppSettings.allowScreenshots.collectAsState()
    var lockTimeout by remember { mutableStateOf(AppSettings.lockTimeoutMs()) }
    var showChangePin by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val lockManager = remember { LockManager(context.applicationContext) }
    val backup = remember { BackupManager(ICoordinatorApp.instance) }
    val hasPin = remember { lockManager.hasPin() }
    val biometricAvailable = remember {
        try {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        LockState.skipNextLock = false
        if (uri != null) {
            scope.launch {
                busy = true
                val error = backup.export(uri)
                busy = false
                toast(error ?: "نسخه‌ی پشتیبان ذخیره شد.")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        LockState.skipNextLock = false
        if (uri != null) pendingRestore = uri
    }

    // در حال بازیابی/ذخیره، دکمه‌ی بازگشت نباید کار را نیمه‌کاره رها کند
    BackHandler(enabled = busy) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = "تنظیمات", onBack = if (busy) null else onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsCard("ظاهر") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        THEME_CHOICES.forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { AppSettings.setThemeMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                SettingsCard("امنیت") {
                    PillButton(
                        text = "تغییر رمز",
                        onClick = { showChangePin = true },
                        outlined = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "قفل خودکار",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSettings.LOCK_TIMEOUT_CHOICES.forEach { ms ->
                            FilterChip(
                                selected = lockTimeout == ms,
                                onClick = {
                                    AppSettings.setLockTimeoutMs(ms)
                                    lockTimeout = ms
                                },
                                label = { Text(lockTimeoutLabel(ms)) },
                            )
                        }
                    }
                    SwitchRow(
                        title = "ورود با اثر انگشت",
                        hint = when {
                            !hasPin -> "ابتدا رمز تعیین کنید"
                            !biometricAvailable -> "این گوشی پشتیبانی نمی‌کند"
                            else -> null
                        },
                        checked = biometricEnabled && hasPin && biometricAvailable,
                        enabled = hasPin && biometricAvailable,
                        onCheckedChange = { AppSettings.setBiometricEnabled(it) },
                    )
                    SwitchRow(
                        title = "اجازه‌ی اسکرین‌شات",
                        hint = "اگر خاموش باشد، صفحه در اسکرین‌شات و لیست برنامه‌های اخیر دیده نمی‌شود",
                        checked = allowScreenshots,
                        enabled = true,
                        onCheckedChange = { AppSettings.setAllowScreenshots(it) },
                    )
                }

                SettingsCard("پشتیبان‌گیری") {
                    PillButton(
                        text = "ذخیره نسخه پشتیبان",
                        onClick = {
                            LockState.skipNextLock = true
                            exportLauncher.launch(backup.suggestedFileName())
                        },
                        outlined = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PillButton(
                        text = "بازیابی از فایل",
                        onClick = {
                            LockState.skipNextLock = true
                            restoreLauncher.launch(arrayOf("*/*"))
                        },
                        outlined = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "فایل پشتیبان رمزگذاری نشده است؛ آن را جای امن نگه دارید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "نسخه " + versionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    val restoreUri = pendingRestore
    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) pendingRestore = null },
            text = {
                Text(
                    text = "همه‌ی داده‌های فعلی با فایل پشتیبان جایگزین می‌شود و برنامه دوباره باز می‌شود. ادامه می‌دهید؟",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            val error = backup.restore(restoreUri)
                            pendingRestore = null
                            if (error == null || backup.needsRestart) {
                                if (error != null) {
                                    toast(error)
                                    delay(1500)
                                }
                                if (activity != null) BackupManager.restartApp(activity)
                                busy = false
                            } else {
                                busy = false
                                toast(error)
                            }
                        }
                    },
                ) {
                    Text(text = "ادامه", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingRestore = null }) {
                    Text(text = "انصراف")
                }
            },
            containerColor = scheme.surface,
        )
    }

    if (showChangePin) {
        ChangePinDialog(
            lock = lockManager,
            onDismiss = { showChangePin = false },
            onDone = {
                showChangePin = false
                toast("رمز با موفقیت تغییر کرد")
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        SectionLabel(text = title)
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.5f),
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            onValueChange(text.filter { it in '0'..'9' }.take(LockManager.PIN_LENGTH))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = scheme.outlineVariant,
            focusedBorderColor = scheme.primary,
            errorBorderColor = scheme.error,
        ),
    )
}

/** Small change-PIN dialog (LockScreen's change flow is private to the lock screen): current, new, repeat. */
@Composable
private fun ChangePinDialog(lock: LockManager, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(text = "تغییر رمز", color = scheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(current, { current = it; error = null }, "رمز فعلی")
                PinField(newPin, { newPin = it; error = null }, "رمز جدید")
                PinField(repeat, { repeat = it; error = null }, "تکرار رمز جدید")
                if (error != null) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    when {
                        !LockManager.isValidPin(newPin) -> error = "رمز جدید باید ۴ رقم باشد"
                        newPin != repeat -> error = "رمزها یکسان نیستند"
                        else -> {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.Default) { lock.verify(current) }
                                if (ok) {
                                    withContext(Dispatchers.Default) { lock.setPin(newPin) }
                                    busy = false
                                    onDone()
                                } else {
                                    busy = false
                                    error = if (System.currentTimeMillis() < lock.lockedOutUntil()) {
                                        "تلاش بیش از حد؛ کمی بعد دوباره امتحان کنید"
                                    } else {
                                        "رمز فعلی اشتباه است"
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                Text(text = "ذخیره")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(text = "انصراف")
            }
        },
        containerColor = scheme.surface,
    )
}
