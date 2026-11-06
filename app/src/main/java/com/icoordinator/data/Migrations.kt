package com.icoordinator.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** هر تغییر در ساختار دیتابیس باید اینجا یک Migration داشته باشد. */
object Migrations {

    val SQL_5_6: List<String> = listOf(
        "ALTER TABLE `goals` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0",
        "UPDATE `goals` SET `sortOrder` = `id`",
        "CREATE TABLE `tasks_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weekStart` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, `title` TEXT NOT NULL, `projectId` INTEGER, `isDone` INTEGER NOT NULL, `alarmOffsetMinutes` INTEGER, `createdAt` INTEGER NOT NULL, `goalId` INTEGER, `goalContribution` INTEGER, FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)",
        "INSERT INTO `tasks_new` (`id`, `weekStart`, `dueAt`, `title`, `projectId`, `isDone`, `alarmOffsetMinutes`, `createdAt`) SELECT `id`, `weekStart`, `dueAt`, `title`, `projectId`, `isDone`, `alarmOffsetMinutes`, `createdAt` FROM `tasks`",
        "DROP TABLE `tasks`",
        "ALTER TABLE `tasks_new` RENAME TO `tasks`",
        "CREATE INDEX `index_tasks_weekStart` ON `tasks` (`weekStart`)",
        "CREATE INDEX `index_tasks_dueAt` ON `tasks` (`dueAt`)",
        "CREATE INDEX `index_tasks_projectId` ON `tasks` (`projectId`)",
        "CREATE INDEX `index_tasks_goalId` ON `tasks` (`goalId`)",
    )

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_5_6.forEach { db.execSQL(it) }
        }
    }
}
