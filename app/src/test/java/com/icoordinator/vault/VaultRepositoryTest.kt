package com.icoordinator.vault

import com.icoordinator.accounting.DirectTransactionRunner
import kotlinx.coroutines.Dispatchers
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
import javax.crypto.KeyGenerator

/** List holder whose flow emits on every assignment (MutableStateFlow would drop lists that are equal by entity id). */
class RowsFlow<T> {
    private class Box<T>(val list: List<T>)   // identity equality on purpose
    private val state = MutableStateFlow(Box<T>(emptyList()))
    var value: List<T>
        get() = state.value.list
        set(v) { state.value = Box(v) }
    val flow: Flow<List<T>> get() = state.map { it.list }
}

/** In-memory VaultDao. Like Room's @Upsert, returns the new row id on insert and -1 on update. */
class FakeVaultDao : VaultDao {
    val folders = RowsFlow<VaultFolderEntity>()
    val entries = RowsFlow<VaultEntryEntity>()
    private var nextId = 1L

    private fun newId(given: Long): Long = if (given != 0L) { if (given >= nextId) nextId = given + 1; given } else nextId++

    override fun observeFolders(): Flow<List<VaultFolderEntity>> = folders.flow.map { l -> l.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    override fun observeEntries(): Flow<List<VaultEntryEntity>> = entries.flow
    override suspend fun getEntry(id: Long): VaultEntryEntity? = entries.value.firstOrNull { it.id == id }
    override suspend fun upsertFolder(f: VaultFolderEntity): Long {
        val exists = folders.value.any { it.id == f.id && f.id != 0L }
        val e = f.copy(id = newId(f.id))
        folders.value = folders.value.filter { it.id != e.id } + e
        return if (exists) -1 else e.id
    }
    override suspend fun upsertEntry(e: VaultEntryEntity): Long {
        val exists = entries.value.any { it.id == e.id && e.id != 0L }
        val n = e.copy(id = newId(e.id))
        entries.value = entries.value.filter { it.id != n.id } + n
        return if (exists) -1 else n.id
    }
    override suspend fun deleteEntry(id: Long) { entries.value = entries.value.filter { it.id != id } }
    override suspend fun deleteFolder(id: Long) { folders.value = folders.value.filter { it.id != id } }
    override suspend fun moveEntriesOutOfFolder(folderId: Long) {
        entries.value = entries.value.map { if (it.folderId == folderId) it.copy(folderId = null) else it }
    }
    override suspend fun clearEntries() { entries.value = emptyList() }
    override suspend fun clearFolders() { folders.value = emptyList() }
    override suspend fun maxFolderSort(): Int? = folders.value.maxOfOrNull { it.sortOrder }
    override suspend fun countEntries(): Int = entries.value.size
}

class VaultRepositoryTest {
    private fun newCipher(): AesGcmCipher {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return AesGcmCipher(kg.generateKey())
    }

    private val cipher = newCipher()
    private val dao = FakeVaultDao()
    private val repo = VaultRepository(dao, DirectTransactionRunner, cipher, Dispatchers.Unconfined)

    private suspend fun snap() = repo.observeSnapshot().first()

    private fun contains(hay: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || hay.size < needle.size) return false
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    @Test fun saveThenSnapshotDecrypts() = runBlocking {
        repo.saveEntry(null, null, VaultPayload("بانک", "ali", "S3cret!"), 5L)
        val e = snap().entries.single()
        assertEquals("S3cret!", e.payload.password)
        assertEquals("بانک", e.payload.title)
        assertEquals(5L, e.createdAt)
        assertEquals(0, snap().unreadable)
    }

    @Test fun storedBlobHasNoPlaintext() = runBlocking {
        repo.saveEntry(null, null, VaultPayload("MyTitleXYZ", "user1", "S3cret!pw"), 5L)
        val blob = dao.entries.value.single().blob
        assertFalse(contains(blob, "S3cret!pw".toByteArray()))
        assertFalse(contains(blob, "MyTitleXYZ".toByteArray()))
        assertFalse(contains(blob, "user1".toByteArray()))
    }

    @Test fun folderNameStoredEncrypted() = runBlocking {
        repo.addFolder("FolderNameXYZ")
        assertFalse(contains(dao.folders.value.single().nameBlob, "FolderNameXYZ".toByteArray()))
        assertEquals("FolderNameXYZ", snap().folders.single().name)
    }

    @Test fun editKeepsCreatedAtAndId() = runBlocking {
        val id = repo.saveEntry(null, null, VaultPayload("a", password = "1"), 5L)
        val same = repo.saveEntry(id, null, VaultPayload("a2", password = "2"), 9L)
        assertEquals(id, same)
        val e = snap().entries.single()
        assertEquals(5L, e.createdAt)
        assertEquals(9L, e.updatedAt)
        assertEquals("2", e.payload.password)
        assertEquals("a2", e.payload.title)
    }

    @Test fun titleTrimmedOthersAsTyped() = runBlocking {
        repo.saveEntry(null, null, VaultPayload("  t  ", " u ", " p ", " url ", " n "), 1L)
        val p = snap().entries.single().payload
        assertEquals(VaultPayload("t", " u ", " p ", " url ", " n "), p)
    }

    @Test fun blankTitleThrows() {
        try {
            runBlocking { repo.saveEntry(null, null, VaultPayload("   "), 1L) }
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("title", e.message)
        }
        assertEquals(0, dao.entries.value.size)
    }

    @Test fun corruptedBlobCountedUnreadableAndSkipped() = runBlocking {
        repo.saveEntry(null, null, VaultPayload("good"), 1L)
        val badId = repo.saveEntry(null, null, VaultPayload("bad"), 2L)
        dao.entries.value = dao.entries.value.map {
            if (it.id == badId) it.copy(blob = it.blob.copyOf().also { b -> b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte() }) else it
        }
        val s = snap()
        assertEquals(listOf("good"), s.entries.map { it.payload.title })
        assertEquals(1, s.unreadable)
    }

    @Test fun unreadableFolderSkippedNotCounted() = runBlocking {
        repo.addFolder("ok")
        repo.addFolder("bad")
        dao.folders.value = dao.folders.value.map { if (it.sortOrder == 1) it.copy(nameBlob = ByteArray(5)) else it }
        val s = snap()
        assertEquals(listOf("ok"), s.folders.map { it.name })
        assertEquals(0, s.unreadable)
    }

    @Test fun folderAddRenameDeleteMovesEntriesOut() = runBlocking {
        val f = repo.addFolder("  بانک ")
        repo.renameFolder(f, "حساب‌ها")
        val e = repo.saveEntry(null, f, VaultPayload("x"), 1L)
        assertEquals("حساب‌ها", snap().folders.single().name)
        assertEquals(f, snap().entries.single().folderId)
        repo.deleteFolder(f)
        val s = snap()
        assertTrue(s.folders.isEmpty())
        assertNull(s.entries.single().folderId)
        assertEquals(e, s.entries.single().id)
    }

    @Test fun blankFolderNameThrows() {
        try {
            runBlocking { repo.addFolder("  ") }
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun entryPointingAtMissingFolderShownWithNullFolder() = runBlocking {
        val f = repo.addFolder("f")
        repo.saveEntry(null, f, VaultPayload("x"), 1L)
        dao.folders.value = emptyList()   // folder row vanished without the entry being moved
        assertNull(snap().entries.single().folderId)
    }

    @Test fun entriesSortedByTitleCaseInsensitive() = runBlocking {
        repo.saveEntry(null, null, VaultPayload("beta"), 1L)
        repo.saveEntry(null, null, VaultPayload("Alpha"), 1L)
        repo.saveEntry(null, null, VaultPayload("alpha"), 1L)
        repo.saveEntry(null, null, VaultPayload("Zed"), 1L)
        assertEquals(listOf("Alpha", "alpha", "beta", "Zed"), snap().entries.map { it.payload.title })
    }

    @Test fun exportRoundTripIntoSecondRepository() = runBlocking {
        val f = repo.addFolder("بانک")
        repo.saveEntry(null, f, VaultPayload("a", "u", "p", "url", "note"), 1L)
        repo.saveEntry(null, null, VaultPayload("b"), 1L)
        val data = repo.exportData()
        assertEquals(1, data.folders.size)
        assertEquals(2, data.entries.size)

        val dao2 = FakeVaultDao()
        val repo2 = VaultRepository(dao2, DirectTransactionRunner, newCipher(), Dispatchers.Unconfined)
        assertEquals(2, repo2.replaceAll(data, 50L))
        val s2 = repo2.observeSnapshot().first()
        assertEquals(listOf("a", "b"), s2.entries.map { it.payload.title })
        assertEquals("بانک", s2.folders.single().name)
        val a = s2.entries.first { it.payload.title == "a" }
        assertEquals(s2.folders.single().id, a.folderId)
        assertEquals(VaultPayload("a", "u", "p", "url", "note"), a.payload)
        assertNull(s2.entries.first { it.payload.title == "b" }.folderId)
        assertEquals(50L, a.createdAt)
    }

    @Test fun replaceAllClearsOldDataAndMapsFolderKeys() = runBlocking {
        val old = repo.addFolder("old")
        repo.saveEntry(null, old, VaultPayload("old entry"), 1L)
        val data = VaultBackupData(
            listOf(BackupFolder(7, "A"), BackupFolder(9, "B")),
            listOf(
                BackupEntry(9, VaultPayload("in-b")),
                BackupEntry(7, VaultPayload("in-a")),
                BackupEntry(null, VaultPayload("loose")),
                BackupEntry(42, VaultPayload("dangling key")),
            ),
        )
        assertEquals(4, repo.replaceAll(data, 10L))
        val s = snap()
        assertEquals(listOf("A", "B"), s.folders.map { it.name })
        assertEquals(4, s.entries.size)
        val idByName = s.folders.associate { it.name to it.id }
        assertEquals(idByName["B"], s.entries.first { it.payload.title == "in-b" }.folderId)
        assertEquals(idByName["A"], s.entries.first { it.payload.title == "in-a" }.folderId)
        assertNull(s.entries.first { it.payload.title == "loose" }.folderId)
        assertNull(s.entries.first { it.payload.title == "dangling key" }.folderId)
        assertTrue(s.entries.none { it.payload.title == "old entry" })
        assertEquals(listOf(0, 1), dao.folders.value.sortedBy { it.sortOrder }.map { it.sortOrder })
    }

    @Test fun countAndDelete() = runBlocking {
        assertEquals(0, repo.count())
        val id = repo.saveEntry(null, null, VaultPayload("a"), 1L)
        repo.saveEntry(null, null, VaultPayload("b"), 1L)
        assertEquals(2, repo.count())
        repo.deleteEntry(id)
        assertEquals(1, repo.count())
    }

    @Test fun exportSkipsUnreadableRows() = runBlocking {
        repo.saveEntry(null, null, VaultPayload("good"), 1L)
        repo.saveEntry(null, null, VaultPayload("bad"), 1L)
        dao.entries.value = dao.entries.value.mapIndexed { i, e -> if (i == 1) e.copy(blob = ByteArray(40)) else e }
        assertEquals(listOf("good"), repo.exportData().entries.map { it.payload.title })
    }
}
