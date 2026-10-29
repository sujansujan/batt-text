package com.batttext.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import com.batttext.app.data.db.BatteryDao
import com.batttext.app.data.db.BatterySampleEntity
import com.batttext.app.data.db.BatterySessionEntity
import com.batttext.app.data.prefs.SettingsRepository
import com.batttext.app.data.shizuku.DeviceHardwareSpecs
import com.batttext.app.data.shizuku.HardwareSpecReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

data class BatterySnapshot(
    val timestampMillis: Long = System.currentTimeMillis(),
    val levelPercent: Int = 0,
    val statusText: String = "UNKNOWN",
    val plugType: String = "UNPLUGGED",
    val isCharging: Boolean = false,
    val currentMa: Int = 0,
    val avgCurrentMa: Int = 0,
    val voltageMv: Int = 0,
    val temperatureDeciC: Int = 0,
    val healthText: String = "GOOD",
    val technology: String = "LI-ION",
    val chargeCounterMah: Int? = null,
    val estimatedFullCapacityMah: Int? = null,
    val effectiveDesignCapacityMah: Int = 5000,
    val speedPercentPerHour: Float = 0f,
    val etaMinutes: Int? = null,
    val powerWatts: Float = 0f,
    val isScreenOn: Boolean = true,
    val isDeviceIdleDoze: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val bootElapsedRealtimeMs: Long = 0L,
    val bootDeepSleepMs: Long = 0L,
    val bootAwakeUptimeMs: Long = 0L,
    val hardwareSpecs: DeviceHardwareSpecs = DeviceHardwareSpecs()
)

class BatteryRepository(
    private val context: Context,
    private val dao: BatteryDao,
    private val settingsRepository: SettingsRepository,
    private val hardwareSpecReader: HardwareSpecReader
) {
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _snapshot = MutableStateFlow(BatterySnapshot())
    val snapshot: StateFlow<BatterySnapshot> = _snapshot.asStateFlow()

    private val recentCurrentsMa = ArrayDeque<Int>(12)

    private var lastSampleSavedAtMillis: Long = 0L
    private var activeSessionCache: BatterySessionEntity? = null
    private var sessionRunningSumMa: Long = 0L

    init {
        runCatching {
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            updateFromIntent(sticky, designCapOverrideMah = 0)
        }
    }

    fun refreshHardwareSpecs(designCapOverrideMah: Int): BatterySnapshot {
        hardwareSpecReader.discoverSpecs(forceRefresh = true)
        return updateFromIntent(null, designCapOverrideMah)
    }

    fun updateFromIntent(intent: Intent?, designCapOverrideMah: Int): BatterySnapshot {
        val hwSpecs = hardwareSpecReader.discoverSpecs(forceRefresh = false)
        val designCapMah = if (designCapOverrideMah > 0) designCapOverrideMah else hwSpecs.autoDesignCapacityMah

        val src = intent ?: runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val now = System.currentTimeMillis()

        val rawLevel = src?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = src?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val levelPct = if (rawLevel >= 0) ((rawLevel * 100f) / scale).roundToInt().coerceIn(0, 100) else 0

        val statusInt = src?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val pluggedInt = src?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val isCharging = statusInt == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusInt == BatteryManager.BATTERY_STATUS_FULL || pluggedInt != 0

        val plugType = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WLS"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "DOCK"
            else -> "NONE"
        }

        val statusText = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT CHARGING"
            else -> if (pluggedInt != 0) "CHARGING" else "DISCHARGING"
        }

        val rawVoltage = src?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageMv = when {
            rawVoltage <= 0 -> 0
            rawVoltage < 20 -> rawVoltage * 1000
            rawVoltage < 100 -> rawVoltage * 100
            else -> rawVoltage
        }

        val rawTemp = src?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempDeciC = if (rawTemp in 1..1200) rawTemp else 0

        val healthInt = src?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthText = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER-VOLT"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            else -> "UNKNOWN"
        }

        val tech = src?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() }?.uppercase() ?: "LI-ION"

        val rawCurrentProp = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(0)
        val normalizedCurrentMa = normalizeCurrentToMa(rawCurrentProp, isCharging, statusInt)

        synchronized(recentCurrentsMa) {
            if (normalizedCurrentMa != 0) {
                if (recentCurrentsMa.isNotEmpty() && (recentCurrentsMa.last() > 0) != (normalizedCurrentMa > 0)) {
                    recentCurrentsMa.clear()
                }
                if (recentCurrentsMa.size >= 12) recentCurrentsMa.removeFirst()
                recentCurrentsMa.addLast(normalizedCurrentMa)
            }
        }

        val avgMa = synchronized(recentCurrentsMa) {
            if (recentCurrentsMa.isEmpty()) normalizedCurrentMa
            else recentCurrentsMa.average().roundToInt()
        }

        val rawChargeCounterUah = runCatching {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        }.getOrDefault(0)
        val chargeCounterMah = if (rawChargeCounterUah > 10_000 && rawChargeCounterUah != Int.MIN_VALUE) {
            (rawChargeCounterUah / 1000f).roundToInt()
        } else if (rawChargeCounterUah in 100..10_000) {
            rawChargeCounterUah
        } else {
            null
        }

        // Prefer kernel learned full capacity if available via sysfs/Shizuku, else compute from CHARGE_COUNTER
        val estFullCapMah = hwSpecs.kernelFullCapacityMah ?: if (chargeCounterMah != null && levelPct >= 15) {
            ((chargeCounterMah.toFloat() / levelPct.toFloat()) * 100f).roundToInt()
                .takeIf { it in 500..25000 }
        } else {
            null
        }

        val effectiveCapacityMah = (estFullCapMah ?: designCapMah).coerceAtLeast(500)

        val speedPctPerHr = if (avgMa != 0) {
            (abs(avgMa).toFloat() / effectiveCapacityMah.toFloat()) * 100f
        } else {
            0f
        }

        val etaMinutes = when {
            statusInt == BatteryManager.BATTERY_STATUS_FULL -> 0
            isCharging && avgMa > 25 && levelPct < 100 -> {
                val remainingMah = effectiveCapacityMah * ((100 - levelPct) / 100f)
                ((remainingMah / avgMa.toFloat()) * 60f).roundToInt().coerceIn(1, 2880)
            }
            !isCharging && avgMa < -15 && levelPct > 0 -> {
                val remainingMah = effectiveCapacityMah * (levelPct / 100f)
                ((remainingMah / abs(avgMa).toFloat()) * 60f).roundToInt().coerceIn(1, 5760)
            }
            else -> null
        }

        val powerWatts = if (voltageMv > 0 && normalizedCurrentMa != 0) {
            (voltageMv / 1000f) * (abs(normalizedCurrentMa) / 1000f)
        } else {
            0f
        }

        val screenOn = runCatching { powerManager.isInteractive }.getOrDefault(true)
        val isDoze = runCatching { powerManager.isDeviceIdleMode }.getOrDefault(false)
        val isPowerSave = runCatching { powerManager.isPowerSaveMode }.getOrDefault(false)

        val elapsedMs = SystemClock.elapsedRealtime().coerceAtLeast(1L)
        val uptimeMs = SystemClock.uptimeMillis().coerceIn(0L, elapsedMs)
        val deepSleepMs = (elapsedMs - uptimeMs).coerceAtLeast(0L)

        val snap = BatterySnapshot(
            timestampMillis = now,
            levelPercent = levelPct,
            statusText = statusText,
            plugType = plugType,
            isCharging = isCharging,
            currentMa = normalizedCurrentMa,
            avgCurrentMa = avgMa,
            voltageMv = voltageMv,
            temperatureDeciC = tempDeciC,
            healthText = healthText,
            technology = tech,
            chargeCounterMah = chargeCounterMah,
            estimatedFullCapacityMah = estFullCapMah,
            effectiveDesignCapacityMah = designCapMah,
            speedPercentPerHour = speedPctPerHr,
            etaMinutes = etaMinutes,
            powerWatts = powerWatts,
            isScreenOn = screenOn,
            isDeviceIdleDoze = isDoze,
            isPowerSaveMode = isPowerSave,
            bootElapsedRealtimeMs = elapsedMs,
            bootDeepSleepMs = deepSleepMs,
            bootAwakeUptimeMs = uptimeMs,
            hardwareSpecs = hwSpecs
        )
        _snapshot.value = snap
        return snap
    }

    private fun normalizeCurrentToMa(rawProp: Int, isCharging: Boolean, statusInt: Int): Int {
        if (rawProp == 0 || rawProp == Int.MIN_VALUE || rawProp == Int.MAX_VALUE) return 0
        val absVal = abs(rawProp)
        val magnitudeMa = if (absVal > 25_000) (absVal / 1000f).roundToInt() else absVal
        if (magnitudeMa == 0) return 0

        return when {
            statusInt == BatteryManager.BATTERY_STATUS_FULL && magnitudeMa < 50 -> 0
            isCharging -> magnitudeMa
            else -> -magnitudeMa
        }
    }

    suspend fun recordTelemetryIfNeeded(
        snapshot: BatterySnapshot,
        sampleIntervalMinutes: Int,
        retentionDays: Int
    ) {
        val now = snapshot.timestampMillis
        val intervalMs = sampleIntervalMinutes.coerceAtLeast(1) * 60_000L

        if (now - lastSampleSavedAtMillis >= intervalMs) {
            lastSampleSavedAtMillis = now
            dao.insertSample(
                BatterySampleEntity(
                    timestampMillis = now,
                    levelPercent = snapshot.levelPercent,
                    currentMa = snapshot.currentMa,
                    voltageMv = snapshot.voltageMv,
                    temperatureDeciC = snapshot.temperatureDeciC,
                    isCharging = snapshot.isCharging,
                    isScreenOn = snapshot.isScreenOn
                )
            )
            val cutoffMs = now - (retentionDays.coerceAtLeast(1) * 86_400_000L)
            dao.pruneSamplesOlderThan(cutoffMs)
            dao.pruneSessionsOlderThan(cutoffMs)
        }

        updateSessionTracking(snapshot)
    }

    private suspend fun updateSessionTracking(snapshot: BatterySnapshot) {
        val currentActive = activeSessionCache ?: dao.getActiveSession()
        val now = snapshot.timestampMillis

        if (currentActive == null) {
            sessionRunningSumMa = snapshot.currentMa.toLong()
            val newSession = BatterySessionEntity(
                isCharging = snapshot.isCharging,
                plugType = snapshot.plugType,
                startTimeMillis = now,
                endTimeMillis = now,
                startLevel = snapshot.levelPercent,
                endLevel = snapshot.levelPercent,
                avgCurrentMa = snapshot.currentMa,
                maxTempDeciC = snapshot.temperatureDeciC,
                sampleCount = 1,
                isActive = true
            )
            val id = dao.insertSession(newSession)
            activeSessionCache = newSession.copy(id = id)
        } else if (currentActive.isCharging != snapshot.isCharging) {
            val closed = currentActive.copy(
                endTimeMillis = now,
                isActive = false
            )
            dao.updateSession(closed)

            sessionRunningSumMa = snapshot.currentMa.toLong()
            val nextSession = BatterySessionEntity(
                isCharging = snapshot.isCharging,
                plugType = snapshot.plugType,
                startTimeMillis = now,
                endTimeMillis = now,
                startLevel = snapshot.levelPercent,
                endLevel = snapshot.levelPercent,
                avgCurrentMa = snapshot.currentMa,
                maxTempDeciC = snapshot.temperatureDeciC,
                sampleCount = 1,
                isActive = true
            )
            val newId = dao.insertSession(nextSession)
            activeSessionCache = nextSession.copy(id = newId)
        } else {
            val nextCount = currentActive.sampleCount + 1
            sessionRunningSumMa += snapshot.currentMa.toLong()
            val newAvg = (sessionRunningSumMa / nextCount).toInt()
            val updated = currentActive.copy(
                plugType = if (snapshot.isCharging) snapshot.plugType else currentActive.plugType,
                endTimeMillis = now,
                endLevel = snapshot.levelPercent,
                avgCurrentMa = newAvg,
                maxTempDeciC = maxOf(currentActive.maxTempDeciC, snapshot.temperatureDeciC),
                sampleCount = nextCount
            )
            dao.updateSession(updated)
            activeSessionCache = updated
        }
    }

    fun observeSamplesSince(sinceMillis: Long): Flow<List<BatterySampleEntity>> =
        dao.observeSamplesSince(sinceMillis)

    fun observeSessions(limit: Int = 100): Flow<List<BatterySessionEntity>> =
        dao.observeRecentSessions(limit)

    suspend fun clearAllHistory() {
        dao.clearAllSamples()
        dao.clearAllSessions()
        activeSessionCache = null
        sessionRunningSumMa = 0L
        lastSampleSavedAtMillis = 0L
    }
}
