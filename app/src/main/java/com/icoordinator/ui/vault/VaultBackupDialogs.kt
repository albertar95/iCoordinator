package com.icoordinator.ui.vault

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.icoordinator.ICoordinatorApp
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.security.LockState
import com.icoordinator.vault.VaultBackup
import com.icoordinator.vault.VaultBackupData
import com.icoordinator.vault.VaultBackupFormat
import java.util.Arrays
import kotlinx.coroutines.launch

enum class VaultBackupMode { EXPORT, IMPORT }

private enum class BackupStage { PICKING, ASK, WORKING, CONFIRM, RESULT }

private const val EXPORT_INTRO =
    "این رمز برای باز کردن فایل پشتیبان لازم است و هیچ‌جا ذخیره نمی‌شود. اگر فراموشش کنی، پشتیبان قابل بازیابی نیست."

/**
 * Backup / restore dialogs of the vault. mode == null shows nothing. EXPORT: master password (+ repeat), then the
 * file picker, then the encrypted file is written. IMPORT: file picker, master password, confirmation, restore.
 * The master password text is only held in this composable's state and dropped (state set to "") right after use.
 * It is not wiped: Strings cannot be zeroed, so the text stays in memory until garbage collection; only the
 * CharArray copy passed to VaultBackup is wiped.
 */
@Composable
fun VaultBackupDialogs(mode: VaultBackupMode?, onDismiss: () -> Unit) {
    when (mode) {
        null -> Unit
        VaultBackupMode.EXPORT -> ExportFlow(onDismiss)
        VaultBackupMode.IMPORT -> ImportFlow(onDismiss)
    }
}

@Composable
private fun ExportFlow(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val backup = remember { VaultBackup(ICoordinatorApp.instance) }
    var stage by remember { mutableStateOf(BackupStage.ASK) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var skipped by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        LockState.skipNextLock = false
        val issue = passwordIssue(password, repeat)
        if (uri == null || issue != null) {
            // cancelled (or the state was lost): back to the password dialog
            problem = issue
            stage = BackupStage.ASK
        } else {
            val secret = password.toCharArray()
            password = ""
            repeat = ""
            stage = BackupStage.WORKING
            scope.launch {
                failure = backup.export(uri, secret)
                skipped = if (failure == null) backup.lastExportSkipped else 0
                stage = BackupStage.RESULT
            }
        }
    }

    when (stage) {
        BackupStage.ASK -> PasswordDialog(
            title = "پشتیبان‌گیری رمزها",
            intro = EXPORT_INTRO,
            askRepeat = true,
            password = password,
            onPassword = {
                password = it
                problem = null
            },
            repeat = repeat,
            onRepeat = {
                repeat = it
                problem = null
            },
            problem = problem,
            confirmText = "ادامه",
            onConfirm = {
                val issue = passwordIssue(password, repeat)
                if (issue != null) {
                    problem = issue
                } else {
                    problem = null
                    stage = BackupStage.PICKING
                    LockState.skipNextLock = true
                    launcher.launch(backup.suggestedFileName())
                }
            },
            onDismiss = {
                password = ""
                repeat = ""
                onDismiss()
            },
        )
        BackupStage.WORKING -> WorkingDialog("در حال ساخت فایل پشتیبان…")
        BackupStage.RESULT -> ResultDialog(
            title = if (failure == null) "پشتیبان رمزها ذخیره شد" else "پشتیبان‌گیری ناموفق بود",
            message = failure ?: if (skipped > 0) {
                AccountingFormat.digits("پشتیبان ذخیره شد، اما $skipped مورد قابل خواندن نبود و در فایل نیست.")
            } else {
                null
            },
            onClose = onDismiss,
        )
        else -> Unit
    }
}

@Composable
private fun ImportFlow(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val backup = remember { VaultBackup(ICoordinatorApp.instance) }
    var stage by remember { mutableStateOf(BackupStage.PICKING) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    var parsed by remember { mutableStateOf<VaultBackupData?>(null) }
    var workingText by remember { mutableStateOf("") }
    var resultTitle by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        LockState.skipNextLock = false
        if (uri == null) {
            onDismiss()
        } else {
            source = uri
            stage = BackupStage.ASK
        }
    }
    LaunchedEffect(Unit) {
        LockState.skipNextLock = true
        picker.launch(arrayOf("*/*"))
    }

    when (stage) {
        BackupStage.ASK -> PasswordDialog(
            title = "بازیابی پشتیبان رمزها",
            intro = "رمز اصلی فایل پشتیبان را وارد کن.",
            askRepeat = false,
            password = password,
            onPassword = {
                password = it
                problem = null
            },
            repeat = "",
            onRepeat = {},
            problem = problem,
            confirmText = "ادامه",
            onConfirm = {
                val uri = source
                if (uri == null || password.isEmpty()) {
                    problem = "رمز اصلی را وارد کن"
                } else {
                    val secret = password.toCharArray()
                    password = ""
                    workingText = "در حال خواندن فایل پشتیبان…"
                    stage = BackupStage.WORKING
                    scope.launch {
                        val (data, error) = backup.readFile(uri, secret)
                        if (data != null) {
                            parsed = data
                            stage = BackupStage.CONFIRM
                        } else {
                            resultTitle = "بازیابی ناموفق بود"
                            resultMessage = error
                            stage = BackupStage.RESULT
                        }
                    }
                }
            },
            onDismiss = {
                password = ""
                onDismiss()
            },
        )
        BackupStage.WORKING -> WorkingDialog(workingText)
        BackupStage.CONFIRM -> {
            val data = parsed
            if (data != null) {
                ConfirmRestoreDialog(
                    count = data.entries.size,
                    onConfirm = {
                        workingText = "در حال بازیابی رمزها…"
                        stage = BackupStage.WORKING
                        scope.launch {
                            val error = backup.restore(data)
                            parsed = null
                            if (error == null) {
                                resultTitle = "رمزها بازیابی شد"
                                resultMessage = AccountingFormat.digits("${data.entries.size} رمز بازیابی شد.")
                            } else {
                                resultTitle = "بازیابی ناموفق بود"
                                resultMessage = error
                            }
                            stage = BackupStage.RESULT
                        }
                    },
                    onDismiss = {
                        parsed = null
                        onDismiss()
                    },
                )
            }
        }
        BackupStage.RESULT -> ResultDialog(title = resultTitle, message = resultMessage, onClose = onDismiss)
        else -> Unit
    }
}

private fun passwordIssue(password: String, repeat: String): String? {
    val first = password.toCharArray()
    val second = repeat.toCharArray()
    try {
        return VaultBackupFormat.passwordProblem(first, second)
    } finally {
        Arrays.fill(first, '\u0000')
        Arrays.fill(second, '\u0000')
    }
}

/** Dialog windows are separate windows: mark them secure as well, like the activity window. */
@Composable
private fun SecureDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    intro: String,
    askRepeat: Boolean,
    password: String,
    onPassword: (String) -> Unit,
    repeat: String,
    onRepeat: (String) -> Unit,
    problem: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SecureDialogWindow()
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SecretTextField(
                    value = password,
                    onValueChange = onPassword,
                    label = "رمز اصلی",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (askRepeat) {
                    SecretTextField(
                        value = repeat,
                        onValueChange = onRepeat,
                        label = "تکرار رمز اصلی",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (problem != null) {
                    Text(
                        text = problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun WorkingDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            SecureDialogWindow()
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    )
}

@Composable
private fun ConfirmRestoreDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازیابی رمزها") },
        text = {
            SecureDialogWindow()
            Text(AccountingFormat.digits("$count رمز در فایل هست. همه‌ی رمزهای فعلی با این فایل جایگزین می‌شوند."))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("جایگزین کن", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ResultDialog(title: String, message: String?, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = if (message != null) {
            {
                SecureDialogWindow()
                Text(message)
            }
        } else {
            null
        },
        confirmButton = { TextButton(onClick = onClose) { Text("باشه") } },
    )
}
