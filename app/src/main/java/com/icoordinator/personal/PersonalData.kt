package com.icoordinator.personal

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "note_folders")
data class NoteFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

/** content = NoteCodec JSON; plainText = NoteText.plainText(doc) for search. */
@Entity(tableName = "notes", indices = [Index("folderId")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long? = null,
    val title: String = "",
    val content: String = "",
    val plainText: String = "",
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val target: Double,
    val current: Double,
    val previous: Double? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long,
)

@Dao
interface PersonalDao {
    @Query("SELECT * FROM note_folders ORDER BY sortOrder, id") fun observeFolders(): Flow<List<NoteFolderEntity>>
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC") fun observeNotes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun getNote(id: Long): NoteEntity?
    @Upsert suspend fun upsertNote(n: NoteEntity): Long
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: Long)

    @Upsert suspend fun upsertFolder(f: NoteFolderEntity): Long
    @Query("DELETE FROM note_folders WHERE id = :id") suspend fun deleteFolder(id: Long)
    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId") suspend fun moveNotesOutOfFolder(folderId: Long)

    @Query("SELECT * FROM records ORDER BY sortOrder, id") fun observeRecords(): Flow<List<RecordEntity>>
    @Query("SELECT * FROM records WHERE id = :id") suspend fun getRecord(id: Long): RecordEntity?
    @Upsert suspend fun upsertRecord(r: RecordEntity): Long
    @Query("DELETE FROM records WHERE id = :id") suspend fun deleteRecord(id: Long)

    @Query("SELECT MAX(sortOrder) FROM records") suspend fun maxRecordSort(): Int?
    @Query("SELECT MAX(sortOrder) FROM note_folders") suspend fun maxFolderSort(): Int?
    @Query("SELECT COUNT(*) FROM notes") suspend fun countNotes(): Int
    @Query("SELECT COUNT(*) FROM records") suspend fun countRecords(): Int
}

@Database(
    entities = [NoteFolderEntity::class, NoteEntity::class, RecordEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PersonalDatabase : RoomDatabase() {
    abstract fun personalDao(): PersonalDao
    companion object { const val DB_NAME = "personal.db" }
}
