package com.icoordinator.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProject(projectId: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM goals ORDER BY projectId, sortOrder, id")
    fun observeAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE projectId = :projectId ORDER BY sortOrder, id")
    fun observeGoals(projectId: Long): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :goalId")
    fun observeGoal(goalId: Long): Flow<GoalEntity?>

    @Query("SELECT COALESCE(SUM(weightPercent), 0) FROM goals WHERE projectId = :projectId")
    suspend fun totalWeight(projectId: Long): Int

    @Query("SELECT MAX(sortOrder) FROM goals WHERE projectId = :projectId")
    suspend fun maxSortOrder(projectId: Long): Int?

    @Query("SELECT COALESCE(SUM(weightPercent), 0) FROM goals WHERE projectId = :projectId AND id != :goalId")
    suspend fun totalWeightExcluding(projectId: Long, goalId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Update
    suspend fun updateGoals(goals: List<GoalEntity>)

    @Delete
    suspend fun deleteGoal(goal: GoalEntity)
}
