package com.icoordinator.personal

import androidx.room.withTransaction
import com.icoordinator.accounting.TransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Image files of notes (filesDir/note_images in the app; a temp dir in tests). */
interface NoteImageStore { fun delete(name: String) }

/** Single entry point of notes, note folders and records (personal.db). */
class PersonalRepository internal constructor(
    private val dao: PersonalDao,
    private val runner: TransactionRunner,
    private val images: NoteImageStore,
) {

    constructor(db: PersonalDatabase, images: NoteImageStore) : this(
        db.personalDao(),
        object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = db.withTransaction { block() }
        },
        images,
    )

    fun observeFolders(): Flow<List<NoteFolderEntity>> = dao.observeFolders()
    fun observeNotes(): Flow<List<NoteEntity>> = dao.observeNotes()
    fun observeRecords(): Flow<List<RecordEntity>> = dao.observeRecords()

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    /** New note when [id] is null/0 (or has no row); keeps createdAt and pinned on edit. Image files dropped from the document are deleted after the write. */
    suspend fun saveNote(id: Long?, folderId: Long?, title: String, doc: NoteDocument, now: Long): Long {
        val existing = if (id != null && id != 0L) dao.getNote(id) else null
        val entity = NoteEntity(
            id = if (id != null && id != 0L) id else 0L,
            folderId = folderId,
            title = title.trim(),
            content = NoteCodec.encode(doc),
            plainText = NoteText.plainText(doc),
            pinned = existing?.pinned ?: false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val rowId = dao.upsertNote(entity)
        // Room's @Upsert returns -1 when it updated an existing row
        val savedId = if (entity.id != 0L) entity.id else rowId
        if (existing != null) {
            val removed = NoteBlocks.imageNames(NoteCodec.decode(existing.content)) - NoteBlocks.imageNames(doc)
            removed.forEach { images.delete(it) }
        }
        return savedId
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val n = dao.getNote(id) ?: return
        if (n.pinned != pinned) dao.upsertNote(n.copy(pinned = pinned))
    }

    suspend fun moveNote(id: Long, folderId: Long?) {
        val n = dao.getNote(id) ?: return
        if (n.folderId != folderId) dao.upsertNote(n.copy(folderId = folderId))
    }

    /** Deletes the note, then its image files. */
    suspend fun deleteNote(id: Long) {
        val n = dao.getNote(id) ?: return
        dao.deleteNote(id)
        NoteBlocks.imageNames(NoteCodec.decode(n.content)).forEach { images.delete(it) }
    }

    /** Title blank, no text in any block (empty checklist items do not count) and no images. */
    suspend fun isBlank(title: String, doc: NoteDocument): Boolean =
        title.isBlank() &&
            NoteBlocks.imageNames(doc).isEmpty() &&
            doc.blocks.none { it.type == BlockType.IMAGE || it.text.isNotBlank() }

    suspend fun addFolder(name: String): Long {
        val n = requireName(name)
        val sort = (dao.maxFolderSort() ?: -1) + 1
        val rowId = dao.upsertFolder(NoteFolderEntity(name = n, sortOrder = sort))
        return rowId
    }

    suspend fun renameFolder(id: Long, name: String) {
        val n = requireName(name)
        val f = dao.observeFolders().first().firstOrNull { it.id == id } ?: return
        dao.upsertFolder(f.copy(name = n))
    }

    suspend fun deleteFolder(id: Long) {
        runner.run {
            dao.moveNotesOutOfFolder(id)
            dao.deleteFolder(id)
        }
    }

    suspend fun addRecord(title: String, target: Double, current: Double, now: Long): Long {
        val t = requireName(title)
        require(target > 0.0) { "target" }
        require(current >= 0.0) { "current" }
        val sort = (dao.maxRecordSort() ?: -1) + 1
        return dao.upsertRecord(RecordEntity(title = t, target = target, current = current, previous = null, sortOrder = sort, updatedAt = now))
    }

    suspend fun editRecord(id: Long, title: String, target: Double) {
        val t = requireName(title)
        require(target > 0.0) { "target" }
        val r = dao.getRecord(id) ?: return
        dao.upsertRecord(r.copy(title = t, target = target))
    }

    suspend fun updateRecordValue(id: Long, newValue: Double, now: Long) {
        require(newValue >= 0.0) { "current" }
        val r = dao.getRecord(id) ?: return
        if (r.current == newValue) return
        dao.upsertRecord(r.copy(previous = r.current, current = newValue, updatedAt = now))
    }

    suspend fun deleteRecord(id: Long) = dao.deleteRecord(id)

    private fun requireName(text: String): String {
        val t = text.trim()
        require(t.isNotEmpty()) { "name" }
        return t
    }
}
