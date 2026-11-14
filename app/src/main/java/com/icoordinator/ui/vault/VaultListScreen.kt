package com.icoordinator.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.icoordinator.R
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.LetterCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.PillRow
import com.icoordinator.ui.components.QuickAction
import com.icoordinator.ui.components.TbIcon
import com.icoordinator.vault.VaultEntry
import com.icoordinator.vault.VaultFolder
import com.icoordinator.vault.VaultSnapshot

/** Vault list: folder chips, search, entry rows (title and user name only; the secret is never shown here). */
@Composable
fun VaultListScreen(
    vm: VaultViewModel,
    onExit: () -> Unit,
    onOpenEntry: (id: Long?, folderId: Long?) -> Unit,
) {
    val snapshot by vm.snapshot.collectAsState()
    val loadError by vm.loadError.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf<Long?>(null) }
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
            // a deleted folder can no longer be the selected one
            val activeFolder = selectedFolder?.takeIf { id -> snap.folders.any { it.id == id } }
            VaultListContent(
                snapshot = snap,
                query = query,
                onQueryChange = { query = it },
                selectedFolder = activeFolder,
                onSelectFolder = { selectedFolder = it },
                onNewFolder = {
                    editingFolder = null
                    folderDialogOpen = true
                },
                onEditFolder = { folder ->
                    editingFolder = folder
                    folderDialogOpen = true
                },
                onNewEntry = { onOpenEntry(null, activeFolder) },
                onOpenEntry = { id -> onOpenEntry(id, null) },
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
                if (selectedFolder == toDelete.id) selectedFolder = null
                deletingFolder = null
            },
            onDismiss = { deletingFolder = null },
        )
    }
}

@Composable
private fun VaultListContent(
    snapshot: VaultSnapshot,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFolder: Long?,
    onSelectFolder: (Long?) -> Unit,
    onNewFolder: () -> Unit,
    onEditFolder: (VaultFolder) -> Unit,
    onNewEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val needle = remember(query) { normalizeForSearch(query.trim()) }
    val shown = remember(snapshot, needle, selectedFolder) {
        snapshot.entries.filter { entry ->
            (selectedFolder == null || entry.folderId == selectedFolder) && matchesQuery(entry, needle)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(
                value = query,
                onValueChange = onQueryChange,
                label = "جستجو",
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
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "chip_all") {
                FolderChip(
                    label = "همه",
                    selected = selectedFolder == null,
                    onSelect = { onSelectFolder(null) },
                    onLongPress = null,
                )
            }
            items(snapshot.folders, key = { "chip_" + it.id }) { folder ->
                FolderChip(
                    label = folder.name,
                    selected = selectedFolder == folder.id,
                    onSelect = { onSelectFolder(folder.id) },
                    onLongPress = { onEditFolder(folder) },
                )
            }
            item(key = "chip_new") {
                FolderChip(
                    label = "پوشه‌ی جدید",
                    selected = false,
                    onSelect = onNewFolder,
                    onLongPress = null,
                    icon = R.drawable.ic_tb_plus,
                    showLabel = false,
                )
            }
        }
        if (snapshot.unreadable > 0) {
            Text(
                text = AccountingFormat.digits("${snapshot.unreadable} مورد قابل خواندن نیست"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp),
            )
        }
        when {
            snapshot.entries.isEmpty() -> EmptyState(
                icon = R.drawable.ic_tb_shield_lock,
                title = "رمزی ذخیره نشده",
                message = "با دکمه‌ی رمز جدید شروع کن",
            )
            shown.isEmpty() -> Text(
                text = "موردی پیدا نشد",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.id }) { entry ->
                    EntryRow(entry = entry, onClick = { onOpenEntry(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: VaultEntry, onClick: () -> Unit) {
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
    }
}

/** Folder chip: tap selects, long press (when given) opens the rename / delete dialog. */
@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onLongPress: (() -> Unit)?,
    icon: Int? = null,
    showLabel: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surface)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .pointerInput(label, selected) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onLongPress = { onLongPress?.invoke() },
                )
            }
            .semantics {
                role = Role.Button
                onClick(label = null) {
                    onSelect()
                    true
                }
                if (onLongPress != null) {
                    onLongClick(label = null) {
                        onLongPress()
                        true
                    }
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                TbIcon(
                    icon = icon,
                    contentDescription = if (showLabel) null else label,
                    size = 18.dp,
                    tint = scheme.onSurface,
                )
            }
            if (showLabel) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) scheme.primary else scheme.onSurface,
                    maxLines = 1,
                )
            }
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
                if (folder != null) {
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
