package com.icoordinator

import android.app.Application
import androidx.room.Room
import com.icoordinator.alarm.AlarmRescheduler
import com.icoordinator.alarm.TaskAlarmScheduler
import com.icoordinator.data.AppDatabase
import com.icoordinator.data.ProjectRepository
import com.icoordinator.data.ReportRepository
import com.icoordinator.data.RoutineRepository
import com.icoordinator.data.TaskRepository

class ICoordinatorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    val database: AppDatabase by lazy {
        // فعلاً نسخه‌ی اول است؛ در مهاجرت‌های بعدی Migration جداگانه می‌نویسیم
        Room.databaseBuilder(this, AppDatabase::class.java, "icoordinator.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private val alarmScheduler: TaskAlarmScheduler by lazy { TaskAlarmScheduler(this) }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao())
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao(), alarmScheduler)
    }

    val routineRepository: RoutineRepository by lazy {
        RoutineRepository(database.routineDao(), alarmScheduler)
    }

    val reportRepository: ReportRepository by lazy {
        ReportRepository(database.weeklyReportDao(), database.taskDao(), database.routineDao())
    }

    val alarmRescheduler: AlarmRescheduler by lazy {
        AlarmRescheduler(database.taskDao(), database.routineDao(), alarmScheduler)
    }

    companion object {
        lateinit var instance: ICoordinatorApp
            private set
    }
}
