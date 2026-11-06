package com.icoordinator.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.icoordinator.data.dao.ProjectDao
import com.icoordinator.data.dao.RoutineDao
import com.icoordinator.data.dao.TaskDao
import com.icoordinator.data.dao.WeeklyReportDao
import com.icoordinator.data.entity.GoalEntity
import com.icoordinator.data.entity.ProjectEntity
import com.icoordinator.data.entity.RoutineCompletionEntity
import com.icoordinator.data.entity.RoutineEntity
import com.icoordinator.data.entity.TaskEntity
import com.icoordinator.data.entity.WeeklyReportEntity

@Database(
    entities = [
        ProjectEntity::class,
        GoalEntity::class,
        TaskEntity::class,
        RoutineEntity::class,
        RoutineCompletionEntity::class,
        WeeklyReportEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun routineDao(): RoutineDao
    abstract fun weeklyReportDao(): WeeklyReportDao
}
