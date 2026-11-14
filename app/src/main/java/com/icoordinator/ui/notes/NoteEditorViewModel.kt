package com.icoordinator.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.ICoordinatorApp
import com.icoordinator.personal.BlockType
import com.icoordinator.personal.NoteBlock
import com.icoordinator.personal.NoteBlocks
import com.icoordinator.personal.NoteCodec
import com.icoordinator.personal.NoteDocument
import com.icoordinator.personal.PersonalRepository
import com.icoordinator.personal.SpanKind
import com.icoordinator.personal.SpanOps
import com.icoordinator.personal.TextSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Asks one block to take the keyboard focus with the cursor at [cursor] (-1 = keep the selection); [token] makes every request unique. */
data class FocusRequest(val blockId: Long, val cursor: Int, val token: Long)

data class EditorState(
    val loaded: Boolean = false,
    val title: String = "",
    val blocks: List<NoteBlock> = emptyList(),
    val focusedBlockId: Long? = null,
    /** Selection inside the focused block. */
    val selStart: Int = 0,
    val selEnd: Int = 0,
    /**
     * Styles toggled while nothing is selected ("pending styles"). Key = kind; value = style value to
     * type with, or null = switched off. Applied to the next inserted text; dropped when the cursor moves away.
     */
    val pending: Map<SpanKind, Int?> = emptyMap(),
    val focusRequest: FocusRequest? = null,
    /** Bumped when the model changes a block's text itself (split, merge): the text fields must take the model text. */
    val structRevision: Long = 0L,
)

/** Whether [kind] is active at the current selection of the focused block (pending styles win over the text). */
fun EditorState.isStyleActive(kind: SpanKind): Boolean {
    if (selStart == selEnd && pending.containsKey(kind)) return pending[kind] != null
    val b = blocks.firstOrNull { it.id == focusedBlockId } ?: return false
    return SpanOps.isActive(b.spans, minOf(selStart, selEnd), maxOf(selStart, selEnd), kind)
}

/** Value of [kind] (colour / highlight index, size step) at the current selection, null when none or mixed. */
fun EditorState.styleValue(kind: SpanKind): Int? {
    if (selStart == selEnd && pending.containsKey(kind)) return pending[kind]
    val b = blocks.firstOrNull { it.id == focusedBlockId } ?: return null
    return SpanOps.valueAt(b.spans, minOf(selStart, selEnd), maxOf(selStart, selEnd), kind)
}

class NoteEditorViewModel(noteId: Long?, private val initialFolderId: Long?) : ViewModel() {

    private val repo: PersonalRepository get() = ICoordinatorApp.instance.personalRepository

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var noteId: Long? = noteId
    private var folderId: Long? = initialFolderId
    private var lastSavedKey: String? = null
    private var focusToken = 0L
    private var pendingBlockId: Long? = null
    private var pendingAnchor: Int = -1

    /** Own scope: the final save of onCleared must outlive viewModelScope, which is already cancelled then. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveMutex = Mutex()
    private var debounceJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val id = noteId
        if (id == null) {
            val doc = NoteDocument.empty()
            _state.value = EditorState(
                loaded = true,
                blocks = doc.blocks,
                focusedBlockId = doc.blocks.first().id,
                focusRequest = FocusRequest(doc.blocks.first().id, 0, ++focusToken),
            )
            return
        }
        val entity = withContext(Dispatchers.IO) { repo.getNote(id) }
        if (entity == null) {
            // the note vanished (deleted or restored over): continue as a new note
            noteId = null
            load()
            return
        }
        folderId = entity.folderId
        val doc = NoteCodec.decode(entity.content)
        lastSavedKey = saveKey(entity.title, doc)
        _state.value = EditorState(loaded = true, title = entity.title, blocks = doc.blocks)
    }

    // ---- title and focus ----

    fun onTitleChange(text: String) {
        _state.update { it.copy(title = text) }
        changed()
    }

    fun onBlockFocused(blockId: Long) {
        _state.update { s ->
            if (s.focusedBlockId == blockId) s
            else s.copy(focusedBlockId = blockId, selStart = 0, selEnd = 0, pending = emptyMap())
        }
    }

    /** The title field has the focus: toolbar actions have no block to act on until a block is focused again. */
    fun onTitleFocused() {
        _state.update { it.copy(focusedBlockId = null, selStart = 0, selEnd = 0, pending = emptyMap()) }
    }

    /** Gives the focus back to the focused block without touching its selection (after a popup closed). */
    fun restoreFocus() {
        val id = _state.value.focusedBlockId ?: return
        _state.update { it.copy(focusRequest = FocusRequest(id, KEEP_SELECTION, ++focusToken)) }
    }

    fun onFocusRequestHandled(token: Long) {
        _state.update { if (it.focusRequest?.token == token) it.copy(focusRequest = null) else it }
    }

    // ---- text editing ----

    /** Called for every change of a block's text field (typing, paste, selection move). */
    fun onBlockValueChange(blockId: Long, newText: String, selStart: Int, selEnd: Int) {
        val s = _state.value
        val block = s.blocks.firstOrNull { it.id == blockId } ?: return
        val text = newText.replace("\r\n", "\n").replace('\r', '\n')
        if (text == block.text) {
            onBlockSelection(blockId, selStart, selEnd)
            return
        }
        if (text == "\n" && block.text.isEmpty() && endsListOnEnter(block.type)) {
            endList(block)
            return
        }
        if (text.contains('\n')) {
            handleNewlines(block, text, selEnd)
            return
        }
        var spans = SpanOps.applyEdit(block.text, text, block.spans)
        var pending = s.pending
        val inserted = insertionRange(block.text, text)
        if (pending.isNotEmpty() && s.selStart == s.selEnd && inserted != null && s.focusedBlockId == blockId) {
            spans = applyPending(spans, text.length, inserted.first, inserted.second, pending)
        } else {
            pending = emptyMap()
        }
        val cursor = selEnd.coerceIn(0, text.length)
        pendingBlockId = blockId
        pendingAnchor = cursor
        _state.update { st ->
            st.copy(
                blocks = st.blocks.map { if (it.id == blockId) it.copy(text = text, spans = spans) else it },
                focusedBlockId = blockId,
                selStart = selStart.coerceIn(0, text.length),
                selEnd = cursor,
                pending = pending,
            )
        }
        changed()
    }

    /** Selection moved (or the block got the focus) without a text change. */
    fun onBlockSelection(blockId: Long, selStart: Int, selEnd: Int) {
        val keepPending = pendingBlockId == blockId && selStart == selEnd && selStart == pendingAnchor
        _state.update {
            it.copy(
                focusedBlockId = blockId,
                selStart = selStart,
                selEnd = selEnd,
                pending = if (keepPending && it.focusedBlockId == blockId) it.pending else emptyMap(),
            )
        }
    }

    /** Pure insertion at one place -> [start, end) of the inserted text; anything else -> null. */
    private fun insertionRange(oldText: String, newText: String): Pair<Int, Int>? {
        if (newText.length <= oldText.length) return null
        val minLen = oldText.length
        var p = 0
        while (p < minLen && oldText[p] == newText[p]) p++
        val n = newText.length - oldText.length
        // the rest must match as suffix, otherwise it was a replacement
        val tailOk = oldText.substring(p) == newText.substring(p + n)
        return if (tailOk) p to (p + n) else null
    }

    private fun applyPending(
        spans: List<TextSpan>,
        length: Int,
        start: Int,
        end: Int,
        pending: Map<SpanKind, Int?>,
    ): List<TextSpan> {
        var out = spans
        for ((kind, value) in pending) {
            out = when (kind) {
                SpanKind.BOLD, SpanKind.ITALIC, SpanKind.UNDERLINE -> {
                    val active = SpanOps.isActive(out, start, end, kind)
                    val want = value != null
                    if (active != want) SpanOps.toggle(out, length, start, end, kind) else out
                }
                else -> SpanOps.setValue(out, length, start, end, kind, value)
            }
        }
        return out
    }

    private fun endsListOnEnter(type: BlockType): Boolean =
        type == BlockType.BULLET || type == BlockType.NUMBERED || type == BlockType.CHECK

    /** Enter in an empty list item ends the list: the item becomes a paragraph and keeps the focus. */
    private fun endList(block: NoteBlock) {
        pendingBlockId = null
        _state.update { st ->
            st.copy(
                blocks = st.blocks.map {
                    if (it.id == block.id) it.copy(type = BlockType.PARAGRAPH, checked = false) else it
                },
                focusedBlockId = block.id,
                selStart = 0,
                selEnd = 0,
                pending = emptyMap(),
                focusRequest = FocusRequest(block.id, 0, ++focusToken),
                structRevision = st.structRevision + 1,
            )
        }
        changed()
    }

    /** Enter (one typed "\n") splits the block; a pasted multi-line text becomes several blocks of the same type. */
    private fun handleNewlines(block: NoteBlock, text: String, cursorAfter: Int) {
        val pasted = text.length - block.text.length > 1
        var current = block.copy(text = text, spans = SpanOps.applyEdit(block.text, text, block.spans))
        val doc = NoteDocument(_state.value.blocks)
        var nextId = NoteBlocks.nextId(doc)
        val pieces = ArrayList<NoteBlock>()
        var consumed = 0
        while (true) {
            val idx = current.text.indexOf('\n')
            if (idx < 0) break
            val (first, second0) = NoteBlocks.split(current, idx, nextId++)
            var second = dropFirstChar(second0)
            if (pasted) second = second.copy(type = block.type)
            pieces.add(first)
            consumed += first.text.length + 1
            current = second
        }
        pieces.add(current)
        val last = pieces.last()
        val cursor = (cursorAfter - consumed).coerceIn(0, last.text.length)
        pendingBlockId = null
        _state.update { st ->
            val out = ArrayList<NoteBlock>()
            for (b in st.blocks) {
                if (b.id == block.id) out.addAll(pieces) else out.add(b)
            }
            st.copy(
                blocks = out,
                focusedBlockId = last.id,
                selStart = cursor,
                selEnd = cursor,
                pending = emptyMap(),
                focusRequest = FocusRequest(last.id, cursor, ++focusToken),
                structRevision = st.structRevision + 1,
            )
        }
        changed()
    }

    private fun dropFirstChar(b: NoteBlock): NoteBlock {
        if (b.text.isEmpty()) return b
        val t = b.text.substring(1)
        val spans = SpanOps.normalize(b.spans.map { it.copy(start = it.start - 1, end = it.end - 1) }, t.length)
        return b.copy(text = t, spans = spans)
    }

    /**
     * Backspace pressed with the cursor at position 0 of [blockId]. Returns true when it was handled.
     * Non-paragraph -> paragraph; otherwise merge into a previous text block; after an image nothing happens.
     */
    fun onBackspaceAtStart(blockId: Long): Boolean {
        val blocks = _state.value.blocks
        val index = blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return false
        val block = blocks[index]
        if (block.type == BlockType.IMAGE) return false
        if (block.type != BlockType.PARAGRAPH) {
            replaceBlock(blockId) { it.copy(type = BlockType.PARAGRAPH, checked = false) }
            changed()
            return true
        }
        if (index == 0) return false
        val previous = blocks[index - 1]
        if (previous.type == BlockType.IMAGE) return true
        val joinAt = previous.text.length
        val merged = NoteBlocks.mergeIntoPrevious(previous, block)
        pendingBlockId = null
        _state.update { st ->
            st.copy(
                blocks = st.blocks.filter { it.id != blockId }.map { if (it.id == previous.id) merged else it },
                focusedBlockId = previous.id,
                selStart = joinAt,
                selEnd = joinAt,
                pending = emptyMap(),
                focusRequest = FocusRequest(previous.id, joinAt, ++focusToken),
                structRevision = st.structRevision + 1,
            )
        }
        changed()
        return true
    }

    // ---- toolbar actions ----

    /** BOLD / ITALIC / UNDERLINE. With a selection it toggles the range, otherwise it toggles a pending style. */
    fun toggleStyle(kind: SpanKind) {
        val s = _state.value
        val id = s.focusedBlockId ?: return
        val block = s.blocks.firstOrNull { it.id == id } ?: return
        if (block.type == BlockType.IMAGE) return
        val start = minOf(s.selStart, s.selEnd)
        val end = maxOf(s.selStart, s.selEnd)
        if (start != end) {
            val spans = SpanOps.toggle(block.spans, block.text.length, start, end, kind)
            replaceBlock(id) { it.copy(spans = spans) }
            changed()
        } else {
            val activeNow = s.isStyleActive(kind)
            setPending(kind, if (activeNow) null else 0)
        }
    }

    /** HIGHLIGHT / COLOR / SIZE; [value] null clears the style. */
    fun setStyleValue(kind: SpanKind, value: Int?) {
        val s = _state.value
        val id = s.focusedBlockId ?: return
        val block = s.blocks.firstOrNull { it.id == id } ?: return
        if (block.type == BlockType.IMAGE) return
        val start = minOf(s.selStart, s.selEnd)
        val end = maxOf(s.selStart, s.selEnd)
        if (start != end) {
            val spans = SpanOps.setValue(block.spans, block.text.length, start, end, kind, value)
            replaceBlock(id) { it.copy(spans = spans) }
            changed()
        } else {
            setPending(kind, value)
        }
    }

    private fun setPending(kind: SpanKind, value: Int?) {
        pendingBlockId = _state.value.focusedBlockId
        pendingAnchor = _state.value.selStart
        _state.update { it.copy(pending = it.pending + (kind to value)) }
    }

    /** Heading / bullet / numbered / checklist button: the same type again goes back to PARAGRAPH. */
    fun toggleBlockType(type: BlockType) {
        val id = _state.value.focusedBlockId ?: return
        val block = _state.value.blocks.firstOrNull { it.id == id } ?: return
        if (block.type == BlockType.IMAGE) return
        val target = if (block.type == type) BlockType.PARAGRAPH else type
        replaceBlock(id) { it.copy(type = target, checked = false) }
        changed()
    }

    fun setChecked(blockId: Long, checked: Boolean) {
        replaceBlock(blockId) { it.copy(checked = checked) }
        changed()
    }

    /** Inserts an image block after the focused block (or at the end), followed by an empty paragraph that gets the focus. */
    fun insertImage(fileName: String) {
        val s = _state.value
        val doc = NoteDocument(s.blocks)
        val imageId = NoteBlocks.nextId(doc)
        val paragraphId = imageId + 1
        val image = NoteBlock(id = imageId, type = BlockType.IMAGE, imageName = fileName)
        val paragraph = NoteBlock(id = paragraphId, type = BlockType.PARAGRAPH)
        val at = s.blocks.indexOfFirst { it.id == s.focusedBlockId }
        val insertAt = if (at < 0) s.blocks.size else at + 1
        val out = s.blocks.toMutableList()
        out.add(insertAt, image)
        out.add(insertAt + 1, paragraph)
        pendingBlockId = null
        _state.update {
            it.copy(
                blocks = out,
                focusedBlockId = paragraphId,
                selStart = 0,
                selEnd = 0,
                pending = emptyMap(),
                focusRequest = FocusRequest(paragraphId, 0, ++focusToken),
            )
        }
        changed()
    }

    /** Removes an image block; the file is deleted by the repository on the next save. */
    fun deleteImage(blockId: Long) {
        _state.update { st ->
            val rest = st.blocks.filter { it.id != blockId }
            st.copy(blocks = if (rest.isEmpty()) NoteDocument.empty().blocks else rest)
        }
        changed()
    }

    private fun replaceBlock(id: Long, change: (NoteBlock) -> NoteBlock) {
        _state.update { st -> st.copy(blocks = st.blocks.map { if (it.id == id) change(it) else it }) }
    }

    // ---- saving ----

    private fun changed() {
        debounceJob?.cancel()
        debounceJob = ioScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveNow()
        }
    }

    private fun saveKey(title: String, doc: NoteDocument): String = title.trim() + "\u0000" + NoteCodec.encode(doc)

    /** Saves when something changed; a new note that is still blank is not saved. Not cancellable once started. */
    suspend fun saveNow() {
        withContext(NonCancellable + Dispatchers.IO) {
            saveMutex.withLock {
                val s = _state.value
                if (!s.loaded) return@withLock
                val doc = NoteDocument(s.blocks)
                val key = saveKey(s.title, doc)
                if (key == lastSavedKey) return@withLock
                if (noteId == null && repo.isBlank(s.title, doc)) return@withLock
                val newId = repo.saveNote(noteId, folderId, s.title, doc, System.currentTimeMillis())
                noteId = newId
                lastSavedKey = key
            }
        }
    }

    /** For ON_STOP: save without waiting. */
    fun saveInBackground() {
        debounceJob?.cancel()
        ioScope.launch { saveNow() }
    }

    override fun onCleared() {
        debounceJob?.cancel()
        // the VM is gone: one last non-cancellable save on its own scope
        ioScope.launch { saveNow() }
        super.onCleared()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 800L
        const val KEEP_SELECTION = -1
    }
}
