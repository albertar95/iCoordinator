package com.icoordinator.vault

import androidx.room.withTransaction
import com.icoordinator.accounting.TransactionRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Arrays

data class VaultFolder(val id: Long, val name: String)
data class VaultEntry(val id: Long, val folderId: Long?, val payload: VaultPayload, val createdAt: Long, val updatedAt: Long)

/** unreadable = entry rows whose decrypt failed (skipped). */
data class VaultSnapshot(val folders: List<VaultFolder>, val entries: List<VaultEntry>, val unreadable: Int)

/**
 * Encrypts on write and decrypts on read; the database only ever holds encrypted blobs.
 * Decryption happens on [decryptDispatcher] and only in memory. Never logs or throws secrets.
 */
class VaultRepository(
    private val dao: VaultDao,
    private val runner: TransactionRunner,
    private val cipher: EntryCipher,
    private val decryptDispatcher: CoroutineDispatcher,
) {

    constructor(db: VaultDatabase, cipher: EntryCipher) : this(
        db.vaultDao(),
        object : TransactionRunner {
            override suspend fun <R> run(block: suspend () -> R): R = db.withTransaction { block() }
        },
        cipher,
        Dispatchers.Default,
    )

    fun observeSnapshot(): Flow<VaultSnapshot> =
        combine(dao.observeFolders(), dao.observeEntries()) { folders, entries -> decryptAll(folders, entries) }
            .flowOn(decryptDispatcher)

    private fun decryptFolders(rows: List<VaultFolderEntity>): List<VaultFolder> = rows
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .mapNotNull { row ->
            try {
                VaultFolder(row.id, VaultCodec.decodeName(cipher.decrypt(row.nameBlob)))
            } catch (e: VaultCryptoException) {
                null
            }
        }

    private fun decryptAll(folderRows: List<VaultFolderEntity>, entryRows: List<VaultEntryEntity>): VaultSnapshot {
        val folders = decryptFolders(folderRows)
        val folderIds = folders.mapTo(HashSet()) { it.id }
        var unreadable = 0
        val entries = ArrayList<VaultEntry>()
        for (row in entryRows) {
            val payload = decryptPayload(row.blob)
            if (payload == null) { unreadable++; continue }
            val fid = row.folderId?.takeIf { it in folderIds }
            entries.add(VaultEntry(row.id, fid, payload, row.createdAt, row.updatedAt))
        }
        entries.sortWith(compareBy({ it.payload.title.lowercase() }, { it.id }))
        return VaultSnapshot(folders, entries, unreadable)
    }

    private fun decryptPayload(blob: ByteArray): VaultPayload? {
        var plain: ByteArray? = null
        return try {
            plain = cipher.decrypt(blob)
            VaultCodec.decodePayload(plain)
        } catch (e: VaultCryptoException) {
            null
        } finally {
            plain?.let { Arrays.fill(it, 0) }
        }
    }

    private fun encryptPayload(payload: VaultPayload): ByteArray {
        val plain = VaultCodec.encodePayload(payload)
        try {
            return cipher.encrypt(plain)
        } finally {
            Arrays.fill(plain, 0)
        }
    }

    private fun encryptName(name: String): ByteArray {
        val plain = VaultCodec.encodeName(name)
        try {
            return cipher.encrypt(plain)
        } finally {
            Arrays.fill(plain, 0)
        }
    }

    /** Trims the title (blank -> IllegalArgumentException("title")); keeps createdAt on edit. Returns the entry id. */
    suspend fun saveEntry(id: Long?, folderId: Long?, payload: VaultPayload, now: Long): Long {
        val title = payload.title.trim()
        require(title.isNotEmpty()) { "title" }
        val blob = withContext(decryptDispatcher) { encryptPayload(payload.copy(title = title)) }
        val realId = if (id != null && id != 0L) id else 0L
        val existing = if (realId != 0L) dao.getEntry(realId) else null
        val rowId = dao.upsertEntry(
            VaultEntryEntity(
                id = realId,
                folderId = folderId,
                blob = blob,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
        // Room's @Upsert returns -1 when it updated an existing row
        return if (realId != 0L) realId else rowId
    }

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    suspend fun addFolder(name: String): Long {
        val n = requireName(name)
        val blob = withContext(decryptDispatcher) { encryptName(n) }
        val sort = (dao.maxFolderSort() ?: -1) + 1
        return dao.upsertFolder(VaultFolderEntity(nameBlob = blob, sortOrder = sort))
    }

    suspend fun renameFolder(id: Long, name: String) {
        val n = requireName(name)
        val row = dao.observeFolders().first().firstOrNull { it.id == id } ?: return
        val blob = withContext(decryptDispatcher) { encryptName(n) }
        dao.upsertFolder(row.copy(nameBlob = blob))
    }

    suspend fun deleteFolder(id: Long) {
        runner.run {
            dao.moveEntriesOutOfFolder(id)
            dao.deleteFolder(id)
        }
    }

    /** Decrypts everything for the backup file; unreadable rows are skipped. Folder keys are 1-based positions. */
    suspend fun exportData(): VaultBackupData {
        val folderRows = dao.observeFolders().first()
        val entryRows = dao.observeEntries().first()
        return withContext(decryptDispatcher) {
            val folders = decryptFolders(folderRows)
            val keyById = HashMap<Long, Int>()
            folders.forEachIndexed { i, f -> keyById[f.id] = i + 1 }
            val entries = ArrayList<BackupEntry>()
            for (row in entryRows.sortedBy { it.id }) {
                val payload = decryptPayload(row.blob) ?: continue
                entries.add(BackupEntry(row.folderId?.let { keyById[it] }, payload))
            }
            VaultBackupData(folders.mapIndexed { i, f -> BackupFolder(i + 1, f.name) }, entries)
        }
    }

    /** One transaction: clear, insert folders (sortOrder = index) then entries (folderKey mapped). Returns the entry count. */
    suspend fun replaceAll(data: VaultBackupData, now: Long): Int {
        val folderBlobs = withContext(decryptDispatcher) { data.folders.map { encryptName(it.name) } }
        val entryBlobs = withContext(decryptDispatcher) { data.entries.map { encryptPayload(it.payload) } }
        runner.run {
            dao.clearEntries()
            dao.clearFolders()
            val idByKey = HashMap<Int, Long>()
            data.folders.forEachIndexed { index, f ->
                idByKey[f.key] = dao.upsertFolder(VaultFolderEntity(nameBlob = folderBlobs[index], sortOrder = index))
            }
            data.entries.forEachIndexed { index, e ->
                dao.upsertEntry(
                    VaultEntryEntity(
                        folderId = e.folderKey?.let { idByKey[it] },
                        blob = entryBlobs[index],
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        return data.entries.size
    }

    suspend fun count(): Int = dao.countEntries()

    private fun requireName(text: String): String {
        val t = text.trim()
        require(t.isNotEmpty()) { "name" }
        return t
    }
}
