package com.batttext.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: BatterySampleEntity): Long

    @Query("SELECT * FROM battery_samples WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis ASC")
    fun observeSamplesSince(sinceMillis: Long): Flow<List<BatterySampleEntity>>

    @Query("DELETE FROM battery_samples WHERE timestampMillis < :cutoffMillis")
    suspend fun pruneSamplesOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM battery_samples")
    suspend fun clearAllSamples()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BatterySessionEntity): Long

    @Update
    suspend fun updateSession(session: BatterySessionEntity)

    @Query("SELECT * FROM battery_sessions WHERE isActive = 1 ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getActiveSession(): BatterySessionEntity?

    @Query("SELECT * FROM battery_sessions ORDER BY startTimeMillis DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 100): Flow<List<BatterySessionEntity>>

    @Query("DELETE FROM battery_sessions WHERE startTimeMillis < :cutoffMillis AND isActive = 0")
    suspend fun pruneSessionsOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM battery_sessions")
    suspend fun clearAllSessions()
}
