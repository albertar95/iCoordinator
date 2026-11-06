package com.icoordinator.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * تسک هفتگی. هر تکرار یک ردیف جداگانه است.
 * weekStart: شنبه‌ی آغاز هفته (epochDay)
 * dueAt: زمان انجام به میلی‌ثانیه
 * alarmOffsetMinutes: null یعنی بدون آلارم، ۰ یعنی لحظه‌ی رسیدن، و عدد مثبت یعنی چند دقیقه قبل
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
    val alarmOffsetMinutes: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
