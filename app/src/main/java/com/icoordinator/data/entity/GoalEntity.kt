package com.icoordinator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** هدف یک پروژه. weightPercent درصدی از کل پیشرفت پروژه است. */
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val weightPercent: Int,
    val isDone: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)
