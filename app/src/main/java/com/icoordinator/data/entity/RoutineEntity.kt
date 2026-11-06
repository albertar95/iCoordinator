package com.icoordinator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * روتین: کاری که در روزهای مشخص هفته بین startDay و endDay تکرار می‌شود.
 * daysMask: بیت i (از ۰) یعنی روزی با DayOfWeek.value = i+1
 * timeOfDay: ساعت به قالب HH:mm
 * startDay / endDay: epochDay. endDay = null یعنی بدون پایان.
 * alarmOffsetMinutes: null یعنی بدون آلارم، ۰ یعنی همان ساعت روتین، و عدد مثبت یعنی چند دقیقه قبل
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val daysMask: Int,
    val timeOfDay: String,
    val startDay: Long,
    val endDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val alarmOffsetMinutes: Int? = 0,
)
