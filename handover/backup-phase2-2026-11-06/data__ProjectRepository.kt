package com.icoordinator.data

import com.icoordinator.data.dao.ProjectDao
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.domain.ProgressCalculator
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val dao: ProjectDao) {

    fun observeProjects(): Flow<List<ProjectEntity>> = dao.observeProjects()

    fun observeProject(id: Long): Flow<ProjectEntity?> = dao.observeProject(id)

    fun observeAllGoals(): Flow<List<GoalEntity>> = dao.observeAllGoals()

    fun observeGoals(projectId: Long): Flow<List<GoalEntity>> = dao.observeGoals(projectId)

    suspend fun createProject(title: String): Long =
        dao.insertProject(ProjectEntity(title = title.trim()))

    suspend fun updateManifesto(project: ProjectEntity, text: String) =
        dao.updateProject(project.copy(manifesto = text))

    suspend fun deleteProject(project: ProjectEntity) = dao.deleteProject(project)

    /** برمی‌گرداند: null یعنی موفق، در غیر این صورت پیام خطا. */
    suspend fun addGoal(projectId: Long, title: String, weightPercent: Int): String? {
        val total = dao.totalWeight(projectId)
        ProgressCalculator.validateNewWeight(total, weightPercent)?.let { return it }
        dao.insertGoal(GoalEntity(projectId = projectId, title = title.trim(), weightPercent = weightPercent))
        return null
    }

    suspend fun toggleGoal(goal: GoalEntity) = dao.updateGoal(goal.copy(isDone = !goal.isDone))

    suspend fun deleteGoal(goal: GoalEntity) = dao.deleteGoal(goal)
}
