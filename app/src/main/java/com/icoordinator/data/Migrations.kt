package com.icoordinator.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.icoordinator.domain.PersianSort

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

    val SQL_6_7: List<String> = listOf(
        "ALTER TABLE `tasks` ADD COLUMN `seriesId` INTEGER",
        "CREATE INDEX IF NOT EXISTS `index_tasks_seriesId` ON `tasks` (`seriesId`)",
        "ALTER TABLE `projects` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `routines` ADD COLUMN `alarmOffsetMinutes` INTEGER DEFAULT 0",
    )

    /** ids of the given (id, title) rows in Persian alphabetical order of title, ties by id. */
    fun projectOrder(rows: List<Pair<Long, String>>): List<Long> =
        rows.sortedWith(
            Comparator<Pair<Long, String>> { a, b ->
                val c = PersianSort.compare(a.second, b.second)
                if (c != 0) c else a.first.compareTo(b.first)
            },
        ).map { it.first }

    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_6_7.forEach { db.execSQL(it) }
            val rows = ArrayList<Pair<Long, String>>()
            db.query("SELECT id, title FROM projects").use { cursor ->
                while (cursor.moveToNext()) {
                    rows.add(Pair(cursor.getLong(0), cursor.getString(1) ?: ""))
                }
            }
            projectOrder(rows).forEachIndexed { order, id ->
                db.execSQL("UPDATE projects SET sortOrder = ? WHERE id = ?", arrayOf<Any?>(order, id))
            }
        }
    }
}
