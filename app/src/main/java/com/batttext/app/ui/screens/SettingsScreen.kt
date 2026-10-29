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
    settings: UserSettings,
    settingsRepository: SettingsRepository,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onCycleThemeMode: () -> Unit,
    onCycleFontMode: () -> Unit,
    onCycleNotificationStyle: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptExemption: () -> Unit,
    onClearHistory: () -> Unit
) {
    val colors = BattTextTheme.colors
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextFramedBox(
            title = "DISPLAY & TYPOGRAPHY",
            rightHeaderTag = "DRACULA / ALUCARD"
        ) {
            TextStepperRow(
                label = "THEME MODE",
                valueText = settings.themeMode.name,
                onDecrement = onCycleThemeMode,
                onIncrement = onCycleThemeMode,
                subtitle = "AUTO follows system; DARK = Dracula, LIGHT = Alucard"
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

        TextFramedBox(
            title = "SERVICE & TELEMETRY",
            rightHeaderTag = "API 34 FGS"
        ) {
            TextStepperRow(
                label = "DESIGN CAPACITY",
                valueText = "${settings.designCapacityMah}mAh",
                onDecrement = {
                    scope.launch { settingsRepository.setDesignCapacityMah(settings.designCapacityMah - 100) }
                },
                onIncrement = {
                    scope.launch { settingsRepository.setDesignCapacityMah(settings.designCapacityMah + 100) }
                },
                subtitle = "Nominal factory battery capacity in mAh"
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
                subtitle = "How often Room records a history point"
            )
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
            TextStepperRow(
                label = "NOTIF EXPANDED STYLE",
                valueText = settings.notificationStyle.name,
                onDecrement = onCycleNotificationStyle,
                onIncrement = onCycleNotificationStyle,
                subtitle = "COMPACT vs DETAILED (includes capacity & screen drain)"
            )
            TextDivider()
            TextToggleRow(
                label = "DYNAMIC DIGIT STATUS ICON",
                checked = settings.dynamicPercentIcon,
                onToggle = { scope.launch { settingsRepository.setDynamicPercentIcon(it) } },
                subtitle = "Draw battery % as plain digits in status bar"
            )
            TextDivider()
            TextToggleRow(
                label = "START SERVICE ON BOOT",
                checked = settings.startOnBoot,
                onToggle = { scope.launch { settingsRepository.setStartOnBoot(it) } },
                subtitle = "Restart foreground monitor on BOOT_COMPLETED"
            )
            TextDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButtonBracket(
                    text = if (hasNotificationPermission) "NOTIF: GRANTED" else "GRANT NOTIF PERM",
                    onClick = onRequestNotificationPermission,
                    color = if (hasNotificationPermission) colors.good else colors.warning
                )
                TextButtonBracket(
                    text = if (isIgnoringBatteryOptimizations) "DOZE: EXEMPT" else "EXEMPT DOZE",
                    onClick = onRequestBatteryOptExemption,
                    color = if (isIgnoringBatteryOptimizations) colors.good else colors.warning
                )
            }
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
