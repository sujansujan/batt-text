package com.batttext.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.batttext.app.BattTextApp
import com.batttext.app.data.prefs.ScreenDrainStats
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.data.repository.BatterySnapshot
import com.batttext.app.notification.NotificationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Lightweight, event-driven foreground service.
 * Uses zero wake locks and zero high-frequency background polling: updates occur strictly when
 * the system dispatches ACTION_BATTERY_CHANGED or ACTION_SCREEN_ON / ACTION_SCREEN_OFF.
 */
class BatteryMonitorService : LifecycleService() {

    private val app: BattTextApp
        get() = application as BattTextApp

    private var latestSettings = UserSettings()
    private var latestScreenStats = ScreenDrainStats()

    // State trackers for screen-on/off drain accounting
    private var isScreenInteractive: Boolean = true
    private var lastSliceTimestampMs: Long = System.currentTimeMillis()
    private var lastSliceBatteryLevel: Int = -1
    private var wasChargingLastSlice: Boolean = false

    // One-shot latches so each alert fires once per threshold crossing and resets cleanly
    private var firedChargeLimit = false
    private var firedFullCharge = false
    private var firedLowBattery = false
    private var firedCriticalBattery = false
    private var firedHighTemp = false
    private var lastPluggedState: Boolean? = null

    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> handleBatteryBroadcast(intent)
                Intent.ACTION_SCREEN_ON -> handleScreenStateChange(screenOn = true)
                Intent.ACTION_SCREEN_OFF -> handleScreenStateChange(screenOn = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenInteractive = pm.isInteractive
        lastSliceTimestampMs = System.currentTimeMillis()

        val initialSnap = app.batteryRepository.updateFromIntent(null, latestSettings.designCapacityMah)
        lastSliceBatteryLevel = initialSnap.levelPercent
        wasChargingLastSlice = initialSnap.isCharging
        lastPluggedState = initialSnap.isCharging

        promoteToForegroundSafely(initialSnap)

        // Observe settings & screen stats changes so notification formatting updates immediately
        lifecycleScope.launch {
            combine(
                app.settingsRepository.settingsFlow,
                app.settingsRepository.screenDrainFlow
            ) { settings, screenStats ->
                settings to screenStats
            }.collectLatest { (settings, screenStats) ->
                latestSettings = settings
                latestScreenStats = screenStats
                val snap = app.batteryRepository.updateFromIntent(null, settings.designCapacityMah)
                app.notificationHelper.updateOngoingNotification(snap, settings, screenStats)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // System broadcasts on Android 14+ / Xiaomi HyperOS require RECEIVER_EXPORTED
        ContextCompat.registerReceiver(
            this,
            systemEventsReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val snap = app.batteryRepository.updateFromIntent(null, latestSettings.designCapacityMah)
        promoteToForegroundSafely(snap)
        app.notificationHelper.updateOngoingNotification(snap, latestSettings, latestScreenStats)
        return START_STICKY
    }

    private fun promoteToForegroundSafely(snap: BatterySnapshot) {
        runCatching {
            val notification = app.notificationHelper.buildOngoingNotification(
                snap,
                latestSettings,
                latestScreenStats
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.ONGOING_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationHelper.ONGOING_NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(systemEventsReceiver) }
        super.onDestroy()
    }

    private fun handleBatteryBroadcast(intent: Intent) {
        val snap = app.batteryRepository.updateFromIntent(intent, latestSettings.designCapacityMah)
        val now = System.currentTimeMillis()

        // Account for screen-on/off discharge since the last broadcast
        if (lastSliceBatteryLevel in 0..100) {
            val deltaLevel = if (!snap.isCharging && !wasChargingLastSlice) {
                (lastSliceBatteryLevel - snap.levelPercent).coerceAtLeast(0)
            } else {
                0
            }
            val deltaTime = if (!snap.isCharging && !wasChargingLastSlice) {
                (now - lastSliceTimestampMs).coerceAtLeast(0L)
            } else {
                0L
            }
            if (deltaLevel > 0 || deltaTime >= 30_000L) {
                lifecycleScope.launch {
                    app.settingsRepository.recordScreenSlice(
                        isScreenOn = isScreenInteractive,
                        deltaPercent = deltaLevel,
                        deltaDurationMs = deltaTime
                    )
                }
                lastSliceTimestampMs = now
                lastSliceBatteryLevel = snap.levelPercent
            }
        } else {
            lastSliceBatteryLevel = snap.levelPercent
            lastSliceTimestampMs = now
        }

        if (wasChargingLastSlice != snap.isCharging) {
            lastSliceBatteryLevel = snap.levelPercent
            lastSliceTimestampMs = now
            wasChargingLastSlice = snap.isCharging
        }

        // Persist periodic history sample & update active session
        lifecycleScope.launch {
            app.batteryRepository.recordTelemetryIfNeeded(
                snapshot = snap,
                sampleIntervalMinutes = latestSettings.sampleIntervalMinutes,
                retentionDays = latestSettings.retentionDays
            )
        }

        evaluateThresholdsAndAlert(snap, latestSettings)
        app.notificationHelper.updateOngoingNotification(snap, latestSettings, latestScreenStats)
    }

    private fun handleScreenStateChange(screenOn: Boolean) {
        val now = System.currentTimeMillis()
        val snap = app.batteryRepository.updateFromIntent(null, latestSettings.designCapacityMah)

        if (!snap.isCharging && !wasChargingLastSlice && lastSliceBatteryLevel in 0..100) {
            val deltaLevel = (lastSliceBatteryLevel - snap.levelPercent).coerceAtLeast(0)
            val deltaTime = (now - lastSliceTimestampMs).coerceAtLeast(0L)
            lifecycleScope.launch {
                app.settingsRepository.recordScreenSlice(
                    isScreenOn = isScreenInteractive,
                    deltaPercent = deltaLevel,
                    deltaDurationMs = deltaTime
                )
            }
        }

        isScreenInteractive = screenOn
        lastSliceTimestampMs = now
        lastSliceBatteryLevel = snap.levelPercent
        wasChargingLastSlice = snap.isCharging
    }

    private fun evaluateThresholdsAndAlert(snap: BatterySnapshot, settings: UserSettings) {
        val playSound = settings.alertSoundEnabled

        // 1. Charge limit reached (default 80%)
        if (settings.alertChargeLimitEnabled && snap.isCharging && snap.levelPercent >= settings.alertChargeLimitPercent && snap.levelPercent < 100) {
            if (!firedChargeLimit) {
                firedChargeLimit = true
                app.notificationHelper.sendAlertNotification(
                    alertId = NotificationHelper.ALERT_ID_CHARGE_LIMIT,
                    title = "[ALERT] CHARGE LIMIT REACHED (${snap.levelPercent}%)",
                    body = "Battery reached target limit of ${settings.alertChargeLimitPercent}%. Unplug charger to reduce cell stress.",
                    playSound = playSound
                )
            }
        } else if (!snap.isCharging || snap.levelPercent < settings.alertChargeLimitPercent - 2) {
            firedChargeLimit = false
        }

        // 2. Fully charged (100%)
        if (settings.alertFullChargeEnabled && snap.isCharging && snap.levelPercent == 100) {
            if (!firedFullCharge) {
                firedFullCharge = true
                app.notificationHelper.sendAlertNotification(
                    alertId = NotificationHelper.ALERT_ID_FULL_CHARGE,
                    title = "[ALERT] BATTERY FULLY CHARGED (100%)",
                    body = "Battery is at 100% (${String.format(Locale.US, "%.2fV", snap.voltageMv / 1000f)}). Disconnect power source.",
                    playSound = playSound
                )
            }
        } else if (snap.levelPercent < 98 || !snap.isCharging) {
            firedFullCharge = false
        }

        // 3. Low battery (default 20%)
        if (settings.alertLowBatteryEnabled && !snap.isCharging && snap.levelPercent <= settings.alertLowBatteryPercent && snap.levelPercent > settings.alertCriticalBatteryPercent) {
            if (!firedLowBattery) {
                firedLowBattery = true
                app.notificationHelper.sendAlertNotification(
                    alertId = NotificationHelper.ALERT_ID_LOW_BATTERY,
                    title = "[WARN] LOW BATTERY (${snap.levelPercent}%)",
                    body = "Battery dropped to ${snap.levelPercent}% (threshold ${settings.alertLowBatteryPercent}%).",
                    playSound = playSound
                )
            }
        } else if (snap.isCharging || snap.levelPercent > settings.alertLowBatteryPercent + 2) {
            firedLowBattery = false
        }

        // 4. Critical battery (default 10%)
        if (settings.alertCriticalBatteryEnabled && !snap.isCharging && snap.levelPercent <= settings.alertCriticalBatteryPercent) {
            if (!firedCriticalBattery) {
                firedCriticalBattery = true
                app.notificationHelper.sendAlertNotification(
                    alertId = NotificationHelper.ALERT_ID_CRITICAL_BATTERY,
                    title = "[CRIT] CRITICAL BATTERY (${snap.levelPercent}%)",
                    body = "Battery critically low at ${snap.levelPercent}%. Connect charger immediately.",
                    playSound = playSound
                )
            }
        } else if (snap.isCharging || snap.levelPercent > settings.alertCriticalBatteryPercent + 2) {
            firedCriticalBattery = false
        }

        // 5. High temperature (default 40°C)
        val tempC = snap.temperatureDeciC / 10f
        if (settings.alertHighTempEnabled && tempC >= settings.alertHighTempCelsius) {
            if (!firedHighTemp) {
                firedHighTemp = true
                app.notificationHelper.sendAlertNotification(
                    alertId = NotificationHelper.ALERT_ID_HIGH_TEMP,
                    title = String.format(Locale.US, "[TEMP] HIGH BATTERY TEMP (%.1fC)", tempC),
                    body = String.format(
                        Locale.US,
                        "Battery temperature reached %.1fC (limit %dC). Avoid heavy load or fast charging.",
                        tempC,
                        settings.alertHighTempCelsius
                    ),
                    playSound = playSound
                )
            }
        } else if (tempC < settings.alertHighTempCelsius - 1.5f) {
            firedHighTemp = false
        }

        // 6. Optional charging started/stopped alert
        val prevPlug = lastPluggedState
        if (prevPlug != null && prevPlug != snap.isCharging && settings.alertPlugStateChange) {
            val title = if (snap.isCharging) "[PLUG] CHARGING STARTED (${snap.plugType})" else "[PLUG] CHARGER DISCONNECTED"
            val body = "Level: ${snap.levelPercent}% | Voltage: ${String.format(Locale.US, "%.2fV", snap.voltageMv / 1000f)}"
            app.notificationHelper.sendAlertNotification(
                alertId = NotificationHelper.ALERT_ID_PLUG_STATE,
                title = title,
                body = body,
                playSound = playSound
            )
        }
        lastPluggedState = snap.isCharging
    }

    companion object {
        fun startService(context: Context) {
            runCatching {
                val intent = Intent(context, BatteryMonitorService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stopService(context: Context) {
            runCatching {
                val intent = Intent(context, BatteryMonitorService::class.java)
                context.stopService(intent)
            }
        }
    }
}
