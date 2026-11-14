package com.icoordinator

import android.app.Application
import androidx.room.Room
import com.icoordinator.accounting.AccountingDatabase
import com.icoordinator.accounting.AccountingRepository
import com.icoordinator.accounting.InstallmentReminders
import com.icoordinator.alarm.AlarmRescheduler
import com.icoordinator.alarm.TaskAlarmScheduler
import com.icoordinator.data.AppDatabase
import com.icoordinator.data.Migrations
import com.icoordinator.data.ProjectRepository
import com.icoordinator.data.ReportRepository
import com.icoordinator.data.RoutineRepository
import com.icoordinator.data.TaskRepository
import com.icoordinator.personal.FileNoteImageStore
import com.icoordinator.personal.NoteImageFiles
import com.icoordinator.personal.PersonalDatabase
import com.icoordinator.personal.PersonalRepository
import com.icoordinator.vault.VaultDatabase
import com.icoordinator.vault.VaultKeyStore
import com.icoordinator.vault.VaultRepository

class ICoordinatorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        InstallmentReminders.start(this)
    }

    val database: AppDatabase by lazy {
        // every schema change needs a Migration in data/Migrations.kt
        Room.databaseBuilder(this, AppDatabase::class.java, "icoordinator.db")
            .addMigrations(Migrations.MIGRATION_5_6, Migrations.MIGRATION_6_7)
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

    val accountingDatabase: AccountingDatabase by lazy {
        Room.databaseBuilder(this, AccountingDatabase::class.java, AccountingDatabase.DB_NAME).build()
    }

    val accountingRepository: AccountingRepository by lazy {
        AccountingRepository(accountingDatabase)
    }

    val personalDatabase: PersonalDatabase by lazy {
        Room.databaseBuilder(this, PersonalDatabase::class.java, PersonalDatabase.DB_NAME).build()
    }

    val personalRepository: PersonalRepository by lazy {
        PersonalRepository(personalDatabase, FileNoteImageStore(NoteImageFiles.dir(this)))
    }

    val vaultDatabase: VaultDatabase by lazy {
        Room.databaseBuilder(this, VaultDatabase::class.java, VaultDatabase.DB_NAME).build()
    }

    val vaultRepository: VaultRepository by lazy {
        VaultRepository(vaultDatabase, VaultKeyStore.cipher())
    }

    companion object {
        lateinit var instance: ICoordinatorApp
            private set
    }
}
