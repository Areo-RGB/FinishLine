package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimingDao {
    @Query("SELECT * FROM timing_runs ORDER BY id DESC")
    fun getAllRuns(): Flow<List<TimingRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: TimingRunEntity): Long

    @Delete
    suspend fun deleteRun(run: TimingRunEntity)

    @Query("DELETE FROM timing_runs")
    suspend fun clearAll()
}
