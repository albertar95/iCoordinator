package com.icoordinator.ui.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.icoordinator.R
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.DeleteIconButton
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.TbIcon
import com.icoordinator.vault.VaultEntry
import com.icoordinator.vault.VaultFolder
import com.icoordinator.vault.VaultPayload

private const val MASK = "••••••••"
private const val COPIED_TEXT = "کپی شد؛ ۳۰ ثانیه دیگر پاک می‌شود"

/**
 * One entry: view mode (existing entry; masked password with eye, copy buttons) or edit mode (new entry or after
 * "ویرایش"). Field values live in plain remember state (never saved state) and only while this screen is shown.
 */
@Composable
fun VaultEntryScreen(vm: VaultViewModel, entryId: Long?, initialFolderId: Long?, onBack: () -> Unit) {
    val snapshot by vm.snapshot.collectAsState()
    val snap = snapshot
    // every way out goes through finish() so a delete (entry vanishes from the snapshot) cannot pop twice
    var finished by remember { mutableStateOf(false) }
    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            onBack()
        }
    }
    val existing = if (entryId == null || snap == null) null else vm.entry(entryId)
    val missing = entryId != null && snap != null && existing == null

    LaunchedEffect(missing) {
        if (missing) finish()
    }

    if (snap == null || missing) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    EntryContent(
        vm = vm,
        existing = existing,
        folders = snap.folders,
        initialFolderId = initialFolderId,
        finish = finish,
    )
}

@Composable
private fun EntryContent(
    vm: VaultViewModel,
    existing: VaultEntry?,
    folders: List<VaultFolder>,
    initialFolderId: Long?,
    finish: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    var editing by remember { mutableStateOf(existing == null) }
    var title by remember { mutableStateOf(existing?.payload?.title ?: "") }
    var username by remember { mutableStateOf(existing?.payload?.username ?: "") }
    var password by remember { mutableStateOf(existing?.payload?.password ?: "") }
    var url by remember { mutableStateOf(existing?.payload?.url ?: "") }
    var note by remember { mutableStateOf(existing?.payload?.note ?: "") }
    var folderId by remember { mutableStateOf(existing?.folderId ?: initialFolderId) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun hasChanges(): Boolean {
        if (existing == null) {
            return title.isNotBlank() || username.isNotEmpty() || password.isNotEmpty() || url.isNotEmpty() ||
                note.isNotEmpty() || folderId != initialFolderId
        }
        val p = existing.payload
        return title != p.title || username != p.username || password != p.password || url != p.url ||
            note != p.note || folderId != existing.folderId
    }

    fun startEdit() {
        if (existing == null) return
        val p = existing.payload
        title = p.title
        username = p.username
        password = p.password
        url = p.url
        note = p.note
        folderId = existing.folderId
        error = null
        revealed = false
        editing = true
    }

    fun leaveEdit() {
        if (existing == null) {
            finish()
        } else {
            error = null
            editing = false
        }
    }

    val requestBack: () -> Unit = {
        when {
            !editing -> finish()
            hasChanges() -> showDiscard = true
            else -> leaveEdit()
        }
    }
    BackHandler(enabled = editing) { requestBack() }

    val copy: (String) -> Unit = { value ->
        SecretClipboard.copy(context, value)
        Toast.makeText(context, COPIED_TEXT, Toast.LENGTH_SHORT).show()
    }

    val save: () -> Unit = {
        if (!busy) {
            busy = true
            error = null
            vm.saveEntry(existing?.id, folderId, VaultPayload(title, username, password, url, note)) { problem ->
                busy = false
                if (problem != null) {
                    error = problem
                } else if (existing == null) {
                    finish()
                } else {
                    revealed = false
                    editing = false
                }
            }
        }
    }

    val headerTitle = when {
        existing == null -> "رمز جدید"
        editing -> "ویرایش رمز"
        else -> existing.payload.title
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        PageHeader(title = headerTitle, onBack = requestBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (editing) {
                AppTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = "عنوان",
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error,
                )
                AppTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "نام کاربری",
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "رمز",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "آدرس سایت یا نام برنامه",
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "یادداشت",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                )
                FolderPicker(folders = folders, selectedId = folderId, onSelect = { folderId = it })
            } else if (existing != null) {
                val p = existing.payload
                ViewRow(label = "عنوان", value = p.title)
                ViewRow(
                    label = "نام کاربری",
                    value = p.username,
                    trailing = if (p.username.isNotEmpty()) {
                        {
                            IconButton(onClick = { copy(p.username) }) {
                                TbIcon(R.drawable.ic_tb_copy, "کپی نام کاربری", tint = scheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        null
                    },
                )
                ViewRow(
                    label = "رمز",
                    value = if (p.password.isEmpty()) "" else if (revealed) p.password else MASK,
                    trailing = if (p.password.isNotEmpty()) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { revealed = !revealed }) {
                                    TbIcon(
                                        icon = if (revealed) R.drawable.ic_tb_eye_off else R.drawable.ic_tb_eye,
                                        contentDescription = if (revealed) "پنهان کردن رمز" else "نمایش رمز",
                                        tint = scheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { copy(p.password) }) {
                                    TbIcon(R.drawable.ic_tb_copy, "کپی رمز", tint = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        null
                    },
                )
                ViewRow(label = "آدرس / برنامه", value = p.url)
                ViewRow(label = "یادداشت", value = p.note)
                val folderName = folders.firstOrNull { it.id == existing.folderId }?.name
                if (folderName != null) ViewRow(label = "پوشه", value = folderName)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                PillButton(
                    text = "انصراف",
                    onClick = requestBack,
                    modifier = Modifier.weight(1f),
                    outlined = true,
                )
                PillButton(
                    text = "ذخیره",
                    onClick = save,
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_tb_check,
                    enabled = !busy,
                )
            } else {
                PillButton(
                    text = "ویرایش",
                    onClick = { startEdit() },
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_tb_pencil,
                )
                DeleteIconButton(onClick = { showDelete = true })
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("تغییرات ذخیره نشود؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        leaveEdit()
                    },
                ) { Text("خروج بدون ذخیره", color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("ادامه‌ی ویرایش") } },
        )
    }

    if (showDelete && existing != null) {
        // the generic name keeps the entry title out of this (not screenshot-protected) dialog window
        ConfirmDeleteDialog(
            itemName = "این رمز",
            onConfirm = {
                showDelete = false
                vm.deleteEntry(existing.id)
                finish()
            },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun ViewRow(label: String, value: String, trailing: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(
                text = if (value.isEmpty()) "—" else value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}

@Composable
private fun FolderPicker(folders: List<VaultFolder>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    var open by remember { mutableStateOf(false) }
    val currentName = folders.firstOrNull { it.id == selectedId }?.name ?: "بدون پوشه"
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .border(1.dp, scheme.outlineVariant, shape)
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TbIcon(R.drawable.ic_tb_folder, null, size = 22.dp, tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "پوشه", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(text = currentName, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("بدون پوشه") },
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = { Text(folder.name) },
                    onClick = {
                        open = false
                        onSelect(folder.id)
                    },
                )
            }
        }
    }
}

/** Password field: masked by default with an eye toggle; password keyboard, auto-correct off. */
@Composable
internal fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var visible by remember { mutableStateOf(false) }
    val supporting: (@Composable () -> Unit)? = if (supportingText != null) {
        { Text(text = supportingText, style = MaterialTheme.typography.bodySmall) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        isError = isError,
        supportingText = supporting,
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                TbIcon(
                    icon = if (visible) R.drawable.ic_tb_eye_off else R.drawable.ic_tb_eye,
                    contentDescription = if (visible) "پنهان کردن" else "نمایش",
                    tint = scheme.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = scheme.outlineVariant,
            focusedBorderColor = scheme.primary,
            errorBorderColor = scheme.error,
        ),
    )
}

/**
 * Copies a secret to the clipboard marked as sensitive (API 33+) and clears it again after 30 seconds.
 * Only a main-thread Handler is used (no WorkManager, no alarms): if the process dies first the clip stays.
 */
object SecretClipboard {
    private const val LABEL = "icoordinator-secret"
    private const val CLEAR_AFTER_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun copy(context: Context, text: String) {
        val appContext = context.applicationContext ?: context
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(LABEL, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val extras = PersistableBundle()
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            clip.description.extras = extras
        }
        try {
            clipboard.setPrimaryClip(clip)
        } catch (e: SecurityException) {
            return
        }
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            pending = null
            clearIfOurs(clipboard)
        }
        pending = task
        handler.postDelayed(task, CLEAR_AFTER_MS)
    }

    private fun clearIfOurs(clipboard: ClipboardManager) {
        try {
            val description = clipboard.primaryClipDescription
            // an unreadable description (newer Android hides it from background apps) is cleared as well
            if (description == null || description.label?.toString() == LABEL) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        } catch (e: SecurityException) {
            // nothing more can be done
        }
    }
}
