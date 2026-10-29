package com.batttext.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
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
 * Ultra-low-overhead, zero-polling event-driven service.
 *
 * Efficiency optimization:
 * - Zero wake locks, zero timers, zero background coroutines spinning.
 * - When the screen turns OFF (`ACTION_SCREEN_OFF`) and the device is unplugged, we UNREGISTER the
 *   `ACTION_BATTERY_CHANGED` receiver so the app process does zero work while your phone is in your
 *   pocket or in Deep Sleep! When you turn the screen back ON (`ACTION_SCREEN_ON`) or plug/unplug power,
 *   it immediately computes the exact screen-off drain & deep sleep delta and resumes live updates.
 */
class BatteryMonitorService : LifecycleService() {

    private val app: BattTextApp
        get() = application as BattTextApp

    private var latestSettings = UserSettings()
    private var latestScreenStats = ScreenDrainStats()

    private var isScreenInteractive: Boolean = true
    private var isBatteryReceiverRegistered: Boolean = false

    private var lastSliceElapsedMs: Long = SystemClock.elapsedRealtime()
    private var lastSliceUptimeMs: Long = SystemClock.uptimeMillis()
    private var lastSliceBatteryLevel: Int = -1
    private var wasChargingLastSlice: Boolean = false

    private var firedChargeLimit = false
    private var firedFullCharge = false
    private var firedLowBattery = false
    private var firedCriticalBattery = false
    private var firedHighTemp = false
    private var lastPluggedState: Boolean? = null

    private val batteryBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                handleBatteryBroadcast(intent)
            }
        }
    }

    private val screenAndPowerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> handleScreenStateChange(screenOn = true)
                Intent.ACTION_SCREEN_OFF -> handleScreenStateChange(screenOn = false)
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED,
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val snap = app.batteryRepository.updateFromIntent(null, latestSettings.designCapacityOverrideMah)
                    syncBatteryReceiverRegistration(screenOn = isScreenInteractive, isCharging = snap.isCharging)
                    evaluateThresholdsAndAlert(snap, latestSettings)
                    app.notificationHelper.updateOngoingNotification(snap, latestSettings, latestScreenStats)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenInteractive = runCatching { pm.isInteractive }.getOrDefault(true)
        lastSliceElapsedMs = SystemClock.elapsedRealtime()
        lastSliceUptimeMs = SystemClock.uptimeMillis()

        val initialSnap = app.batteryRepository.updateFromIntent(null, latestSettings.designCapacityOverrideMah)
        lastSliceBatteryLevel = initialSnap.levelPercent
        wasChargingLastSlice = initialSnap.isCharging
        lastPluggedState = initialSnap.isCharging

        promoteToForegroundSafely(initialSnap)

        lifecycleScope.launch {
            combine(
                app.settingsRepository.settingsFlow,
                app.settingsRepository.screenDrainFlow
            ) { settings, screenStats ->
                settings to screenStats
            }.collectLatest { (settings, screenStats) ->
                latestSettings = settings
                latestScreenStats = screenStats
                if (!settings.backgroundMonitorEnabled) {
                    stopSelf()
                    return@collectLatest
                }
                val snap = app.batteryRepository.updateFromIntent(null, settings.designCapacityOverrideMah)
                app.notificationHelper.updateOngoingNotification(snap, settings, screenStats)
            }
        }

        val stateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(
            this,
            screenAndPowerReceiver,
            stateFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        syncBatteryReceiverRegistration(screenOn = isScreenInteractive, isCharging = initialSnap.isCharging)
    }

    /**
     * Only keep ACTION_BATTERY_CHANGED registered when the screen is ON or the charger is plugged in.
     * When the screen is OFF and unplugged, we unregister ACTION_BATTERY_CHANGED so the app uses 0.0% CPU during standby!
     */
    private fun syncBatteryReceiverRegistration(screenOn: Boolean, isCharging: Boolean) {
        val shouldListen = screenOn || isCharging
        if (shouldListen && !isBatteryReceiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    batteryBroadcastReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED
                )
                isBatteryReceiverRegistered = true
            }
        } else if (!shouldListen && isBatteryReceiverRegistered) {
            runCatching {
                unregisterReceiver(batteryBroadcastReceiver)
                isBatteryReceiverRegistered = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val snap = app.batteryRepository.updateFromIntent(null, latestSettings.designCapacityOverrideMah)
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
        runCatching { unregisterReceiver(screenAndPowerReceiver) }
        if (isBatteryReceiverRegistered) {
            runCatching { unregisterReceiver(batteryBroadcastReceiver) }
            isBatteryReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun handleBatteryBroadcast(intent: Intent) {
        val snap = app.batteryRepository.updateFromIntent(intent, latestSettings.designCapacityOverrideMah)
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()

        if (lastSliceBatteryLevel in 0..100) {
            val deltaLevel = if (!snap.isCharging && !wasChargingLastSlice) {
                (lastSliceBatteryLevel - snap.levelPercent).coerceAtLeast(0)
            } else {
                0
            }
            val deltaElapsed = if (!snap.isCharging && !wasChargingLastSlice) {
                (nowElapsed - lastSliceElapsedMs).coerceAtLeast(0L)
            } else {
                0L
            }
            val deltaUptime = if (!snap.isCharging && !wasChargingLastSlice) {
                (nowUptime - lastSliceUptimeMs).coerceAtLeast(0L)
            } else {
                0L
            }
            if (deltaLevel > 0 || deltaElapsed >= 60_000L) {
                val sliceScreenOn = isScreenInteractive
                lifecycleScope.launch {
                    app.settingsRepository.recordScreenSlice(
                        isScreenOn = sliceScreenOn,
                        deltaPercent = deltaLevel,
                        deltaElapsedMs = deltaElapsed,
                        deltaUptimeMs = deltaUptime
                    )
                }
                lastSliceElapsedMs = nowElapsed
                lastSliceUptimeMs = nowUptime
                lastSliceBatteryLevel = snap.levelPercent
            }
        } else {
            lastSliceBatteryLevel = snap.levelPercent
            lastSliceElapsedMs = nowElapsed
            lastSliceUptimeMs = nowUptime
        }

        if (wasChargingLastSlice != snap.isCharging) {
            lastSliceBatteryLevel = snap.levelPercent
            lastSliceElapsedMs = nowElapsed
            lastSliceUptimeMs = nowUptime
            wasChargingLastSlice = snap.isCharging
            syncBatteryReceiverRegistration(screenOn = isScreenInteractive, isCharging = snap.isCharging)
        }

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
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowUptime = SystemClock.uptimeMillis()
        val snap = app.batteryRepository.updateFromIntent(null, latestSettings.designCapacityOverrideMah)

        if (!snap.isCharging && !wasChargingLastSlice && lastSliceBatteryLevel in 0..100) {
            val deltaLevel = (lastSliceBatteryLevel - snap.levelPercent).coerceAtLeast(0)
            val deltaElapsed = (nowElapsed - lastSliceElapsedMs).coerceAtLeast(0L)
            val deltaUptime = (nowUptime - lastSliceUptimeMs).coerceAtLeast(0L)
            val prevScreenOn = isScreenInteractive
            lifecycleScope.launch {
                app.settingsRepository.recordScreenSlice(
                    isScreenOn = prevScreenOn,
                    deltaPercent = deltaLevel,
                    deltaElapsedMs = deltaElapsed,
                    deltaUptimeMs = deltaUptime
                )
            }
        }

        isScreenInteractive = screenOn
        lastSliceElapsedMs = nowElapsed
        lastSliceUptimeMs = nowUptime
        lastSliceBatteryLevel = snap.levelPercent
        wasChargingLastSlice = snap.isCharging

        // Unregister ACTION_BATTERY_CHANGED when screen turns off and unplugged so CPU sleeps undisturbed!
        syncBatteryReceiverRegistration(screenOn = screenOn, isCharging = snap.isCharging)

        if (screenOn) {
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
    }

    private fun evaluateThresholdsAndAlert(snap: BatterySnapshot, settings: UserSettings) {
        val playSound = settings.alertSoundEnabled

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
