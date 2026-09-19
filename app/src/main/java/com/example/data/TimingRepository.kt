package com.example.data

import kotlinx.coroutines.flow.Flow

class TimingRepository(private val timingDao: TimingDao) {
    val allRuns: Flow<List<TimingRunEntity>> = timingDao.getAllRuns()

    suspend fun insertRun(run: TimingRunEntity): Long {
        return timingDao.insertRun(run)
    }

    suspend fun deleteRun(run: TimingRunEntity) {
        timingDao.deleteRun(run)
    }

    suspend fun clearAll() {
        timingDao.clearAll()
    }
}
