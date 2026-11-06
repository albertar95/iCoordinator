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

    fun observeGoal(id: Long): Flow<GoalEntity?> = dao.observeGoal(id)

    fun observeGoals(projectId: Long): Flow<List<GoalEntity>> = dao.observeGoals(projectId)

    /** پروژه‌ی جدید در انتهای فهرست قرار می‌گیرد. */
    suspend fun createProject(title: String): Long {
        val sortOrder = (dao.maxProjectSortOrder() ?: -1) + 1
        return dao.insertProject(ProjectEntity(title = title.trim(), sortOrder = sortOrder))
    }

    /** ترتیب جدید پروژه‌ها: sortOrder = شماره‌ی جایگاه (از ۰)؛ فقط ردیف‌هایی که تغییر می‌کنند نوشته می‌شوند. */
    suspend fun reorderProjects(ordered: List<ProjectEntity>) {
        val changed = ordered.mapIndexedNotNull { index, project ->
            if (project.sortOrder != index) project.copy(sortOrder = index) else null
        }
        if (changed.isNotEmpty()) dao.updateProjects(changed)
    }

    suspend fun updateManifesto(project: ProjectEntity, text: String) =
        dao.updateProject(project.copy(manifesto = text))

    suspend fun deleteProject(project: ProjectEntity) = dao.deleteProject(project)

    /** برمی‌گرداند: null یعنی موفق، در غیر این صورت پیام خطا. */
    suspend fun addGoal(projectId: Long, title: String, weightPercent: Int): String? {
        val total = dao.totalWeight(projectId)
        ProgressCalculator.validateNewWeight(total, weightPercent)?.let { return it }
        val sortOrder = (dao.maxSortOrder(projectId) ?: 0) + 1
        dao.insertGoal(
            GoalEntity(
                projectId = projectId,
                title = title.trim(),
                weightPercent = weightPercent,
                sortOrder = sortOrder,
            ),
        )
        return null
    }

    /** برمی‌گرداند: null یعنی موفق، در غیر این صورت پیام خطا. سقف ۱۰۰٪ بدون احتساب خود هدف بررسی می‌شود. */
    suspend fun updateGoal(goal: GoalEntity, title: String, weightPercent: Int): String? {
        val clean = title.trim()
        if (clean.isEmpty()) return "عنوان هدف را وارد کن"
        val others = dao.totalWeightExcluding(goal.projectId, goal.id)
        ProgressCalculator.validateNewWeight(others, weightPercent)?.let { return it }
        dao.updateGoal(goal.copy(title = clean, weightPercent = weightPercent))
        return null
    }

    /** ترتیب جدید اهداف: sortOrder = شماره‌ی جایگاه (از ۱) */
    suspend fun reorderGoals(ordered: List<GoalEntity>) =
        dao.updateGoals(ordered.mapIndexed { index, goal -> goal.copy(sortOrder = index + 1) })

    suspend fun toggleGoal(goal: GoalEntity) = dao.updateGoal(goal.copy(isDone = !goal.isDone))

    suspend fun deleteGoal(goal: GoalEntity) = dao.deleteGoal(goal)
}
