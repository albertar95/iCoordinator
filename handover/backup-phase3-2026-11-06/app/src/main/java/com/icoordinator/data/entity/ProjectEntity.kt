package com.icoordinator.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val manifesto: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)
