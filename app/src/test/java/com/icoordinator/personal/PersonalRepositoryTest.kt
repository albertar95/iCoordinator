package com.icoordinator.personal

import com.icoordinator.accounting.DirectTransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** In-memory PersonalDao. Like Room's @Upsert, returns the new row id on insert and -1 on update. */
class FakePersonalDao : PersonalDao {
    val folders = MutableStateFlow<List<NoteFolderEntity>>(emptyList())
    val notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val records = MutableStateFlow<List<RecordEntity>>(emptyList())
    private var nextId = 1L

    private fun newId(given: Long): Long = if (given != 0L) { if (given >= nextId) nextId = given + 1; given } else nextId++

    override fun observeFolders(): Flow<List<NoteFolderEntity>> = folders.map { l -> l.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    override fun observeNotes(): Flow<List<NoteEntity>> =
        notes.map { l -> l.sortedWith(compareByDescending<NoteEntity> { it.pinned }.thenByDescending { it.updatedAt }) }
    override suspend fun getNote(id: Long): NoteEntity? = notes.value.firstOrNull { it.id == id }
    override suspend fun upsertNote(n: NoteEntity): Long {
        val exists = notes.value.any { it.id == n.id && n.id != 0L }
        val e = n.copy(id = newId(n.id))
        notes.value = notes.value.filter { it.id != e.id } + e
        return if (exists) -1 else e.id
    }
    override suspend fun deleteNote(id: Long) { notes.value = notes.value.filter { it.id != id } }

    override suspend fun upsertFolder(f: NoteFolderEntity): Long {
        val exists = folders.value.any { it.id == f.id && f.id != 0L }
        val e = f.copy(id = newId(f.id))
        folders.value = folders.value.filter { it.id != e.id } + e
        return if (exists) -1 else e.id
    }
    override suspend fun deleteFolder(id: Long) { folders.value = folders.value.filter { it.id != id } }
    override suspend fun moveNotesOutOfFolder(folderId: Long) {
        notes.value = notes.value.map { if (it.folderId == folderId) it.copy(folderId = null) else it }
    }

    override fun observeRecords(): Flow<List<RecordEntity>> = records.map { l -> l.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    override suspend fun getRecord(id: Long): RecordEntity? = records.value.firstOrNull { it.id == id }
    override suspend fun upsertRecord(r: RecordEntity): Long {
        val exists = records.value.any { it.id == r.id && r.id != 0L }
        val e = r.copy(id = newId(r.id))
        records.value = records.value.filter { it.id != e.id } + e
        return if (exists) -1 else e.id
    }
    override suspend fun deleteRecord(id: Long) { records.value = records.value.filter { it.id != id } }

    override suspend fun maxRecordSort(): Int? = records.value.maxOfOrNull { it.sortOrder }
    override suspend fun maxFolderSort(): Int? = folders.value.maxOfOrNull { it.sortOrder }
    override suspend fun countNotes(): Int = notes.value.size
    override suspend fun countRecords(): Int = records.value.size
}

class FakeNoteImageStore : NoteImageStore {
    val deleted = mutableListOf<String>()
    override fun delete(name: String) { deleted.add(name) }
}

class PersonalRepositoryTest {
    private val dao = FakePersonalDao()
    private val images = FakeNoteImageStore()
    private val repo = PersonalRepository(dao, DirectTransactionRunner, images)

    private fun para(text: String, id: Long = 1) = NoteBlock(id, BlockType.PARAGRAPH, text)
    private fun img(name: String, id: Long) = NoteBlock(id, BlockType.IMAGE, imageName = name)
    private fun doc(vararg b: NoteBlock) = NoteDocument(b.toList())

    private fun expectIllegal(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun newNoteSetsTimestampsAndPlainText() = runBlocking {
        val id = repo.saveNote(null, null, "  عنوان  ", doc(para("متن"), NoteBlock(2, BlockType.CHECK, "کار", checked = true)), 100L)
        val n = repo.getNote(id)!!
        assertEquals("عنوان", n.title)
        assertEquals(100L, n.createdAt)
        assertEquals(100L, n.updatedAt)
        assertEquals("متن\n☑ کار", n.plainText)
        assertFalse(n.pinned)
        assertEquals(NoteCodec.encode(doc(para("متن"), NoteBlock(2, BlockType.CHECK, "کار", checked = true))), n.content)
    }

    @Test fun editKeepsCreatedAtAndPinned() = runBlocking {
        val id = repo.saveNote(null, null, "a", doc(para("x")), 100L)
        repo.setPinned(id, true)
        val same = repo.saveNote(id, 5L, "b", doc(para("y")), 200L)
        assertEquals(id, same)
        val n = repo.getNote(id)!!
        assertEquals(100L, n.createdAt)
        assertEquals(200L, n.updatedAt)
        assertTrue(n.pinned)
        assertEquals("b", n.title)
        assertEquals(5L, n.folderId)
        assertEquals("y", n.plainText)
        assertEquals(1, dao.notes.value.size)
    }

    @Test fun removedImagesDeletedAndOnlyThose() = runBlocking {
        val id = repo.saveNote(null, null, "", doc(para("t"), img("a.jpg", 2), img("b.jpg", 3)), 1L)
        assertTrue(images.deleted.isEmpty())
        repo.saveNote(id, null, "", doc(para("t"), img("b.jpg", 3), img("c.jpg", 4)), 2L)
        assertEquals(listOf("a.jpg"), images.deleted)
    }

    @Test fun deleteNoteDeletesItsImages() = runBlocking {
        val id = repo.saveNote(null, null, "t", doc(img("a.jpg", 1), img("b.jpg", 2)), 1L)
        repo.deleteNote(id)
        assertNull(repo.getNote(id))
        assertEquals(setOf("a.jpg", "b.jpg"), images.deleted.toSet())
    }

    @Test fun deleteMissingNoteIsNoop() = runBlocking {
        repo.deleteNote(999L)
        assertTrue(images.deleted.isEmpty())
    }

    @Test fun blankDetection() = runBlocking {
        assertTrue(repo.isBlank("  ", NoteDocument.empty()))
        assertTrue(repo.isBlank("", doc(NoteBlock(1, BlockType.CHECK, ""))))
        assertFalse(repo.isBlank("عنوان", NoteDocument.empty()))
        assertFalse(repo.isBlank("", doc(para("x"))))
        assertFalse(repo.isBlank("", doc(para(""), img("a.jpg", 2))))
    }

    @Test fun pinAndMoveNote() = runBlocking {
        val folder = repo.addFolder("کار")
        val id = repo.saveNote(null, null, "a", doc(para("x")), 1L)
        repo.setPinned(id, true)
        repo.moveNote(id, folder)
        val n = repo.getNote(id)!!
        assertTrue(n.pinned)
        assertEquals(folder, n.folderId)
        assertEquals(1L, n.updatedAt)
        repo.moveNote(id, null)
        assertNull(repo.getNote(id)!!.folderId)
    }

    @Test fun pinnedNotesObservedFirst() = runBlocking {
        val a = repo.saveNote(null, null, "a", doc(para("x")), 1L)
        repo.saveNote(null, null, "b", doc(para("x")), 2L)
        repo.setPinned(a, true)
        assertEquals(a, repo.observeNotes().first().first().id)
    }

    @Test fun folderAddRenameTrimmedAndSorted() = runBlocking {
        val f1 = repo.addFolder("  اول ")
        val f2 = repo.addFolder("دوم")
        repo.renameFolder(f1, " نخست ")
        val list = repo.observeFolders().first()
        assertEquals(listOf("نخست", "دوم"), list.map { it.name })
        assertEquals(listOf(0, 1), list.map { it.sortOrder })
        assertEquals(listOf(f1, f2), list.map { it.id })
    }

    @Test fun deleteFolderMovesNotesOut() = runBlocking {
        val f = repo.addFolder("پوشه")
        val n = repo.saveNote(null, f, "a", doc(para("x")), 1L)
        repo.deleteFolder(f)
        assertTrue(dao.folders.value.isEmpty())
        assertNull(repo.getNote(n)!!.folderId)
        assertEquals(1, dao.notes.value.size)
    }

    @Test fun blankFolderNameThrows() {
        expectIllegal { repo.addFolder("   ") }
        expectIllegal {
            val f = repo.addFolder("x")
            repo.renameFolder(f, "")
        }
    }

    @Test fun recordAddSortOrderAndTrim() = runBlocking {
        val a = repo.addRecord(" وزنه ", 100.0, 60.0, 1L)
        val b = repo.addRecord("دو", 10.0, 0.0, 1L)
        val list = repo.observeRecords().first()
        assertEquals(listOf(a, b), list.map { it.id })
        assertEquals(listOf(0, 1), list.map { it.sortOrder })
        assertEquals("وزنه", list[0].title)
        assertNull(list[0].previous)
    }

    @Test fun editRecordKeepsValues() = runBlocking {
        val id = repo.addRecord("a", 100.0, 60.0, 1L)
        repo.updateRecordValue(id, 70.0, 2L)
        repo.editRecord(id, "نام نو", 200.0)
        val r = dao.getRecord(id)!!
        assertEquals("نام نو", r.title)
        assertEquals(200.0, r.target, 0.0)
        assertEquals(70.0, r.current, 0.0)
        assertEquals(60.0, r.previous!!, 0.0)
        assertEquals(2L, r.updatedAt)
    }

    @Test fun updateRecordValueSetsPrevious() = runBlocking {
        val id = repo.addRecord("وزنه", 100.0, 60.0, 1L)
        repo.updateRecordValue(id, 70.0, 2L)
        val r = dao.getRecord(id)!!
        assertEquals(70.0, r.current, 0.0)
        assertEquals(60.0, r.previous!!, 0.0)
        assertEquals(2L, r.updatedAt)
    }

    @Test fun equalValueIsNoop() = runBlocking {
        val id = repo.addRecord("a", 100.0, 60.0, 1L)
        repo.updateRecordValue(id, 70.0, 2L)
        repo.updateRecordValue(id, 70.0, 3L)
        val r = dao.getRecord(id)!!
        assertEquals(60.0, r.previous!!, 0.0)
        assertEquals(2L, r.updatedAt)
    }

    @Test fun deleteRecord() = runBlocking {
        val id = repo.addRecord("a", 10.0, 1.0, 1L)
        repo.deleteRecord(id)
        assertEquals(0, dao.countRecords())
    }

    @Test fun invalidRecordInputsThrow() {
        expectIllegal { repo.addRecord("  ", 10.0, 1.0, 1L) }
        expectIllegal { repo.addRecord("a", 0.0, 1.0, 1L) }
        expectIllegal { repo.addRecord("a", -5.0, 1.0, 1L) }
        expectIllegal { repo.addRecord("a", 10.0, -1.0, 1L) }
        expectIllegal {
            val id = repo.addRecord("a", 10.0, 1.0, 1L)
            repo.editRecord(id, "", 10.0)
        }
        expectIllegal {
            val id = repo.addRecord("a", 10.0, 1.0, 1L)
            repo.editRecord(id, "a", 0.0)
        }
        expectIllegal {
            val id = repo.addRecord("a", 10.0, 1.0, 1L)
            repo.updateRecordValue(id, -2.0, 2L)
        }
    }
}
