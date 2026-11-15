package com.icoordinator.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.ICoordinatorApp
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.domain.PersianCalendar
import com.icoordinator.personal.BlockType
import com.icoordinator.personal.NoteBlock
import com.icoordinator.personal.NoteBlocks
import com.icoordinator.personal.NoteCodec
import com.icoordinator.personal.NoteEntity
import com.icoordinator.personal.NoteFolderEntity
import com.icoordinator.personal.NoteText
import com.icoordinator.personal.PersonalRepository
import com.icoordinator.ui.components.FolderTileData
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line of a note card's preview: a text block as saved, with its list number. */
data class NotePreviewLine(val block: NoteBlock, val number: Int)

/** One row of the notes list, already prepared for display. */
data class NoteListItem(
    val id: Long,
    val folderId: Long?,
    val folderName: String?,
    val title: String,
    val preview: String,
    val dateText: String,
    val pinned: Boolean,
    /** First lines of the note in their saved format (bullet, check, heading, styles). */
    val lines: List<NotePreviewLine> = emptyList(),
    /** More text follows the shown lines. */
    val moreLines: Boolean = false,
    val imageCount: Int = 0,
)

/**
 * Everything both notes pages need. Main page: [folderTiles] + unfiled notes ([pinned], [others]); while [query] is
 * not blank [pinned]/[others] hold the matches from ALL folders and the tiles are hidden by the screen.
 * Folder page: [folderPageFor] is the folder id the folder fields belong to; [folderName] is null when it does not exist.
 */
data class NotesState(
    val loaded: Boolean = false,
    val folders: List<NoteFolderEntity> = emptyList(),
    val folderTiles: List<FolderTileData> = emptyList(),
    val query: String = "",
    val pinned: List<NoteListItem> = emptyList(),
    val others: List<NoteListItem> = emptyList(),
    val folderPageFor: Long? = null,
    val folderName: String? = null,
    val folderQuery: String = "",
    val folderPinned: List<NoteListItem> = emptyList(),
    val folderOthers: List<NoteListItem> = emptyList(),
)

class NotesViewModel : ViewModel() {

    private val repo: PersonalRepository get() = ICoordinatorApp.instance.personalRepository

    private val query = MutableStateFlow("")
    private val openFolderId = MutableStateFlow<Long?>(null)
    private val folderQuery = MutableStateFlow("")

    val state: StateFlow<NotesState> = combine(
        repo.observeFolders(),
        repo.observeNotes(),
        query,
        openFolderId,
        folderQuery,
    ) { folders, notes, q, openId, fq ->
        val names = folders.associate { it.id to it.name }
        val counts = notes.groupingBy { it.folderId }.eachCount()
        val tiles = folders.map { FolderTileData(it.id, it.name, counts[it.id] ?: 0) }
        // notes arrive pinned first; blank query = notes without a folder, otherwise matches from every folder
        val main = (if (q.isBlank()) notes.filter { it.folderId == null } else notes.filter { NoteText.matches(it.title, it.plainText, q) })
            .map { toItem(it, if (q.isBlank()) null else names[it.folderId ?: -1L]) }
        val inFolder = if (openId == null) emptyList() else notes
            .filter { it.folderId == openId && NoteText.matches(it.title, it.plainText, fq) }
            .map { toItem(it, null) }
        NotesState(
            loaded = true,
            folders = folders,
            folderTiles = tiles,
            query = q,
            pinned = main.filter { it.pinned },
            others = main.filter { !it.pinned },
            folderPageFor = openId,
            folderName = openId?.let { names[it] },
            folderQuery = fq,
            folderPinned = inFolder.filter { it.pinned },
            folderOthers = inFolder.filter { !it.pinned },
        )
    }
        // the card previews decode every note: keep that off the main thread
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesState())

    fun setQuery(text: String) {
        query.value = text
    }

    /** Called when the folder page opens: selects the folder and clears the folder search. */
    fun openFolder(id: Long) {
        folderQuery.value = ""
        openFolderId.value = id
    }

    fun setFolderQuery(text: String) {
        folderQuery.value = text
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
                withContext(Dispatchers.IO) { repo.addFolder(name) }
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
        val blocks = NoteCodec.decode(n.content).blocks
        var textLines = blocks.withIndex()
            .filter { it.value.type != BlockType.IMAGE && it.value.text.isNotBlank() }
            .map { NotePreviewLine(it.value, NoteBlocks.numberFor(blocks, it.index)) }
        // without a title the first line is already shown as the title
        if (title.isEmpty()) textLines = textLines.drop(1)
        return NoteListItem(
            id = n.id,
            folderId = n.folderId,
            folderName = folderName,
            title = shownTitle,
            preview = preview,
            dateText = dateText(n.updatedAt),
            pinned = n.pinned,
            lines = textLines.take(PREVIEW_LINES),
            moreLines = textLines.size > PREVIEW_LINES,
            imageCount = blocks.count { it.type == BlockType.IMAGE },
        )
    }

    private fun dateText(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        return AccountingFormat.digits(PersianCalendar.format(date))
    }

    private companion object {
        const val PREVIEW_CHARS = 120
        const val PREVIEW_LINES = 5
        const val TITLE_FALLBACK_CHARS = 60
    }
}
