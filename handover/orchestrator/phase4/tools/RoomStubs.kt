// LOCAL COMPILE STUBS ONLY (never shipped). Mimic the androidx.room API surface used by phase 4.
package androidx.room
import kotlin.reflect.KClass
@Target(AnnotationTarget.CLASS) annotation class Entity(val tableName: String = "", val indices: Array<Index> = [], val foreignKeys: Array<ForeignKey> = [])
annotation class Index(vararg val value: String, val unique: Boolean = false)
annotation class ForeignKey(val entity: KClass<*>, val parentColumns: Array<String>, val childColumns: Array<String>, val onDelete: Int = 1) { companion object { const val CASCADE = 5; const val SET_NULL = 3; const val NO_ACTION = 1 } }
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class ColumnInfo(val name: String = "", val defaultValue: String = "")
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class Ignore
@Target(AnnotationTarget.CLASS) annotation class Dao
@Target(AnnotationTarget.FUNCTION) annotation class Query(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)
@Target(AnnotationTarget.FUNCTION) annotation class Update(val onConflict: Int = OnConflictStrategy.ABORT)
@Target(AnnotationTarget.FUNCTION) annotation class Delete
@Target(AnnotationTarget.FUNCTION) annotation class Upsert
@Target(AnnotationTarget.FUNCTION) annotation class Transaction
@Target(AnnotationTarget.CLASS) annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)
object OnConflictStrategy { const val REPLACE = 1; const val ABORT = 3; const val IGNORE = 5 }
abstract class RoomDatabase { open fun close() {} }
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R = block()
