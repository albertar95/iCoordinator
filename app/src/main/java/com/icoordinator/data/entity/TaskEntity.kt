package com.icoordinator.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * تسک هفتگی.
 * weekStart: روز شنبه‌ی آغاز هفته به صورت epochDay
 * dueAt: زمان انجام تسک به صورت epoch millis (برای تقویم و آلارم در اسپرینت‌های بعد)
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("weekStart"), Index("dueAt"), Index("projectId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,
    val dueAt: Long,
    val title: String,
    val projectId: Long? = null,
    val isDone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
