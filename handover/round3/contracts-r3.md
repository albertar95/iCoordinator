# Contracts - round 3 (Notes, Records, Vault). Locked interfaces; every task must match these exactly.
Project root (work copy): /home/claude/work/proj . Kotlin source root: app/src/main/java/com/icoordinator . Tests: app/src/test/java/com/icoordinator .
All user-visible text is Persian. Digits shown to the user go through `com.icoordinator.accounting.AccountingFormat.digits(text)` (Latin -> Persian digits).

## Package com.icoordinator.personal  (notes + records, database personal.db)

### NoteDocument.kt (pure Kotlin, no android imports) - task J1
```kotlin
enum class BlockType(val code: String) { PARAGRAPH("p"), HEADING("h"), BULLET("b"), NUMBERED("n"), CHECK("c"), IMAGE("i");
  companion object { fun fromCode(code: String): BlockType /* unknown -> PARAGRAPH */ } }
enum class SpanKind(val code: String) { BOLD("b"), ITALIC("i"), UNDERLINE("u"), HIGHLIGHT("h"), COLOR("c"), SIZE("s");
  companion object { fun fromCode(code: String): SpanKind? } }
/** [start, end) character range of NoteBlock.text. value: palette index for HIGHLIGHT (0..3) and COLOR (0..5); size step for SIZE (-1 small, 1 large, 2 huge); 0 for the others. */
data class TextSpan(val start: Int, val end: Int, val kind: SpanKind, val value: Int = 0)
/** imageName: file name inside filesDir/note_images (IMAGE blocks only, text empty). */
data class NoteBlock(val id: Long, val type: BlockType, val text: String = "", val spans: List<TextSpan> = emptyList(), val checked: Boolean = false, val imageName: String? = null)
data class NoteDocument(val blocks: List<NoteBlock>) { companion object { fun empty(): NoteDocument /* one empty PARAGRAPH, id 1 */ } }

object NoteCodec {
  fun encode(doc: NoteDocument): String              // compact JSON, hand-written (no org.json / kotlinx), version field "v":1
  fun decode(text: String?): NoteDocument            // never throws; null/blank -> empty(); not our JSON -> one PARAGRAPH with the raw text
}
object SpanOps {
  fun normalize(spans: List<TextSpan>, textLength: Int): List<TextSpan>   // clamp to [0,len], drop empty, merge touching/overlapping spans of same kind+value, sorted by (start, kind.ordinal)
  fun applyEdit(oldText: String, newText: String, spans: List<TextSpan>): List<TextSpan> // keep styles across a typing edit (common prefix/suffix diff; text inserted at the end of a span extends it; deleted ranges shrink spans) then normalize
  fun isActive(spans: List<TextSpan>, start: Int, end: Int, kind: SpanKind): Boolean   // collapsed range (start==end): true if a span of kind covers position start-1 (or start when start==0); else every char in range covered
  fun valueAt(spans: List<TextSpan>, start: Int, end: Int, kind: SpanKind): Int?        // value of kind covering the whole range, null if none/mixed
  fun toggle(spans: List<TextSpan>, textLength: Int, start: Int, end: Int, kind: SpanKind): List<TextSpan>   // BOLD/ITALIC/UNDERLINE: remove kind from range if isActive else add; empty range -> unchanged
  fun setValue(spans: List<TextSpan>, textLength: Int, start: Int, end: Int, kind: SpanKind, value: Int?): List<TextSpan> // HIGHLIGHT/COLOR/SIZE: clear kind in range, then add with value unless null
}
object NoteBlocks {
  fun nextId(doc: NoteDocument): Long                                   // max id + 1 (1 when empty)
  fun split(block: NoteBlock, cursor: Int, newId: Long): Pair<NoteBlock, NoteBlock> // Enter: second keeps type (HEADING -> PARAGRAPH, CHECK -> unchecked CHECK), spans split and shifted
  fun mergeIntoPrevious(previous: NoteBlock, block: NoteBlock): NoteBlock // Backspace at start: previous.text + block.text, spans shifted; IMAGE previous -> not merged (caller deletes image block instead) - function returns previous unchanged
  fun numberFor(blocks: List<NoteBlock>, index: Int): Int               // 1-based position inside the consecutive NUMBERED run containing index; 0 if not NUMBERED
  fun imageNames(doc: NoteDocument): Set<String>
}
object NoteText {
  fun plainText(doc: NoteDocument): String         // block texts joined with '\n'; CHECK prefixed "☐ " / "☑ "; images skipped
  fun preview(doc: NoteDocument, maxChars: Int = 120): String // plainText with newlines -> spaces, trimmed, cut with "…"
  fun matches(title: String, plain: String, query: String): Boolean // case-insensitive contains on title or plain; Arabic ي/ك normalized to ی/ک; blank query -> true
}
```

### RecordMath.kt (pure) - task J2
```kotlin
object RecordMath {
  fun percent(current: Double, target: Double): Int        // floor(current/target*100) clamped 0..100; target <= 0 or NaN -> 0
  fun parse(text: String): Double?                         // accepts Persian/Arabic/Latin digits, '٫' '.' '/' as decimal point, ',' '٬' thousands; trims; empty/invalid/NaN/inf -> null
  fun format(value: Double): String                        // Persian digits, no trailing ".0", up to 2 decimals (rounded half up), thousands separator '٬'; negative with leading '-'
  fun validateTarget(text: String): String?                // null = ok; else Persian error: empty -> "مقدار هدف را وارد کن", invalid -> "عدد معتبر نیست", <= 0 -> "هدف باید بیشتر از صفر باشد"
  fun validateCurrent(text: String): String?               // empty -> "مقدار فعلی را وارد کن", invalid -> "عدد معتبر نیست", < 0 -> "مقدار نمی‌تواند منفی باشد"
}
```

### PersonalData.kt (Room) - task J4
```kotlin
@Entity(tableName = "note_folders") data class NoteFolderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val sortOrder: Int = 0)
@Entity(tableName = "notes", indices = [Index("folderId")]) data class NoteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val folderId: Long? = null, val title: String = "", val content: String = "", val plainText: String = "", val pinned: Boolean = false, val createdAt: Long, val updatedAt: Long)
@Entity(tableName = "records") data class RecordEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val target: Double, val current: Double, val previous: Double? = null, val sortOrder: Int = 0, val updatedAt: Long)
@Dao interface PersonalDao { observeFolders(): Flow<List<NoteFolderEntity>> (ORDER BY sortOrder, id); observeNotes(): Flow<List<NoteEntity>> (ORDER BY pinned DESC, updatedAt DESC); suspend getNote(id: Long): NoteEntity?; suspend upsertNote(n: NoteEntity): Long; suspend deleteNote(id: Long); suspend upsertFolder(f): Long; suspend deleteFolder(id: Long); suspend moveNotesOutOfFolder(folderId: Long) (UPDATE notes SET folderId = NULL WHERE folderId = :folderId); observeRecords(): Flow<List<RecordEntity>> (ORDER BY sortOrder, id); suspend getRecord(id): RecordEntity?; suspend upsertRecord(r): Long; suspend deleteRecord(id: Long); suspend maxRecordSort(): Int?; suspend maxFolderSort(): Int?; suspend countNotes(): Int; suspend countRecords(): Int }
@Database(entities = [NoteFolderEntity::class, NoteEntity::class, RecordEntity::class], version = 1, exportSchema = false)
abstract class PersonalDatabase : RoomDatabase() { abstract fun personalDao(): PersonalDao; companion object { const val DB_NAME = "personal.db" } }
```
(no foreign keys: folder delete = moveNotesOutOfFolder + deleteFolder in one transaction)

### PersonalRepository.kt - task J4
```kotlin
class PersonalRepository internal constructor(private val dao: PersonalDao, private val runner: com.icoordinator.accounting.TransactionRunner, private val images: NoteImageStore) {
  constructor(db: PersonalDatabase, images: NoteImageStore)
  fun observeFolders(): Flow<List<NoteFolderEntity>>; fun observeNotes(): Flow<List<NoteEntity>>; fun observeRecords(): Flow<List<RecordEntity>>
  suspend fun getNote(id: Long): NoteEntity?
  suspend fun saveNote(id: Long?, folderId: Long?, title: String, doc: NoteDocument, now: Long): Long   // new when id null/0; keeps createdAt & pinned; plainText = NoteText.plainText(doc); deletes image files that were in the old doc and are gone from the new doc
  suspend fun setPinned(id: Long, pinned: Boolean); suspend fun moveNote(id: Long, folderId: Long?)
  suspend fun deleteNote(id: Long)                         // also deletes its image files
  suspend fun isBlank(title: String, doc: NoteDocument): Boolean  // title blank and plainText blank and no images (editor does not save blank new notes)
  suspend fun addFolder(name: String): Long; suspend fun renameFolder(id: Long, name: String); suspend fun deleteFolder(id: Long)  // name trimmed, blank -> IllegalArgumentException
  suspend fun addRecord(title: String, target: Double, current: Double, now: Long): Long   // sortOrder = max+1; previous null
  suspend fun editRecord(id: Long, title: String, target: Double)                          // current/previous unchanged
  suspend fun updateRecordValue(id: Long, newValue: Double, now: Long)                      // previous = old current, current = newValue (no-op if equal)
  suspend fun deleteRecord(id: Long)
}
/** Image files of notes (filesDir/note_images in the app; a temp dir in tests). */
interface NoteImageStore { fun delete(name: String) }
```

### PersonalBackup.kt (android) - task J5
```kotlin
class PersonalBackup(private val app: ICoordinatorApp) {
  var needsRestart: Boolean (private set)
  fun suggestedFileName(now: LocalDateTime = LocalDateTime.now()): String   // "iCoordinator-notes-yyyyMMdd-HHmm.zip"
  suspend fun export(target: Uri): String?    // null = ok; zip entries: "personal.db" (after PRAGMA wal_checkpoint(FULL)) + "note_images/<name>"
  suspend fun restore(source: Uri): String?   // validate zip (has personal.db, SQLite header, tables notes/note_folders/records, PRAGMA user_version == 1, entry names safe: no "..", no '/' prefix, only note_images/<file>) BEFORE touching live data; then close DB, replace db (+ delete -wal/-shm), replace note_images dir; caller then calls BackupManager.restartApp(activity)
}
```
App-side image store (J5, same file): `class FileNoteImageStore(private val dir: java.io.File) : NoteImageStore` and `object NoteImageFiles { const val DIR = "note_images"; fun dir(context: Context): File }`.

## Package com.icoordinator.vault  (password manager, database vault.db)

### VaultCrypto.kt (pure JVM: javax.crypto only, no android imports) - task J3
```kotlin
data class VaultPayload(val title: String, val username: String = "", val password: String = "", val url: String = "", val note: String = "")
interface EntryCipher { fun encrypt(plain: ByteArray): ByteArray; fun decrypt(blob: ByteArray): ByteArray }  // decrypt throws VaultCryptoException on any failure
class VaultCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
/** AES-256-GCM, blob = [1 byte version=1][12 byte random IV][ciphertext+16 byte tag]. */
class AesGcmCipher(private val key: javax.crypto.SecretKey) : EntryCipher
object VaultCodec {
  fun encodePayload(p: VaultPayload): ByteArray       // UTF-8, format: "v1" then each field as <len>:<chars> (len = UTF-16 length), see tests
  fun decodePayload(bytes: ByteArray): VaultPayload    // throws VaultCryptoException on malformed input
  fun encodeName(name: String): ByteArray; fun decodeName(bytes: ByteArray): String
}
data class BackupFolder(val key: Int, val name: String)
data class BackupEntry(val folderKey: Int?, val payload: VaultPayload)
data class VaultBackupData(val folders: List<BackupFolder>, val entries: List<BackupEntry>)
sealed class BackupReadResult { data class Ok(val data: VaultBackupData) : BackupReadResult(); object WrongPassword : BackupReadResult(); object NotAVaultFile : BackupReadResult(); object Corrupt : BackupReadResult() }
object VaultBackupFormat {
  const val MAGIC = "ICVAULT1"; const val ITERATIONS = 600_000; const val MIN_PASSWORD = 8
  fun write(data: VaultBackupData, password: CharArray, random: java.security.SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): ByteArray
     // bytes = MAGIC (ASCII 8) + iterations (4 byte big-endian int) + salt (16) + iv (12) + AES-GCM(ciphertext of body), key = PBKDF2WithHmacSHA256(password, salt, iterations, 256); header bytes (magic..iv) are GCM AAD; body = hand-written serialization of data (reuse VaultCodec field style)
  fun read(bytes: ByteArray, password: CharArray): BackupReadResult   // wrong magic/short -> NotAVaultFile; iterations outside 10_000..5_000_000 -> Corrupt; tag failure -> WrongPassword; body parse failure -> Corrupt. Never throws.
  fun passwordProblem(password: CharArray, repeat: CharArray): String?  // < MIN_PASSWORD -> "رمز اصلی باید حداقل ۸ نویسه باشد"; mismatch -> "تکرار رمز اصلی یکسان نیست"; else null
}
```

### VaultData.kt (Room) + VaultKeyStore.kt (android) + VaultRepository.kt - task J4b
```kotlin
@Entity(tableName = "vault_folders") data class VaultFolderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val nameBlob: ByteArray, val sortOrder: Int = 0)  // equals/hashCode by id
@Entity(tableName = "vault_entries", indices = [Index("folderId")]) data class VaultEntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val folderId: Long? = null, val blob: ByteArray, val createdAt: Long, val updatedAt: Long) // equals/hashCode by id
@Dao interface VaultDao { observeFolders(): Flow<List<VaultFolderEntity>>; observeEntries(): Flow<List<VaultEntryEntity>>; suspend getEntry(id): VaultEntryEntity?; suspend upsertFolder(f): Long; suspend upsertEntry(e): Long; suspend deleteEntry(id: Long); suspend deleteFolder(id: Long); suspend moveEntriesOutOfFolder(folderId: Long); suspend clearEntries(); suspend clearFolders(); suspend maxFolderSort(): Int?; suspend countEntries(): Int }
@Database(entities = [VaultFolderEntity::class, VaultEntryEntity::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() { abstract fun vaultDao(): VaultDao; companion object { const val DB_NAME = "vault.db" } }

object VaultKeyStore { const val ALIAS = "icoordinator_vault_v1"; fun cipher(): EntryCipher }  // the key is fetched/created lazily inside encrypt/decrypt, not in cipher()  // AndroidKeyStore AES-256 GCM key, created on first use, PURPOSE_ENCRYPT|DECRYPT, BLOCK_MODE_GCM, ENCRYPTION_PADDING_NONE, randomized encryption required, no user-auth binding; IV generated by the Cipher (Keystore forbids caller IV) -> AesGcmCipher cannot be reused for the keystore key, so VaultKeyStore returns its own EntryCipher writing the same blob layout [1][iv 12][ct]

data class VaultFolder(val id: Long, val name: String)
data class VaultEntry(val id: Long, val folderId: Long?, val payload: VaultPayload, val createdAt: Long, val updatedAt: Long)
data class VaultSnapshot(val folders: List<VaultFolder>, val entries: List<VaultEntry>, val unreadable: Int)  // unreadable = rows whose decrypt failed (skipped)
class VaultRepository(private val dao: VaultDao, private val runner: com.icoordinator.accounting.TransactionRunner, private val cipher: EntryCipher, private val decryptDispatcher: kotlinx.coroutines.CoroutineDispatcher) {
  constructor(db: VaultDatabase, cipher: EntryCipher)    // Room withTransaction runner + Dispatchers.Default. The app passes VaultKeyStore.cipher(); VaultRepository.kt itself must NOT reference VaultKeyStore or any android.* class (it is compiled and unit-tested on the JVM)
  fun observeSnapshot(): Flow<VaultSnapshot>        // combine folders+entries, decrypt on decryptDispatcher; folders sorted by sortOrder,id; entries sorted by title (case-insensitive) then id
  suspend fun saveEntry(id: Long?, folderId: Long?, payload: VaultPayload, now: Long): Long  // title trimmed, blank -> IllegalArgumentException("title"); keeps createdAt on edit
  suspend fun deleteEntry(id: Long)
  suspend fun addFolder(name: String): Long; suspend fun renameFolder(id: Long, name: String); suspend fun deleteFolder(id: Long) // delete = move entries out + delete, one transaction
  suspend fun exportData(): VaultBackupData           // decrypt all; unreadable rows skipped
  suspend fun replaceAll(data: VaultBackupData, now: Long): Int  // one transaction: clear, insert folders then entries (folderKey mapped), returns entry count
  suspend fun count(): Int
}
```

### VaultBackup.kt (android glue) - task J5
```kotlin
class VaultBackup(private val app: ICoordinatorApp) {
  fun suggestedFileName(now: LocalDateTime = LocalDateTime.now()): String   // "iCoordinator-vault-yyyyMMdd-HHmm.icvault"
  suspend fun export(target: Uri, password: CharArray): String?   // null = ok; wipes password array after use (Arrays.fill)
  suspend fun readFile(source: Uri, password: CharArray): Pair<VaultBackupData?, String?>  // (data, null) or (null, Persian error: "رمز اصلی اشتباه است" / "این فایل پشتیبان رمزها نیست" / "فایل پشتیبان خراب است" / "خواندن فایل ممکن نشد"); file > 20 MB -> "فایل پشتیبان خراب است"
  suspend fun restore(data: VaultBackupData): String?   // repository.replaceAll; null = ok
}
```

### Screen security - file vault/VaultSession.kt (task K4b)
```kotlin
package com.icoordinator.vault
object SecureScreen { val forced: kotlinx.coroutines.flow.StateFlow<Boolean>; fun acquire(); fun release() }  // reference counted; forced = count > 0; release never below 0
object VaultSession { val unlocked: kotlinx.coroutines.flow.StateFlow<Boolean>; fun unlock(); fun lock() }   // starts false
```
MainActivity (task K6): FLAG_SECURE when `!allowScreenshots || SecureScreen.forced`; onStop (not a configuration change) calls VaultSession.lock().

### Vault UI contracts (sprint 7, package com.icoordinator.ui.vault)
K4a VaultViewModel.kt:
```kotlin
class VaultViewModel : ViewModel() {
  val snapshot: StateFlow<VaultSnapshot?>     // null until the first decrypted snapshot after start(); stays null while locked
  fun start()                                  // begins collecting app.vaultRepository.observeSnapshot() (idempotent); called only after unlock
  fun stop()                                   // cancels collection and sets snapshot to null (called on lock)
  fun entry(id: Long): VaultEntry?             // from the current snapshot
  fun saveEntry(id: Long?, folderId: Long?, payload: VaultPayload, onResult: (String?) -> Unit)   // null = ok, else Persian error
  fun deleteEntry(id: Long)
  fun addFolder(name: String, onResult: (String?) -> Unit); fun renameFolder(id: Long, name: String, onResult: (String?) -> Unit); fun deleteFolder(id: Long)
}
```
K4b VaultEntryScreen.kt and VaultBackupDialogs.kt:
```kotlin
@Composable fun VaultEntryScreen(vm: VaultViewModel, entryId: Long?, initialFolderId: Long?, onBack: () -> Unit)  // entryId null = new (edit mode); existing opens in view mode with eye + copy buttons, "ویرایش" switches to edit, delete with ConfirmDeleteDialog
enum class VaultBackupMode { EXPORT, IMPORT }
@Composable fun VaultBackupDialogs(mode: VaultBackupMode?, onDismiss: () -> Unit)   // mode null = nothing shown; owns its SAF launchers, master-password dialogs, restore confirmation and snackbar-free Persian result dialog
object SecretClipboard { fun copy(context: android.content.Context, text: String) }  // marks sensitive (API 33+ ClipDescription.EXTRA_IS_SENSITIVE), clears after 30 s
```
K4a VaultNavHost.kt: `@Composable fun VaultNavHost(onExit: () -> Unit)`; shows the app LockScreen until VaultSession.unlocked; routes "vault_list" and "vault_entry/{id}?folder={folder}" (id -1 = new, folder -1 = none).

## App wiring (task K6, ICoordinatorApp.kt)
```kotlin
val personalDatabase: PersonalDatabase by lazy { Room.databaseBuilder(this, PersonalDatabase::class.java, PersonalDatabase.DB_NAME).build() }
val personalRepository: PersonalRepository by lazy { PersonalRepository(personalDatabase, FileNoteImageStore(NoteImageFiles.dir(this))) }
val vaultDatabase: VaultDatabase by lazy { Room.databaseBuilder(this, VaultDatabase::class.java, VaultDatabase.DB_NAME).build() }
val vaultRepository: VaultRepository by lazy { VaultRepository(vaultDatabase, VaultKeyStore.cipher()) }
```

## UI entry points (sprint 7)
```kotlin
package com.icoordinator.ui.notes;   @Composable fun NotesNavHost(onExit: () -> Unit)
package com.icoordinator.ui.records; @Composable fun RecordsScreen(onExit: () -> Unit)
package com.icoordinator.ui.vault;   @Composable fun VaultNavHost(onExit: () -> Unit)
```
Root routes (RootNavHost): NOTES = "notes", RECORDS = "records", VAULT = "vault".

### Notes UI contracts (sprint 7, package com.icoordinator.ui.notes)
K1 NotesNavHost.kt: `@Composable fun NotesNavHost(onExit: () -> Unit)`; routes "notes_list" and "note_editor/{id}?folder={folder}" (id -1 = new, folder -1 = none); the editor route calls K2's NoteEditorScreen.
K2 NoteEditorScreen.kt: `@Composable fun NoteEditorScreen(noteId: Long?, folderId: Long?, onBack: () -> Unit)` (noteId null = new note; it creates its own NoteEditorViewModel with viewModel(key = "note_editor_" + (noteId ?: "new"))).
K2 NoteImages.kt (package com.icoordinator.ui.notes): `object NoteImages { suspend fun import(context: Context, uri: Uri): String?  /* returns new file name or null */ ; suspend fun load(context: Context, name: String, maxPx: Int): android.graphics.Bitmap? }`
Records: K3 RecordsScreen.kt `@Composable fun RecordsScreen(onExit: () -> Unit)` + RecordsViewModel.kt.
