package com.icoordinator.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * روتین: کاری که در روزهای مشخص هفته تکرار می‌شود.
 * daysMask: بیت i (از ۰) یعنی روز DayOfWeek با value = i+1 (دوشنبه = بیت ۰ ... یکشنبه = بیت ۶)
 * timeOfDay: ساعت به قالب HH:mm
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val daysMask: Int,
    val timeOfDay: String,
    val createdAt: Long = System.currentTimeMillis(),
)
