package com.icoordinator.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** گزارش ذخیره‌شده‌ی یک هفته. weekStart به صورت epochDay (شنبه‌ی آغاز هفته). */
@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
    @PrimaryKey val weekStart: Long,
    val generatedAt: Long,
    val percent: Int?,
    val scoreName: String?,
    val text: String,
)
