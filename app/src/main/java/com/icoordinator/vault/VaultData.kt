package com.icoordinator.vault

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** nameBlob = EntryCipher.encrypt(VaultCodec.encodeName(name)). Equality by id only. */
@Entity(tableName = "vault_folders")
data class VaultFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameBlob: ByteArray,
    val sortOrder: Int = 0,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is VaultFolderEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "VaultFolderEntity(id=$id)"
}

/** blob = EntryCipher.encrypt(VaultCodec.encodePayload(payload)). Equality by id only. */
@Entity(tableName = "vault_entries", indices = [Index("folderId")])
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val blob: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is VaultEntryEntity && other.id == id)
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "VaultEntryEntity(id=$id)"
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_folders ORDER BY sortOrder, id") fun observeFolders(): Flow<List<VaultFolderEntity>>
    @Query("SELECT * FROM vault_entries ORDER BY id") fun observeEntries(): Flow<List<VaultEntryEntity>>
    @Query("SELECT * FROM vault_entries WHERE id = :id") suspend fun getEntry(id: Long): VaultEntryEntity?
    @Upsert suspend fun upsertFolder(f: VaultFolderEntity): Long
    @Upsert suspend fun upsertEntry(e: VaultEntryEntity): Long
    @Query("DELETE FROM vault_entries WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("DELETE FROM vault_folders WHERE id = :id") suspend fun deleteFolder(id: Long)
    @Query("UPDATE vault_entries SET folderId = NULL WHERE folderId = :folderId") suspend fun moveEntriesOutOfFolder(folderId: Long)
    @Query("DELETE FROM vault_entries") suspend fun clearEntries()
    @Query("DELETE FROM vault_folders") suspend fun clearFolders()
    @Query("SELECT MAX(sortOrder) FROM vault_folders") suspend fun maxFolderSort(): Int?
    @Query("SELECT COUNT(*) FROM vault_entries") suspend fun countEntries(): Int
}

@Database(
    entities = [VaultFolderEntity::class, VaultEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
    companion object { const val DB_NAME = "vault.db" }
}
