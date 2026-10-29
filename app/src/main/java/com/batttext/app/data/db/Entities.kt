package com.batttext.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "battery_samples",
    indices = [Index(value = ["timestampMillis"])]
)
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val levelPercent: Int,
    val currentMa: Int,
    val voltageMv: Int,
    val temperatureDeciC: Int,
    val isCharging: Boolean,
    val isScreenOn: Boolean
)

@Entity(
    tableName = "battery_sessions",
    indices = [Index(value = ["startTimeMillis"])]
)
data class BatterySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isCharging: Boolean,
    val plugType: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val startLevel: Int,
    val endLevel: Int,
    val avgCurrentMa: Int,
    val maxTempDeciC: Int,
    val sampleCount: Int,
    val isActive: Boolean
)
