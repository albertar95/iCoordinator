package com.icoordinator.ui.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.icoordinator.security.LockManager
import com.icoordinator.settings.AppSettings
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Stage {
    SETUP_NEW, SETUP_REPEAT, UNLOCK, CHANGE_CURRENT, CHANGE_NEW, CHANGE_REPEAT
}

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

private fun toPersianDigits(text: String): String =
    text.map { if (it in '0'..'9') PERSIAN_DIGITS[it - '0'] else it }.joinToString("")

/** صفحه‌ی تمام‌صفحه‌ی قفل: تعیین رمز، ورود، تغییر رمز و فراموشی رمز (پاک‌کردن همه‌ی اطلاعات). */
@Composable
fun LockScreen(lock: LockManager, onUnlocked: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricOn by AppSettings.biometricEnabled.collectAsState()
    val canBiometric = lock.hasPin() && biometricOn && activity != null && biometricAvailable(context)

    var stage by remember { mutableStateOf(if (lock.hasPin()) Stage.UNLOCK else Stage.SETUP_NEW) }
    var entry by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableStateOf(lock.lockedOutUntil()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }

    // یک بار در هر نمایش صفحه‌ی قفل، پنجره‌ی اثر انگشت خودکار باز می‌شود
    LaunchedEffect(Unit) {
        if (canBiometric && activity != null) showBiometricPrompt(activity) { onUnlocked() }
    }

    val remainingSeconds = if (lockedUntil > now) ((lockedUntil - now + 999) / 1000).toInt() else 0
    val lockedOut = remainingSeconds > 0

    fun showError(text: String) {
        message = text
        messageIsError = true
    }

    fun onPinComplete(pin: String) {
        when (stage) {
            Stage.SETUP_NEW -> {
                firstPin = pin
                entry = ""
                message = null
                stage = Stage.SETUP_REPEAT
            }

            Stage.SETUP_REPEAT -> {
                if (pin == firstPin) {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { lock.setPin(pin) }
                        firstPin = ""
                        entry = ""
                        busy = false
                        onUnlocked()
                    }
                } else {
                    firstPin = ""
                    entry = ""
                    stage = Stage.SETUP_NEW
                    showError("رمزها یکسان نیستند. دوباره تلاش کنید")
                }
            }

            Stage.UNLOCK, Stage.CHANGE_CURRENT -> {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { lock.verify(pin) }
                    entry = ""
                    lockedUntil = lock.lockedOutUntil()
                    now = System.currentTimeMillis()
                    busy = false
                    if (ok) {
                        message = null
                        if (stage == Stage.UNLOCK) {
                            onUnlocked()
                        } else {
                            stage = Stage.CHANGE_NEW
                        }
                    } else {
                        showError("رمز اشتباه است")
                    }
                }
            }

            Stage.CHANGE_NEW -> {
                firstPin = pin
                entry = ""
                message = null
                stage = Stage.CHANGE_REPEAT
            }

            Stage.CHANGE_REPEAT -> {
                if (pin == firstPin) {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { lock.setPin(pin) }
                        firstPin = ""
                        entry = ""
                        busy = false
                        stage = Stage.UNLOCK
                        message = "رمز با موفقیت تغییر کرد"
                        messageIsError = false
                    }
                } else {
                    firstPin = ""
                    entry = ""
                    stage = Stage.CHANGE_NEW
                    showError("رمزها یکسان نیستند. دوباره تلاش کنید")
                }
            }
        }
    }

    fun onDigit(digit: Int) {
        if (busy || lockedOut || entry.length >= LockManager.PIN_LENGTH) return
        entry += digit.toString()
        message = null
        if (entry.length == LockManager.PIN_LENGTH) {
            onPinComplete(entry)
        }
    }

    fun onBackspace() {
        if (busy || entry.isEmpty()) return
        entry = entry.dropLast(1)
    }

    fun cancelChange() {
        firstPin = ""
        entry = ""
        message = null
        stage = Stage.UNLOCK
    }

    val prompt = when (stage) {
        Stage.SETUP_NEW -> "رمز جدید را وارد کنید"
        Stage.SETUP_REPEAT -> "رمز را دوباره وارد کنید"
        Stage.UNLOCK -> "رمز را وارد کنید"
        Stage.CHANGE_CURRENT -> "رمز فعلی را وارد کنید"
        Stage.CHANGE_NEW -> "رمز جدید را وارد کنید"
        Stage.CHANGE_REPEAT -> "رمز جدید را دوباره وارد کنید"
    }
    val title = if (stage == Stage.SETUP_NEW || stage == Stage.SETUP_REPEAT) "تعیین رمز" else "قفل برنامه"
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageHeader(title = title)
        Spacer(Modifier.height(24.dp))
        Text(
            text = prompt,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (i in 0 until LockManager.PIN_LENGTH) {
                    val filled = i < entry.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (filled) scheme.primary else scheme.surface)
                            .border(1.5.dp, if (filled) scheme.primary else scheme.onSurfaceVariant, CircleShape),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val infoText = when {
            lockedOut -> "تلاش بیش از حد. ${toPersianDigits(remainingSeconds.toString())} ثانیه صبر کنید"
            else -> message
        }
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (infoText != null) {
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lockedOut || messageIsError) scheme.error else scheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val keysEnabled = !busy && !lockedOut
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for (col in 0 until 3) {
                            val digit = row * 3 + col + 1
                            KeyButton(
                                label = toPersianDigits(digit.toString()),
                                description = null,
                                enabled = keysEnabled,
                                onClick = { onDigit(digit) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Spacer(Modifier.size(KeySize))
                    KeyButton(
                        label = toPersianDigits("0"),
                        description = null,
                        enabled = keysEnabled,
                        onClick = { onDigit(0) },
                    )
                    KeyButton(
                        label = "⌫",
                        description = "پاک کردن",
                        enabled = !busy && entry.isNotEmpty(),
                        onClick = { onBackspace() },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        when (stage) {
            Stage.UNLOCK -> {
                if (canBiometric && activity != null) {
                    PillButton(
                        text = "ورود با اثر انگشت",
                        onClick = { showBiometricPrompt(activity) { onUnlocked() } },
                        enabled = !busy && !lockedOut,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                PillButton(
                    text = "تغییر رمز",
                    onClick = {
                        entry = ""
                        message = null
                        stage = Stage.CHANGE_CURRENT
                    },
                    outlined = true,
                    enabled = !busy,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showForgot = true }) {
                    Text(
                        text = "رمز را فراموش کرده‌ام",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.error,
                    )
                }
            }

            Stage.CHANGE_CURRENT, Stage.CHANGE_NEW, Stage.CHANGE_REPEAT -> {
                TextButton(onClick = { cancelChange() }, enabled = !busy) {
                    Text(text = "انصراف", style = MaterialTheme.typography.bodyMedium)
                }
            }

            else -> Unit
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            text = {
                Text(
                    text = "همه‌ی اطلاعات برنامه پاک می‌شود و برنامه بسته می‌شود. ادامه می‌دهید؟",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showForgot = false
                    lock.eraseAllData()
                }) {
                    Text(text = "پاک کن", color = scheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgot = false }) {
                    Text(text = "انصراف")
                }
            },
            containerColor = scheme.surface,
        )
    }
}

private val KeySize = 72.dp

private const val BIOMETRIC_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

private fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

private fun biometricAvailable(context: Context): Boolean =
    try {
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) {
        false
    }

/** پنجره‌ی سیستمی اثر انگشت/چهره؛ خطا یا انصراف کاربر را روی صفحه‌ی رمز می‌گذارد. */
private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    try {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("باز کردن iCoordinator")
            .setSubtitle("با اثر انگشت یا چهره")
            .setNegativeButtonText("استفاده از رمز")
            .setAllowedAuthenticators(BIOMETRIC_AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    } catch (e: Exception) {
        // در صورت خطا همان صفحه‌ی رمز می‌ماند
    }
}

@Composable
private fun KeyButton(label: String, description: String?, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val base = Modifier
        .size(KeySize)
        .clip(CircleShape)
        .background(scheme.surfaceContainer)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    val modifier = if (description != null) {
        base.semantics { contentDescription = description }
    } else {
        base
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
