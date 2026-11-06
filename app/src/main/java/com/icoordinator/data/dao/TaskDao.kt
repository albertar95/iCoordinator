package com.icoordinator.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.icoordinator.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE weekStart = :weekStart ORDER BY dueAt")
    fun observeWeek(weekStart: Long): Flow<List<TaskEntity>>

    /** برای تقویم: تسک‌های بین دو زمان (شامل from، غیرشامل to). */
    @Query("SELECT * FROM tasks WHERE dueAt >= :from AND dueAt < :to ORDER BY dueAt")
    fun observeRange(from: Long, to: Long): Flow<List<TaskEntity>>

    /** برای بازتنظیم آلارم‌ها بعد از ری‌استارت: تسک‌های انجام‌نشده‌ی آینده */
    @Query("SELECT * FROM tasks WHERE isDone = 0 AND dueAt > :now")
    suspend fun upcomingUndone(now: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE goalId = :goalId ORDER BY dueAt")
    fun observeByGoal(goalId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE goalId IS NOT NULL")
    fun observeWithGoal(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}
