package com.icoordinator.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** ثبت انجام یک روتین در یک روز مشخص. date به صورت epochDay ذخیره می‌شود. */
@Entity(
    tableName = "routine_completions",
    primaryKeys = ["routineId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class RoutineCompletionEntity(
    val routineId: Long,
    val date: Long,
)
