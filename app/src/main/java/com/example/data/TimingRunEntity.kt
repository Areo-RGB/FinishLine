package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timing_runs")
data class TimingRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runnerName: String,
    val eventTitle: String,
    val startTimeMs: Long,
    val finishCameraTimestampNs: Long,
    val finishWallClockMs: Long,
    val elapsedTimeMs: Long,
    val direction: String,
    val peakMotionPercent: Float,
    val notes: String = "",
    val recordedDate: String
)
