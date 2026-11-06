package com.icoordinator.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.icoordinator.data.entity.RoutineCompletionEntity
import com.icoordinator.data.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routines ORDER BY createdAt")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_completions WHERE date >= :from AND date < :to")
    fun observeCompletions(from: Long, to: Long): Flow<List<RoutineCompletionEntity>>

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: RoutineCompletionEntity)

    @Query("DELETE FROM routine_completions WHERE routineId = :routineId AND date = :date")
    suspend fun deleteCompletion(routineId: Long, date: Long)
}
