package com.batttext.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.batttext.app.data.prefs.ScreenDrainStats
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.data.repository.BatterySnapshot
import com.batttext.app.ui.components.TextButtonBracket
import com.batttext.app.ui.components.TextDivider
import com.batttext.app.ui.components.TextFramedBox
import com.batttext.app.ui.components.TextKeyValueRow
import com.batttext.app.ui.components.TextProgressBar
import com.batttext.app.ui.theme.BattTextTheme
import java.util.Locale

@Composable
fun DashboardScreen(
    snapshot: BatterySnapshot,
    settings: UserSettings,
    screenStats: ScreenDrainStats,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptExemption: () -> Unit,
    onResetScreenStats: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!hasNotificationPermission || !isIgnoringBatteryOptimizations) {
            SystemStatusBanner(
                hasNotificationPermission = hasNotificationPermission,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestBatteryOptExemption = onRequestBatteryOptExemption
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LiveBatteryBox(snapshot = snapshot, settings = settings)
                    Spacer(modifier = Modifier.height(6.dp))
                    CapacityBox(snapshot = snapshot, settings = settings)
                }
                Column(modifier = Modifier.weight(1f)) {
                    RateAndEtaBox(snapshot = snapshot)
                    Spacer(modifier = Modifier.height(6.dp))
                    DeepSleepAndDozeBox(
                        snapshot = snapshot,
                        screenStats = screenStats,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        onRequestBatteryOptExemption = onRequestBatteryOptExemption,
                        onResetScreenStats = onResetScreenStats
                    )
                }
            }
        } else {
            LiveBatteryBox(snapshot = snapshot, settings = settings)
            Spacer(modifier = Modifier.height(6.dp))
            RateAndEtaBox(snapshot = snapshot)
            Spacer(modifier = Modifier.height(6.dp))
            DeepSleepAndDozeBox(
                snapshot = snapshot,
                screenStats = screenStats,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                onRequestBatteryOptExemption = onRequestBatteryOptExemption,
                onResetScreenStats = onResetScreenStats
            )
            Spacer(modifier = Modifier.height(6.dp))
            CapacityBox(snapshot = snapshot, settings = settings)
        }
    }
}

@Composable
private fun SystemStatusBanner(
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptExemption: () -> Unit
) {
    val colors = BattTextTheme.colors
    TextFramedBox(
        title = "SYSTEM PERMISSIONS",
        rightHeaderTag = "ACTION REQ",
        borderColor = colors.warning,
        titleColor = colors.warning
    ) {
        if (!hasNotificationPermission) {
            TextKeyValueRow(
                label = "NOTIF PERM",
                value = "REQUIRED FOR LIVE BAR",
                valueColor = colors.warning,
                trailingContent = {
                    TextButtonBracket(
                        text = "GRANT",
                        onClick = onRequestNotificationPermission,
                        color = colors.link
                    )
                }
            )
        }
        if (!isIgnoringBatteryOptimizations) {
            TextKeyValueRow(
                label = "DOZE EXEMPT",
                value = "KEEP TELEMETRY ALIVE",
                valueColor = colors.warning,
                trailingContent = {
                    TextButtonBracket(
                        text = "EXEMPT",
                        onClick = onRequestBatteryOptExemption,
                        color = colors.link
                    )
                }
            )
        }
    }
}

@Composable
private fun LiveBatteryBox(
    snapshot: BatterySnapshot,
    settings: UserSettings
) {
    val colors = BattTextTheme.colors
    val levelColor = when {
        snapshot.levelPercent <= settings.alertCriticalBatteryPercent -> colors.critical
        snapshot.levelPercent <= settings.alertLowBatteryPercent -> colors.warning
        else -> colors.good
    }

    val statusFormatted = if (snapshot.plugType != "NONE") {
        "${snapshot.statusText} (${snapshot.plugType})"
    } else {
        snapshot.statusText
    }

    val currentSign = if (snapshot.currentMa > 0) "+" else ""
    val currentColor = when {
        snapshot.currentMa > 0 -> colors.good
        snapshot.currentMa < -1000 -> colors.warning
        else -> colors.foreground
    }

    val tempC = snapshot.temperatureDeciC / 10f
    val tempColor = when {
        tempC >= settings.alertHighTempCelsius -> colors.critical
        tempC >= settings.alertHighTempCelsius - 3 -> colors.warning
        else -> colors.foreground
    }
    val tempStr = if (snapshot.temperatureDeciC <= 0) {
        "N/A"
    } else if (settings.useFahrenheit) {
        String.format(Locale.US, "%.1f F", tempC * 9f / 5f + 32f)
    } else {
        String.format(Locale.US, "%.1f C", tempC)
    }

    val voltStr = if (snapshot.voltageMv > 0) {
        String.format(Locale.US, "%.3f V (%d mV)", snapshot.voltageMv / 1000f, snapshot.voltageMv)
    } else {
        "N/A"
    }

    TextFramedBox(
        title = "BATTERY",
        rightHeaderTag = snapshot.technology
    ) {
        TextKeyValueRow(
            label = "LEVEL",
            value = String.format(Locale.US, "%3d%%", snapshot.levelPercent),
            valueColor = levelColor,
            trailingContent = {
                TextProgressBar(
                    fraction = snapshot.levelPercent / 100f,
                    segments = 12,
                    fillColor = levelColor
                )
            }
        )
        TextKeyValueRow(
            label = "STATUS",
            value = statusFormatted,
            valueColor = if (snapshot.isCharging) colors.good else colors.foreground
        )
        TextKeyValueRow(
            label = "CURRENT",
            value = "${currentSign}${snapshot.currentMa} mA  (AVG ${if (snapshot.avgCurrentMa > 0) "+" else ""}${snapshot.avgCurrentMa} mA)",
            valueColor = currentColor
        )
        TextKeyValueRow(
            label = "POWER",
            value = String.format(Locale.US, "%.2f W", snapshot.powerWatts),
            valueColor = colors.foreground
        )
        TextKeyValueRow(
            label = "VOLTAGE",
            value = voltStr,
            valueColor = colors.foreground
        )
        TextKeyValueRow(
            label = "TEMP",
            value = tempStr,
            valueColor = tempColor
        )
        TextKeyValueRow(
            label = "HEALTH",
            value = snapshot.healthText,
            valueColor = if (snapshot.healthText == "GOOD") colors.good else colors.warning
        )
    }
}

@Composable
private fun RateAndEtaBox(snapshot: BatterySnapshot) {
    val colors = BattTextTheme.colors
    val etaLabel = if (snapshot.isCharging) "FULL IN" else "EMPTY IN"
    val etaValue = snapshot.etaMinutes?.let { mins ->
        val h = mins / 60
        val m = mins % 60
        String.format(Locale.US, "%dh %02dm", h, m)
    } ?: "CALCULATING..."

    val ratePrefix = if (snapshot.isCharging) "+" else "-"
    val rateStr = if (snapshot.speedPercentPerHour > 0f) {
        String.format(Locale.US, "%s%.1f %%/hr", ratePrefix, snapshot.speedPercentPerHour)
    } else {
        "0.0 %/hr"
    }

    TextFramedBox(
        title = "SPEED & ESTIMATE",
        rightHeaderTag = if (snapshot.isScreenOn) "SCR:ON" else "SCR:OFF"
    ) {
        TextKeyValueRow(
            label = etaLabel,
            value = etaValue,
            valueColor = colors.accent
        )
        TextKeyValueRow(
            label = "SPEED",
            value = rateStr,
            valueColor = if (snapshot.isCharging) colors.good else colors.foreground
        )
    }
}

/**
 * Combined Screen On/Off Drain + Deep Sleep & Doze Accounting Box (like BatteryGuru).
 * Shows both system-wide Deep Sleep since boot and tracked Screen-Off Deep Sleep vs Awake (Wakelock) ratio.
 */
@Composable
private fun DeepSleepAndDozeBox(
    snapshot: BatterySnapshot,
    screenStats: ScreenDrainStats,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestBatteryOptExemption: () -> Unit,
    onResetScreenStats: () -> Unit
) {
    val colors = BattTextTheme.colors

    // 1. System-wide Deep Sleep since boot (works immediately on app launch!)
    val bootTotalMs = snapshot.bootElapsedRealtimeMs.coerceAtLeast(1L)
    val bootDeepPct = (snapshot.bootDeepSleepMs * 100f) / bootTotalMs.toFloat()
    val bootAwakePct = (snapshot.bootAwakeUptimeMs * 100f) / bootTotalMs.toFloat()

    // 2. Tracked Screen-Off Deep Sleep vs Awake Idle (Wakelock)
    val trackedOffTotalMs = (screenStats.deepSleepDurationMs + screenStats.awakeIdleDurationMs)
    val trackedDeepPct = if (trackedOffTotalMs > 0L) {
        (screenStats.deepSleepDurationMs * 100f) / trackedOffTotalMs.toFloat()
    } else 0f
    val trackedAwakePct = if (trackedOffTotalMs > 0L) {
        (screenStats.awakeIdleDurationMs * 100f) / trackedOffTotalMs.toFloat()
    } else 0f

    val onHours = screenStats.screenOnDurationMs / 3_600_000f
    val offHours = screenStats.screenOffDurationMs / 3_600_000f
    val onRate = if (onHours >= 0.05f) String.format(Locale.US, "%.1f %%/h", screenStats.screenOnDrainPercent / onHours) else "-- %/h"
    val offRate = if (offHours >= 0.05f) String.format(Locale.US, "%.1f %%/h", screenStats.screenOffDrainPercent / offHours) else "-- %/h"

    val dozeStateStr = when {
        snapshot.isDeviceIdleDoze -> "DOZING (IDLE)"
        !snapshot.isScreenOn -> "LIGHT IDLE (SCR OFF)"
        else -> "ACTIVE (SCR ON)"
    }
    val saverTag = if (snapshot.isPowerSaveMode) "SAVER:ON" else "DOZE & SLEEP"

    TextFramedBox(
        title = "DEEP SLEEP, DOZE & DRAIN",
        rightHeaderTag = saverTag
    ) {
        TextKeyValueRow(
            label = "DOZE STATE",
            value = dozeStateStr,
            valueColor = if (snapshot.isDeviceIdleDoze) colors.good else colors.foreground,
            trailingContent = if (!isIgnoringBatteryOptimizations) {
                {
                    TextButtonBracket(
                        text = "WHITELIST",
                        onClick = onRequestBatteryOptExemption,
                        color = colors.link
                    )
                }
            } else null
        )
        TextKeyValueRow(
            label = "BOOT SLEEP",
            value = String.format(
                Locale.US,
                "%.1f%% (%s)",
                bootDeepPct,
                formatDurationShort(snapshot.bootDeepSleepMs)
            ),
            valueColor = colors.good,
            trailingContent = {
                TextProgressBar(
                    fraction = bootDeepPct / 100f,
                    segments = 10,
                    fillColor = colors.good
                )
            }
        )
        TextKeyValueRow(
            label = "BOOT AWAKE",
            value = String.format(
                Locale.US,
                "%.1f%% (%s) | UP %s",
                bootAwakePct,
                formatDurationShort(snapshot.bootAwakeUptimeMs),
                formatDurationShort(snapshot.bootElapsedRealtimeMs)
            ),
            valueColor = colors.foreground
        )

        TextDivider()

        TextKeyValueRow(
            label = "SCREEN ON",
            value = String.format(
                Locale.US,
                "%.1f%%  (%s | %s)",
                screenStats.screenOnDrainPercent,
                formatDurationShort(screenStats.screenOnDurationMs),
                onRate
            ),
            valueColor = colors.warning
        )
        TextKeyValueRow(
            label = "SCREEN OFF",
            value = String.format(
                Locale.US,
                "%.1f%%  (%s | %s)",
                screenStats.screenOffDrainPercent,
                formatDurationShort(screenStats.screenOffDurationMs),
                offRate
            ),
            valueColor = colors.good
        )
        TextKeyValueRow(
            label = "OFF SLEEP",
            value = if (trackedOffTotalMs > 0L) {
                String.format(
                    Locale.US,
                    "%.1f%% (%s) | WAKELOCK %.1f%% (%s)",
                    trackedDeepPct,
                    formatDurationShort(screenStats.deepSleepDurationMs),
                    trackedAwakePct,
                    formatDurationShort(screenStats.awakeIdleDurationMs)
                )
            } else {
                "WAITING FOR SCREEN-OFF STANDBY..."
            },
            valueColor = colors.accent,
            trailingContent = {
                TextButtonBracket(
                    text = "RESET",
                    onClick = onResetScreenStats,
                    color = colors.link
                )
            }
        )
    }
}

@Composable
private fun CapacityBox(
    snapshot: BatterySnapshot,
    settings: UserSettings
) {
    val colors = BattTextTheme.colors
    val counterStr = snapshot.chargeCounterMah?.let { "$it mAh" } ?: "N/A"
    val estCapStr = snapshot.estimatedFullCapacityMah?.let { est ->
        val healthPct = ((est.toFloat() / settings.designCapacityMah.toFloat()) * 100f).toInt()
        "$est mAh ($healthPct% OF DESIGN)"
    } ?: "N/A (NEEDS >=15% & COUNTER)"

    TextFramedBox(
        title = "CAPACITY",
        rightHeaderTag = "DESIGN ${settings.designCapacityMah}mAh"
    ) {
        TextKeyValueRow(
            label = "CHG COUNTER",
            value = counterStr,
            valueColor = colors.foreground
        )
        TextKeyValueRow(
            label = "EST FULL CAP",
            value = estCapStr,
            valueColor = colors.accent
        )
        TextKeyValueRow(
            label = "DESIGN CAP",
            value = "${settings.designCapacityMah} mAh (EDIT IN SETTINGS)",
            valueColor = colors.muted
        )
    }
}

private fun formatDurationShort(ms: Long): String {
    val totalMinutes = (ms / 60_000L).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return String.format(Locale.US, "%dh%02dm", h, m)
}
