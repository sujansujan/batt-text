package com.batttext.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.batttext.app.data.db.BatterySessionEntity
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.ui.components.TextFramedBox
import com.batttext.app.ui.components.TextKeyValueRow
import com.batttext.app.ui.components.TextTable
import com.batttext.app.ui.components.TextTableColumn
import com.batttext.app.ui.theme.BattTextTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun SessionsScreen(
    sessions: List<BatterySessionEntity>,
    settings: UserSettings
) {
    val colors = BattTextTheme.colors
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }

    val chargeSessions = remember(sessions) { sessions.filter { it.isCharging } }
    val dischargeSessions = remember(sessions) { sessions.filter { !it.isCharging } }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextFramedBox(
            title = "SESSION SUMMARY",
            rightHeaderTag = "TOTAL ${sessions.size}"
        ) {
            val avgChargeMa = chargeSessions.map { it.avgCurrentMa }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
            val avgDischargeMa = dischargeSessions.map { it.avgCurrentMa }.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
            val maxRecordedTempDeciC = sessions.maxOfOrNull { it.maxTempDeciC } ?: 0
            val peakTempStr = if (maxRecordedTempDeciC <= 0) "N/A" else {
                val c = maxRecordedTempDeciC / 10f
                if (settings.useFahrenheit) String.format(Locale.US, "%.1f F", c * 9f / 5f + 32f)
                else String.format(Locale.US, "%.1f C", c)
            }

            TextKeyValueRow(
                label = "CHG SESSIONS",
                value = "${chargeSessions.size}  (AVG +${abs(avgChargeMa)} mA)",
                valueColor = colors.good
            )
            TextKeyValueRow(
                label = "DSC SESSIONS",
                value = "${dischargeSessions.size}  (AVG -${abs(avgDischargeMa)} mA)",
                valueColor = colors.foreground
            )
            TextKeyValueRow(
                label = "PEAK SESSION T",
                value = peakTempStr,
                valueColor = colors.warning
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val rows = remember(sessions, settings.useFahrenheit) {
            sessions.map { s ->
                val typeStr = if (s.isCharging) {
                    if (s.isActive) "*CHG(${s.plugType})" else "CHG(${s.plugType})"
                } else {
                    if (s.isActive) "*DSC" else "DSC"
                }
                val typeColor = if (s.isCharging) colors.good else colors.accent
                val timeWindow = sdf.format(Date(s.startTimeMillis))
                val deltaSign = if (s.endLevel >= s.startLevel) "+" else ""
                val spanStr = "${s.startLevel}->${s.endLevel}% (${deltaSign}${s.endLevel - s.startLevel}%)"
                val curStr = "${if (s.avgCurrentMa > 0) "+" else ""}${s.avgCurrentMa}mA"
                val c = s.maxTempDeciC / 10f
                val tempStr = if (s.maxTempDeciC <= 0) "--" else if (settings.useFahrenheit) {
                    String.format(Locale.US, "%.0fF", c * 9f / 5f + 32f)
                } else {
                    String.format(Locale.US, "%.1fC", c)
                }

                listOf(
                    typeStr to typeColor,
                    timeWindow to colors.foreground,
                    spanStr to colors.foreground,
                    curStr to if (s.isCharging) colors.good else colors.foreground,
                    tempStr to colors.warning
                )
            }
        }

        TextFramedBox(
            title = "CHARGE & DISCHARGE SESSIONS",
            rightHeaderTag = "* = ACTIVE"
        ) {
            TextTable(
                columns = listOf(
                    TextTableColumn("TYPE", 0.95f),
                    TextTableColumn("START", 1.25f),
                    TextTableColumn("START->END", 1.35f),
                    TextTableColumn("AVG MA", 0.9f, alignRight = true),
                    TextTableColumn("MAX T", 0.7f, alignRight = true)
                ),
                rows = rows,
                emptyMessage = "NO SESSIONS RECORDED YET"
            )
        }
    }
}
