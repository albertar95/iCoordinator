package com.icoordinator.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.icoordinator.R
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.FolderGrid
import com.icoordinator.ui.components.FolderTileData
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.LetterCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.QuickAction
import com.icoordinator.ui.components.SectionLabel
import com.icoordinator.ui.components.TagText
import com.icoordinator.vault.VaultEntry
import com.icoordinator.vault.VaultFolder
import com.icoordinator.vault.VaultSnapshot

/**
 * Spinner while the first snapshot is loading, the retry state when loading failed, otherwise [content].
 * Nothing here is saved across process death (no rememberSaveable): search text and dialogs are plain remember.
 */
@Composable
private fun SnapshotGate(vm: VaultViewModel, content: @Composable (VaultSnapshot) -> Unit) {
    val snapshot by vm.snapshot.collectAsState()
    val loadError by vm.loadError.collectAsState()
    val snap = snapshot
    if (snap == null && loadError) {
        EmptyState(
            icon = R.drawable.ic_tb_shield_lock,
            title = "خواندن رمزها ممکن نشد",
            message = "",
            action = { PillButton(text = "تلاش دوباره", onClick = { vm.retry() }) },
        )
    } else if (snap == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        content(snap)
    }
}

/** Vault main page: folder tiles, entries without a folder, search across all folders (the secret is never shown here). */
@Composable
fun VaultListScreen(
    vm: VaultViewModel,
    onExit: () -> Unit,
    onOpenEntry: (id: Long?, folderId: Long?) -> Unit,
    onOpenFolder: (folderId: Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var backupMode by remember { mutableStateOf<VaultBackupMode?>(null) }
    var folderDialogOpen by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<VaultFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<VaultFolder?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PageHeader(
                title = "مدیریت رمزها",
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
                            editingFolder = null
                            folderDialogOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("پشتیبان‌گیری رمزها") },
                        onClick = {
                            menuOpen = false
                            backupMode = VaultBackupMode.EXPORT
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("بازیابی پشتیبان رمزها") },
                        onClick = {
                            menuOpen = false
                            backupMode = VaultBackupMode.IMPORT
                        },
                    )
                }
            }
        }

        SnapshotGate(vm) { snap ->
            VaultListContent(
                snapshot = snap,
                query = query,
                onQueryChange = { query = it },
                onNewFolder = {
                    editingFolder = null
                    folderDialogOpen = true
                },
                onEditFolder = { folder ->
                    editingFolder = folder
                    folderDialogOpen = true
                },
                onNewEntry = { onOpenEntry(null, null) },
                onOpenEntry = { id -> onOpenEntry(id, null) },
                onOpenFolder = onOpenFolder,
            )
        }
    }

    VaultBackupDialogs(mode = backupMode, onDismiss = { backupMode = null })

    if (folderDialogOpen) {
        val folder = editingFolder
        FolderNameDialog(
            folder = folder,
            vm = vm,
            onDelete = {
                folderDialogOpen = false
                deletingFolder = folder
            },
            onDismiss = { folderDialogOpen = false },
        )
    }

    val toDelete = deletingFolder
    if (toDelete != null) {
        ConfirmDeleteDialog(
            itemName = toDelete.name,
            onConfirm = {
                vm.deleteFolder(toDelete.id)
                deletingFolder = null
            },
            onDismiss = { deletingFolder = null },
        )
    }
}

/** Search field with the "new password" quick action beside it. */
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    onNewEntry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            label = label,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        QuickAction(
            icon = R.drawable.ic_tb_plus,
            label = "رمز جدید",
            onClick = onNewEntry,
            accent = true,
        )
    }
}

@Composable
private fun VaultListContent(
    snapshot: VaultSnapshot,
    query: String,
    onQueryChange: (String) -> Unit,
    onNewFolder: () -> Unit,
    onEditFolder: (VaultFolder) -> Unit,
    onNewEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenFolder: (Long) -> Unit,
) {
    val needle = remember(query) { normalizeForSearch(query.trim()) }
    val searching = needle.isNotEmpty()
    val folderNames = remember(snapshot) { snapshot.folders.associate { it.id to it.name } }
    val tiles = remember(snapshot) {
        snapshot.folders.map { folder ->
            FolderTileData(folder.id, folder.name, snapshot.entries.count { it.folderId == folder.id })
        }
    }
    // blank query: entries without a folder (an id of a folder that no longer exists counts as none);
    // otherwise matches from every folder
    val shown = remember(snapshot, needle) {
        if (searching) {
            snapshot.entries.filter { matchesQuery(it, needle) }
        } else {
            snapshot.entries.filter { it.folderId == null || !folderNames.containsKey(it.folderId) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchRow(query = query, onQueryChange = onQueryChange, label = "جستجو", onNewEntry = onNewEntry)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (snapshot.unreadable > 0) {
                item(key = "unreadable") {
                    Text(
                        text = AccountingFormat.digits("${snapshot.unreadable} مورد قابل خواندن نیست"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            if (!searching) {
                item(key = "label_folders") { SectionLabel("پوشه‌ها") }
                item(key = "folder_grid") {
                    FolderGrid(
                        folders = tiles,
                        onOpen = onOpenFolder,
                        onLongPress = { id -> snapshot.folders.firstOrNull { it.id == id }?.let(onEditFolder) },
                        onAdd = onNewFolder,
                    )
                }
            }
            if (shown.isEmpty()) {
                if (searching) {
                    item(key = "none") {
                        Text(
                            text = "موردی پیدا نشد",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                        )
                    }
                } else if (snapshot.folders.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_tb_shield_lock,
                            title = "رمزی ذخیره نشده",
                            message = "با دکمه‌ی رمز جدید شروع کن",
                        )
                    }
                }
            } else {
                if (!searching) item(key = "label_entries") { SectionLabel("رمزها") }
                items(shown, key = { "e_" + it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        folderName = if (searching) folderNames[entry.folderId] else null,
                        onClick = { onOpenEntry(entry.id) },
                    )
                }
            }
        }
    }
}

/** Page of one folder: its entries, search inside the folder, rename / delete in the menu. */
@Composable
fun VaultFolderScreen(
    vm: VaultViewModel,
    folderId: Long,
    onBack: () -> Unit,
    onOpenEntry: (id: Long?, folderId: Long?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val snapshot by vm.snapshot.collectAsState()
    val folder = snapshot?.folders?.firstOrNull { it.id == folderId }

    // a folder that no longer exists (deleted, or gone after a restore) sends the user back
    val loaded = snapshot != null
    LaunchedEffect(loaded, folder == null) {
        if (loaded && folder == null) onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PageHeader(
                title = folder?.name ?: "",
                onBack = onBack,
                actionIcon = R.drawable.ic_tb_dots_vertical,
                actionDescription = "گزینه‌ها",
                onAction = { menuOpen = true },
            )
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("تغییر نام پوشه") },
                        onClick = {
                            menuOpen = false
                            showRename = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("حذف پوشه", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            showDelete = true
                        },
                    )
                }
            }
        }

        SnapshotGate(vm) { snap ->
            if (folder != null) {
                val needle = normalizeForSearch(query.trim())
                val inFolder = snap.entries.filter { it.folderId == folderId }
                val shown = inFolder.filter { matchesQuery(it, needle) }
                SearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    label = "جستجو در این پوشه",
                    onNewEntry = { onOpenEntry(null, folderId) },
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            if (inFolder.isEmpty()) {
                                EmptyState(
                                    icon = R.drawable.ic_tb_shield_lock,
                                    title = "این پوشه خالی است",
                                    message = "با دکمه‌ی رمز جدید شروع کن",
                                )
                            } else {
                                Text(
                                    text = "موردی پیدا نشد",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                                )
                            }
                        }
                    }
                    items(shown, key = { "e_" + it.id }) { entry ->
                        EntryRow(entry = entry, folderName = null, onClick = { onOpenEntry(entry.id, null) })
                    }
                }
            }
        }
    }

    if (showRename && folder != null) {
        FolderNameDialog(
            folder = folder,
            vm = vm,
            onDelete = {},
            onDismiss = { showRename = false },
            showDelete = false,
        )
    }
    if (showDelete && folder != null) {
        ConfirmDeleteDialog(
            itemName = folder.name,
            onConfirm = {
                showDelete = false
                // entries move out of the folder (repository), then back to the main page
                vm.deleteFolder(folder.id)
                onBack()
            },
            onDismiss = { showDelete = false },
        )
    }
}

/** Entry row: title and user name only (never the secret); [folderName] is given while searching across folders. */
@Composable
private fun EntryRow(entry: VaultEntry, folderName: String?, onClick: () -> Unit) {
    val title = entry.payload.title
    val username = entry.payload.username
    val startsWithLetter = title.trim().firstOrNull()?.isLetter() == true
    PillRow(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        leading = {
            if (startsWithLetter) LetterCircle(text = title) else IconCircle(icon = R.drawable.ic_tb_key)
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (username.isNotBlank()) {
            Text(
                text = username,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (folderName != null) {
            TagText(text = folderName, icon = R.drawable.ic_tb_folder)
        }
    }
}

/** New folder (folder == null) or rename / delete an existing one. */
@Composable
private fun FolderNameDialog(
    folder: VaultFolder?,
    vm: VaultViewModel,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    showDelete: Boolean = true,
) {
    var name by remember { mutableStateOf(folder?.name ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (folder == null) "پوشه‌ی جدید" else "ویرایش پوشه") },
        text = {
            Column {
                AppTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = "نام پوشه",
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error,
                )
                if (folder != null && showDelete) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDelete) {
                        Text("حذف پوشه", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val done: (String?) -> Unit = { problem ->
                        if (problem == null) onDismiss() else error = problem
                    }
                    if (folder == null) vm.addFolder(name, done) else vm.renameFolder(folder.id, name, done)
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

// Arabic ye / kaf are folded to the Persian letters, and the text is lower-cased.
private fun normalizeForSearch(text: String): String =
    text.replace('ي', 'ی').replace('ك', 'ک').lowercase()

private fun matchesQuery(entry: VaultEntry, needle: String): Boolean {
    if (needle.isEmpty()) return true
    val p = entry.payload
    return normalizeForSearch(p.title).contains(needle) ||
        normalizeForSearch(p.username).contains(needle) ||
        normalizeForSearch(p.url).contains(needle)
}
