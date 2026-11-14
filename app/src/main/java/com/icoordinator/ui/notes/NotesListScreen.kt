package com.icoordinator.ui.notes

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.data.BackupManager
import com.icoordinator.personal.NoteFolderEntity
import com.icoordinator.personal.PersonalBackup
import com.icoordinator.security.LockState
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TagText
import com.icoordinator.ui.components.TbIcon
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotesListScreen(
    onExit: () -> Unit,
    onOpenNote: (noteId: Long?, folderId: Long?) -> Unit,
) {
    val vm: NotesViewModel = viewModel(key = "notes_list")
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val backup = remember { PersonalBackup(ICoordinatorApp.instance) }

    var menuOpen by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var restartAfterError by remember { mutableStateOf(false) }

    var showNewFolder by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<NoteFolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<NoteFolderEntity?>(null) }
    var noteMenu by remember { mutableStateOf<NoteListItem?>(null) }
    var noteToMove by remember { mutableStateOf<NoteListItem?>(null) }
    var noteToDelete by remember { mutableStateOf<NoteListItem?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        LockState.skipNextLock = false
        if (uri != null) {
            scope.launch {
                backupBusy = true
                val error = backup.export(uri)
                backupBusy = false
                if (error == null) snackbar.showSnackbar("پشتیبان ذخیره شد") else errorMessage = error
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        LockState.skipNextLock = false
        if (uri != null) {
            scope.launch {
                backupBusy = true
                // the restore swaps files and closes the database; it must not be cut off half-way
                val error = withContext(NonCancellable) { backup.restore(uri) }
                backupBusy = false
                if (error == null) {
                    restartApp(activity)
                } else {
                    if (backup.needsRestart) restartAfterError = true
                    errorMessage = error
                }
            }
        }
    }

    val showError: (String?) -> Unit = { error -> if (error != null) scope.launch { snackbar.showSnackbar(error) } }
    val searching = state.query.isNotBlank()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PageHeader(
                    title = "یادداشت‌ها",
                    onBack = onExit,
                    actionIcon = R.drawable.ic_tb_dots_vertical,
                    actionDescription = "گزینه‌ها",
                    onAction = { menuOpen = true },
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("پوشه‌ی جدید") },
                            onClick = {
                                menuOpen = false
                                showNewFolder = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("پشتیبان‌گیری یادداشت‌ها و رکوردها") },
                            enabled = !backupBusy,
                            onClick = {
                                menuOpen = false
                                LockState.skipNextLock = true
                                exportLauncher.launch(backup.suggestedFileName())
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("بازیابی پشتیبان یادداشت‌ها") },
                            enabled = !backupBusy,
                            onClick = {
                                menuOpen = false
                                confirmRestore = true
                            },
                        )
                    }
                }
            }
            if (backupBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            AppTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = "جستجو در یادداشت‌ها",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "chip_all") {
                    FolderChip(
                        text = "همه",
                        selected = state.selectedFolderId == null,
                        onClick = { vm.selectFolder(null) },
                    )
                }
                items(state.folders, key = { "chip_" + it.id }) { folder ->
                    FolderChip(
                        text = folder.name,
                        selected = state.selectedFolderId == folder.id,
                        onClick = { vm.selectFolder(folder.id) },
                        onLongClick = { folderMenu = folder },
                    )
                }
                item(key = "chip_add") {
                    FolderChip(
                        text = null,
                        selected = false,
                        onClick = { showNewFolder = true },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                !state.loaded -> Unit
                state.pinned.isEmpty() && state.others.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_tb_notes,
                    title = if (searching) "چیزی پیدا نشد" else "یادداشتی نیست",
                    message = if (searching) "عبارت دیگری را امتحان کن" else "با دکمه‌ی یادداشت جدید شروع کن",
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    val showLabels = state.pinned.isNotEmpty() && state.others.isNotEmpty()
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (showLabels) {
                            item(key = "label_pinned") { SectionLabel("سنجاق‌شده") }
                        }
                        items(state.pinned, key = { "n_" + it.id }) { item ->
                            NoteCard(
                                item = item,
                                showFolder = state.selectedFolderId == null,
                                onClick = { onOpenNote(item.id, item.folderId) },
                                onLongClick = { noteMenu = item },
                            )
                        }
                        if (showLabels) {
                            item(key = "label_others") { SectionLabel("یادداشت‌ها") }
                        }
                        items(state.others, key = { "n_" + it.id }) { item ->
                            NoteCard(
                                item = item,
                                showFolder = state.selectedFolderId == null,
                                onClick = { onOpenNote(item.id, item.folderId) },
                                onLongClick = { noteMenu = item },
                            )
                        }
                    }
                }
            }
        }
        PillButton(
            text = "یادداشت جدید",
            onClick = { onOpenNote(null, state.selectedFolderId) },
            icon = R.drawable.ic_tb_plus,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp),
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
        )
    }

    if (showNewFolder) {
        FolderNameDialog(
            title = "پوشه‌ی جدید",
            initial = "",
            confirmText = "ساخت",
            onConfirm = { name, onError ->
                vm.addFolder(name) { error ->
                    if (error == null) showNewFolder = false else onError(error)
                }
            },
            onDismiss = { showNewFolder = false },
        )
    }

    val editingFolder = folderMenu
    if (editingFolder != null) {
        FolderNameDialog(
            title = "ویرایش پوشه",
            initial = editingFolder.name,
            confirmText = "ذخیره",
            onConfirm = { name, onError ->
                vm.renameFolder(editingFolder.id, name) { error ->
                    if (error == null) folderMenu = null else onError(error)
                }
            },
            onDismiss = { folderMenu = null },
            extraAction = {
                TextButton(
                    onClick = {
                        folderToDelete = editingFolder
                        folderMenu = null
                    },
                ) { Text("حذف پوشه", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    val deletingFolder = folderToDelete
    if (deletingFolder != null) {
        ConfirmDeleteDialog(
            itemName = deletingFolder.name,
            onConfirm = {
                folderToDelete = null
                vm.deleteFolder(deletingFolder.id, showError)
            },
            onDismiss = { folderToDelete = null },
        )
    }

    val actionNote = noteMenu
    if (actionNote != null) {
        AlertDialog(
            onDismissRequest = { noteMenu = null },
            title = { Text(actionNote.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ActionRow(if (actionNote.pinned) "برداشتن سنجاق" else "سنجاق کردن") {
                        noteMenu = null
                        vm.setPinned(actionNote.id, !actionNote.pinned, showError)
                    }
                    ActionRow("انتقال به پوشه") {
                        noteMenu = null
                        noteToMove = actionNote
                    }
                    ActionRow("حذف", MaterialTheme.colorScheme.error) {
                        noteMenu = null
                        noteToDelete = actionNote
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { noteMenu = null }) { Text("انصراف") } },
        )
    }

    val movingNote = noteToMove
    if (movingNote != null) {
        AlertDialog(
            onDismissRequest = { noteToMove = null },
            title = { Text("انتقال به پوشه") },
            text = {
                Column {
                    ActionRow("بدون پوشه", selected = movingNote.folderId == null) {
                        noteToMove = null
                        vm.moveNote(movingNote.id, null, showError)
                    }
                    state.folders.forEach { folder ->
                        ActionRow(folder.name, selected = movingNote.folderId == folder.id) {
                            noteToMove = null
                            vm.moveNote(movingNote.id, folder.id, showError)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { noteToMove = null }) { Text("انصراف") } },
        )
    }

    val deletingNote = noteToDelete
    if (deletingNote != null) {
        ConfirmDeleteDialog(
            itemName = deletingNote.title,
            onConfirm = {
                noteToDelete = null
                vm.deleteNote(deletingNote.id, showError)
            },
            onDismiss = { noteToDelete = null },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            text = { Text("همه‌ی یادداشت‌ها و رکوردهای فعلی با فایل پشتیبان جایگزین می‌شوند") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        LockState.skipNextLock = true
                        restoreLauncher.launch(arrayOf("*/*"))
                    },
                ) { Text("ادامه", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("انصراف") } },
        )
    }

    val shownError = errorMessage
    if (shownError != null) {
        AlertDialog(
            onDismissRequest = {
                errorMessage = null
                if (restartAfterError) {
                    restartAfterError = false
                    if (activity != null) BackupManager.restartApp(activity)
                }
            },
            text = { Text(shownError) },
            confirmButton = {
                TextButton(
                    onClick = {
                        errorMessage = null
                        if (restartAfterError) {
                            restartAfterError = false
                            if (activity != null) BackupManager.restartApp(activity)
                        }
                    },
                ) { Text("باشه") }
            },
        )
    }
}

/** Folder chip; text == null draws the "+" chip. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(
    text: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = Modifier
            .heightIn(min = 40.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (text == null) {
                TbIcon(R.drawable.ic_tb_plus, "پوشه‌ی جدید", size = 20.dp, tint = scheme.onSurface)
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    item: NoteListItem,
    showFolder: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.preview.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagText(text = item.dateText)
                    val folderName = item.folderName
                    if (showFolder && folderName != null) {
                        Spacer(Modifier.width(12.dp))
                        TagText(text = folderName, icon = R.drawable.ic_tb_folder)
                    }
                }
            }
            if (item.pinned) {
                Spacer(Modifier.width(8.dp))
                TbIcon(R.drawable.ic_tb_pinned, "سنجاق‌شده", size = 20.dp, tint = scheme.primary)
            }
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = color,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) TbIcon(R.drawable.ic_tb_check, null, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Create / rename dialog with one name field; [onConfirm] reports a Persian error through its second argument. */
@Composable
private fun FolderNameDialog(
    title: String,
    initial: String,
    confirmText: String,
    onConfirm: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = "نام پوشه",
                    isError = error != null,
                    supportingText = error,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (extraAction != null) {
                    Spacer(Modifier.height(8.dp))
                    extraAction()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) error = "نام پوشه را وارد کن" else onConfirm(name) { message -> error = message }
                },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private fun findActivity(context: Context): Activity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Restarts the app via the activity; without an activity it launches the main intent from the application context (as BackupManager.restartApp does). */
private fun restartApp(activity: Activity?) {
    if (activity != null) {
        BackupManager.restartApp(activity)
        return
    }
    val app = ICoordinatorApp.instance
    val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        app.startActivity(intent)
    }
    Process.killProcess(Process.myPid())
}
