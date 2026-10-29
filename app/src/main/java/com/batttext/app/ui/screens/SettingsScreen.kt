package com.batttext.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.batttext.app.data.prefs.SettingsRepository
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.data.repository.BatterySnapshot
import com.batttext.app.ui.components.TextButtonBracket
import com.batttext.app.ui.components.TextDivider
import com.batttext.app.ui.components.TextFramedBox
import com.batttext.app.ui.components.TextModalDialog
import com.batttext.app.ui.components.TextStepperRow
import com.batttext.app.ui.components.TextToggleRow
import com.batttext.app.ui.theme.BattTextTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    snapshot: BatterySnapshot,
    settings: UserSettings,
    settingsRepository: SettingsRepository,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onToggleBackgroundMonitor: (Boolean) -> Unit,
    onCycleThemeMode: () -> Unit,
    onCycleFontMode: () -> Unit,
    onCycleStatusBarIconMetric: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptExemption: () -> Unit,
    onRequestShizukuOrRescan: () -> Unit,
    onClearHistory: () -> Unit
) {
    val colors = BattTextTheme.colors
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    val autoCap = snapshot.hardwareSpecs.autoDesignCapacityMah
    val capDisplay = if (settings.designCapacityOverrideMah <= 0) {
        "AUTO (${autoCap})"
    } else {
        "${settings.designCapacityOverrideMah}mAh"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Notification & Status Bar Icon Customization
        TextFramedBox(
            title = "NOTIFICATION & ICON CUSTOMIZATION",
            rightHeaderTag = "MINIMAL"
        ) {
            TextStepperRow(
                label = "STATUS BAR ICON VALUE",
                valueText = settings.statusBarIconMetric.label,
                onDecrement = { onCycleStatusBarIconMetric(false) },
                onIncrement = { onCycleStatusBarIconMetric(true) },
                subtitle = "Choose LEVEL %, TEMP, CURRENT, WATTS, or STATIC"
            )
            TextDivider()
            TextToggleRow(
                label = "SHOW BATTERY %",
                checked = settings.notifShowLevel,
                onToggle = { scope.launch { settingsRepository.setNotifShowLevel(it) } }
            )
            TextToggleRow(
                label = "SHOW LIVE CURRENT (mA)",
                checked = settings.notifShowCurrent,
                onToggle = { scope.launch { settingsRepository.setNotifShowCurrent(it) } }
            )
            TextToggleRow(
                label = "SHOW LIVE POWER (W)",
                checked = settings.notifShowPower,
                onToggle = { scope.launch { settingsRepository.setNotifShowPower(it) } }
            )
            TextToggleRow(
                label = "SHOW TEMPERATURE",
                checked = settings.notifShowTemp,
                onToggle = { scope.launch { settingsRepository.setNotifShowTemp(it) } }
            )
            TextToggleRow(
                label = "SHOW VOLTAGE (V)",
                checked = settings.notifShowVoltage,
                onToggle = { scope.launch { settingsRepository.setNotifShowVoltage(it) } }
            )
            TextToggleRow(
                label = "SHOW TIME REMAINING (ETA)",
                checked = settings.notifShowEta,
                onToggle = { scope.launch { settingsRepository.setNotifShowEta(it) } }
            )
            TextDivider()
            TextToggleRow(
                label = "SHOW ACTIVE DRAIN / HR",
                checked = settings.notifShowActiveRate,
                onToggle = { scope.launch { settingsRepository.setNotifShowActiveRate(it) } }
            )
            TextToggleRow(
                label = "SHOW IDLE DRAIN / HR",
                checked = settings.notifShowIdleRate,
                onToggle = { scope.launch { settingsRepository.setNotifShowIdleRate(it) } }
            )
            TextToggleRow(
                label = "SHOW AWAKE %",
                checked = settings.notifShowAwakePct,
                onToggle = { scope.launch { settingsRepository.setNotifShowAwakePct(it) } }
            )
            TextToggleRow(
                label = "SHOW DEEP SLEEP %",
                checked = settings.notifShowDeepSleepPct,
                onToggle = { scope.launch { settingsRepository.setNotifShowDeepSleepPct(it) } }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Efficiency & Background Service
        TextFramedBox(
            title = "EFFICIENCY & BACKGROUND SERVICE",
            rightHeaderTag = "ZERO-POLL"
        ) {
            TextToggleRow(
                label = "BACKGROUND MONITOR & NOTIFICATION",
                checked = settings.backgroundMonitorEnabled,
                onToggle = onToggleBackgroundMonitor,
                subtitle = "Event-driven only (0% CPU during screen-off standby)"
            )
            TextDivider()
            TextToggleRow(
                label = "START SERVICE ON BOOT",
                checked = settings.startOnBoot,
                onToggle = { scope.launch { settingsRepository.setStartOnBoot(it) } },
                subtitle = "Restart monitor automatically after reboot"
            )
            TextDivider()
            TextStepperRow(
                label = "SAMPLE INTERVAL",
                valueText = "${settings.sampleIntervalMinutes} MIN",
                onDecrement = {
                    scope.launch { settingsRepository.setSampleIntervalMinutes(settings.sampleIntervalMinutes - 1) }
                },
                onIncrement = {
                    scope.launch { settingsRepository.setSampleIntervalMinutes(settings.sampleIntervalMinutes + 1) }
                },
                subtitle = "Minimum minutes between Room history samples"
            )
            TextDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButtonBracket(
                    text = if (hasNotificationPermission) "NOTIF: GRANTED" else "GRANT NOTIF",
                    onClick = onRequestNotificationPermission,
                    color = if (hasNotificationPermission) colors.good else colors.warning
                )
                TextButtonBracket(
                    text = if (isIgnoringBatteryOptimizations) "DOZE: EXEMPT" else "EXEMPT DOZE",
                    onClick = onRequestBatteryOptExemption,
                    color = if (isIgnoringBatteryOptimizations) colors.good else colors.warning
                )
                TextButtonBracket(
                    text = if (snapshot.hardwareSpecs.shizukuGranted) "SHIZUKU: OK" else "SHIZUKU",
                    onClick = onRequestShizukuOrRescan,
                    color = if (snapshot.hardwareSpecs.shizukuGranted) colors.good else colors.link
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Display & Typography
        TextFramedBox(
            title = "DISPLAY & TYPOGRAPHY",
            rightHeaderTag = "DRACULA / ALUCARD"
        ) {
            TextStepperRow(
                label = "THEME MODE",
                valueText = settings.themeMode.name,
                onDecrement = onCycleThemeMode,
                onIncrement = onCycleThemeMode,
                subtitle = "AUTO / DARK (Dracula) / AMOLED (Pure Black) / LIGHT (Alucard)"
            )
            TextDivider()
            TextStepperRow(
                label = "FONT FAMILY",
                valueText = settings.fontMode.name,
                onDecrement = onCycleFontMode,
                onIncrement = onCycleFontMode,
                subtitle = "SYSTEM uses FontFamily.Default; MONOSPACE uses system mono"
            )
            TextDivider()
            TextToggleRow(
                label = "PURE ASCII BORDERS",
                checked = settings.pureAsciiBorders,
                onToggle = { scope.launch { settingsRepository.setPureAsciiBorders(it) } },
                subtitle = "Use +-|=# instead of Unicode box-drawing characters"
            )
            TextDivider()
            TextToggleRow(
                label = "TEMPERATURE IN FAHRENHEIT",
                checked = settings.useFahrenheit,
                onToggle = { scope.launch { settingsRepository.setUseFahrenheit(it) } },
                subtitle = "Toggle between Celsius (C) and Fahrenheit (F)"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Alert Thresholds
        TextFramedBox(
            title = "ALERT THRESHOLDS",
            rightHeaderTag = "ONE-SHOT EVENTS"
        ) {
            TextToggleRow(
                label = "ALERT SOUND",
                checked = settings.alertSoundEnabled,
                onToggle = { scope.launch { settingsRepository.setAlertSoundEnabled(it) } },
                subtitle = "Play default notification sound when an alert fires"
            )
            TextDivider()
            TextToggleRow(
                label = "CHARGE LIMIT ALERT",
                checked = settings.alertChargeLimitEnabled,
                onToggle = { scope.launch { settingsRepository.setAlertChargeLimitEnabled(it) } }
            )
            if (settings.alertChargeLimitEnabled) {
                TextStepperRow(
                    label = "  LIMIT TARGET %",
                    valueText = "${settings.alertChargeLimitPercent}%",
                    onDecrement = {
                        scope.launch { settingsRepository.setAlertChargeLimitPercent(settings.alertChargeLimitPercent - 5) }
                    },
                    onIncrement = {
                        scope.launch { settingsRepository.setAlertChargeLimitPercent(settings.alertChargeLimitPercent + 5) }
                    }
                )
            }
            TextDivider()
            TextToggleRow(
                label = "FULL CHARGE ALERT (100%)",
                checked = settings.alertFullChargeEnabled,
                onToggle = { scope.launch { settingsRepository.setAlertFullChargeEnabled(it) } }
            )
            TextDivider()
            TextToggleRow(
                label = "LOW BATTERY ALERT",
                checked = settings.alertLowBatteryEnabled,
                onToggle = { scope.launch { settingsRepository.setAlertLowBatteryEnabled(it) } }
            )
            if (settings.alertLowBatteryEnabled) {
                TextStepperRow(
                    label = "  LOW THRESHOLD %",
                    valueText = "${settings.alertLowBatteryPercent}%",
                    onDecrement = {
                        scope.launch { settingsRepository.setAlertLowBatteryPercent(settings.alertLowBatteryPercent - 5) }
                    },
                    onIncrement = {
                        scope.launch { settingsRepository.setAlertLowBatteryPercent(settings.alertLowBatteryPercent + 5) }
                    }
                )
            }
            TextDivider()
            TextToggleRow(
                label = "CRITICAL BATTERY ALERT",
                checked = settings.alertCriticalBatteryEnabled,
                onToggle = { scope.launch { settingsRepository.setAlertCriticalBatteryEnabled(it) } }
            )
            if (settings.alertCriticalBatteryEnabled) {
                TextStepperRow(
                    label = "  CRITICAL %",
                    valueText = "${settings.alertCriticalBatteryPercent}%",
                    onDecrement = {
                        scope.launch { settingsRepository.setAlertCriticalBatteryPercent(settings.alertCriticalBatteryPercent - 2) }
                    },
                    onIncrement = {
                        scope.launch { settingsRepository.setAlertCriticalBatteryPercent(settings.alertCriticalBatteryPercent + 2) }
                    }
                )
            }
            TextDivider()
            TextToggleRow(
                label = "HIGH TEMPERATURE ALERT",
                checked = settings.alertHighTempEnabled,
                onToggle = { scope.launch { settingsRepository.setAlertHighTempEnabled(it) } }
            )
            if (settings.alertHighTempEnabled) {
                TextStepperRow(
                    label = "  HIGH TEMP LIMIT",
                    valueText = "${settings.alertHighTempCelsius} C",
                    onDecrement = {
                        scope.launch { settingsRepository.setAlertHighTempCelsius(settings.alertHighTempCelsius - 1) }
                    },
                    onIncrement = {
                        scope.launch { settingsRepository.setAlertHighTempCelsius(settings.alertHighTempCelsius + 1) }
                    }
                )
            }
            TextDivider()
            TextToggleRow(
                label = "PLUG / UNPLUG ALERT",
                checked = settings.alertPlugStateChange,
                onToggle = { scope.launch { settingsRepository.setAlertPlugStateChange(it) } },
                subtitle = "Notify when charger is connected or disconnected"
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5. Capacity & Storage
        TextFramedBox(
            title = "CAPACITY & STORAGE",
            rightHeaderTag = "AUTO / SHIZUKU"
        ) {
            TextStepperRow(
                label = "DESIGN CAPACITY",
                valueText = capDisplay,
                onDecrement = {
                    val base = if (settings.designCapacityOverrideMah <= 0) autoCap else settings.designCapacityOverrideMah
                    scope.launch { settingsRepository.setDesignCapacityOverrideMah(base - 100) }
                },
                onIncrement = {
                    val base = if (settings.designCapacityOverrideMah <= 0) autoCap else settings.designCapacityOverrideMah
                    scope.launch { settingsRepository.setDesignCapacityOverrideMah(base + 100) }
                },
                subtitle = "Auto-detected from hardware; tap [AUTO] below to reset"
            )
            if (settings.designCapacityOverrideMah > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButtonBracket(
                        text = "USE AUTO CAPACITY (${autoCap}mAh)",
                        onClick = { scope.launch { settingsRepository.setDesignCapacityOverrideMah(0) } },
                        color = colors.good
                    )
                }
            }
            TextDivider()
            TextStepperRow(
                label = "HISTORY RETENTION",
                valueText = "${settings.retentionDays} DAYS",
                onDecrement = {
                    scope.launch { settingsRepository.setRetentionDays(settings.retentionDays - 1) }
                },
                onIncrement = {
                    scope.launch { settingsRepository.setRetentionDays(settings.retentionDays + 1) }
                },
                subtitle = "Automatically prune samples older than N days"
            )
            TextDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButtonBracket(
                    text = "PURGE ALL ROOM HISTORY",
                    onClick = { showClearConfirm = true },
                    color = colors.critical
                )
            }
        }
    }

    if (showClearConfirm) {
        TextModalDialog(
            title = "CONFIRM DATABASE PURGE",
            bodyLines = listOf(
                "Delete all recorded battery samples and charge/discharge sessions from Room?",
                "This operation cannot be undone."
            ),
            confirmLabel = "CONFIRM PURGE",
            onConfirm = {
                onClearHistory()
                showClearConfirm = false
            },
            dismissLabel = "CANCEL",
            onDismiss = { showClearConfirm = false }
        )
    }
}
