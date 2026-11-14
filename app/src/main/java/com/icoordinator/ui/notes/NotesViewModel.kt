package com.icoordinator.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.ICoordinatorApp
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.personal.NoteEntity
import com.icoordinator.personal.NoteFolderEntity
import com.icoordinator.personal.NoteText
import com.icoordinator.personal.PersonalRepository
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row of the notes list, already prepared for display. */
data class NoteListItem(
    val id: Long,
    val folderId: Long?,
    val folderName: String?,
    val title: String,
    val preview: String,
    val dateText: String,
    val pinned: Boolean,
)

data class NotesState(
    val loaded: Boolean = false,
    val folders: List<NoteFolderEntity> = emptyList(),
    /** null = "همه". */
    val selectedFolderId: Long? = null,
    val query: String = "",
    val pinned: List<NoteListItem> = emptyList(),
    val others: List<NoteListItem> = emptyList(),
)

class NotesViewModel : ViewModel() {

    private val repo: PersonalRepository get() = ICoordinatorApp.instance.personalRepository

    private val selectedFolder = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<NotesState> = combine(
        repo.observeFolders(),
        repo.observeNotes(),
        selectedFolder,
        query,
    ) { folders, notes, selected, q ->
        // a folder that no longer exists falls back to "همه"
        val effective = if (selected != null && folders.none { it.id == selected }) null else selected
        val names = folders.associate { it.id to it.name }
        val items = notes
            .filter { effective == null || it.folderId == effective }
            .filter { NoteText.matches(it.title, it.plainText, q) }
            .map { toItem(it, if (effective == null) names[it.folderId ?: -1L] else null) }
        NotesState(
            loaded = true,
            folders = folders,
            selectedFolderId = effective,
            query = q,
            pinned = items.filter { it.pinned },
            others = items.filter { !it.pinned },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun selectFolder(id: Long?) {
        selectedFolder.value = id
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setPinned(id: Long, pinned: Boolean, onResult: (String?) -> Unit = {}) =
        launchAction(onResult) { repo.setPinned(id, pinned) }

    fun moveNote(id: Long, folderId: Long?, onResult: (String?) -> Unit = {}) =
        launchAction(onResult) { repo.moveNote(id, folderId) }

    fun deleteNote(id: Long, onResult: (String?) -> Unit = {}) =
        launchAction(onResult) { repo.deleteNote(id) }

    fun addFolder(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                val newId = withContext(Dispatchers.IO) { repo.addFolder(name) }
                selectedFolder.value = newId
                null
            } catch (e: IllegalArgumentException) {
                "نام پوشه را وارد کن"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "ساخت پوشه ممکن نشد"
            }
            onResult(error)
        }
    }

    fun renameFolder(id: Long, name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                withContext(Dispatchers.IO) { repo.renameFolder(id, name) }
                null
            } catch (e: IllegalArgumentException) {
                "نام پوشه را وارد کن"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "تغییر نام پوشه ممکن نشد"
            }
            onResult(error)
        }
    }

    fun deleteFolder(id: Long, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val error = try {
                withContext(Dispatchers.IO) { repo.deleteFolder(id) }
                if (selectedFolder.value == id) selectedFolder.value = null
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "حذف پوشه ممکن نشد"
            }
            onResult(error)
        }
    }

    private fun launchAction(onResult: (String?) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            val error = try {
                withContext(Dispatchers.IO) { block() }
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "انجام نشد"
            }
            onResult(error)
        }
    }

    private fun toItem(n: NoteEntity, folderName: String?): NoteListItem {
        val lines = n.plainText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val title = n.title.trim()
        val shownTitle: String
        val previewSource: String
        if (title.isNotEmpty()) {
            shownTitle = title
            previewSource = lines.joinToString(" ")
        } else if (lines.isNotEmpty()) {
            shownTitle = lines.first().take(TITLE_FALLBACK_CHARS)
            previewSource = lines.drop(1).joinToString(" ")
        } else {
            shownTitle = "بدون عنوان"
            previewSource = ""
        }
        val preview = if (previewSource.length <= PREVIEW_CHARS) previewSource else previewSource.take(PREVIEW_CHARS).trimEnd() + "…"
        return NoteListItem(
            id = n.id,
            folderId = n.folderId,
            folderName = folderName,
            title = shownTitle,
            preview = preview,
            dateText = dateText(n.updatedAt),
            pinned = n.pinned,
        )
    }

    private fun dateText(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        return AccountingFormat.digits(PersianCalendar.format(date))
    }

    private companion object {
        const val PREVIEW_CHARS = 120
        const val TITLE_FALLBACK_CHARS = 60
    }
}
