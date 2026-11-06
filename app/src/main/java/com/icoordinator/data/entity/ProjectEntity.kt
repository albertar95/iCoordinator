package com.icoordinator.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * پروژه.
 * sortOrder: ترتیب نمایش در فهرست پروژه‌ها (۰ اولین است)
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val manifesto: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)
