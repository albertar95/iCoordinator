package com.icoordinator.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.icoordinator.data.entity.WeeklyReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReportDao {

    @Query("SELECT * FROM weekly_reports ORDER BY weekStart DESC")
    fun observeAll(): Flow<List<WeeklyReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: WeeklyReportEntity)

    @Query("DELETE FROM weekly_reports WHERE weekStart = :weekStart")
    suspend fun delete(weekStart: Long)
}
