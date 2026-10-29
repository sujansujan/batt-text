package com.batttext.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.batttext.app.data.db.BatterySampleEntity
import com.batttext.app.data.prefs.UserSettings
import com.batttext.app.data.repository.BatterySnapshot
import com.batttext.app.ui.HistoryRange
import com.batttext.app.ui.components.TextBlockGraph
import com.batttext.app.ui.components.TextButtonBracket
import com.batttext.app.ui.components.TextFramedBox
import com.batttext.app.ui.components.TextKeyValueRow
import com.batttext.app.ui.components.TextTable
import com.batttext.app.ui.components.TextTableColumn
import com.batttext.app.ui.theme.BattTextTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    samples: List<BatterySampleEntity>,
    liveSnapshot: BatterySnapshot,
    settings: UserSettings,
    selectedRange: HistoryRange,
    onSelectRange: (HistoryRange) -> Unit
) {
    val colors = BattTextTheme.colors

    val effectiveSamples = remember(samples, liveSnapshot) {
        if (samples.isNotEmpty()) samples
        else listOf(
            BatterySampleEntity(
                timestampMillis = liveSnapshot.timestampMillis,
                levelPercent = liveSnapshot.levelPercent,
                currentMa = liveSnapshot.currentMa,
                voltageMv = liveSnapshot.voltageMv,
                temperatureDeciC = liveSnapshot.temperatureDeciC,
                isCharging = liveSnapshot.isCharging,
                isScreenOn = liveSnapshot.isScreenOn
            )
        )
    }

    val bucketCount = 24
    val levelBuckets = remember(effectiveSamples) {
        downsampleToBuckets(effectiveSamples.map { it.levelPercent.toFloat() }, bucketCount)
    }
    val tempBuckets = remember(effectiveSamples, settings.useFahrenheit) {
        val rawTemps = effectiveSamples.map { sample ->
            val c = sample.temperatureDeciC / 10f
            if (settings.useFahrenheit) c * 9f / 5f + 32f else c
        }
        downsampleToBuckets(rawTemps, bucketCount)
    }

    val startLabel = if (selectedRange == HistoryRange.HOURS_24) "-24H" else "-7D"
    val midLabel = if (selectedRange == HistoryRange.HOURS_24) "-12H" else "-3.5D"
    val endLabel = "NOW"

    Column(modifier = Modifier.fillMaxWidth()) {
        TextFramedBox(
            title = "GRAPH WINDOW",
            rightHeaderTag = "${samples.size} SAMPLES"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryRange.entries.forEach { range ->
                        TextButtonBracket(
                            text = range.label,
                            onClick = { onSelectRange(range) },
                            active = selectedRange == range
                        )
                    }
                }
                TextKeyValueRow(
                    label = "INTERVAL",
                    value = "EVERY ${settings.sampleIntervalMinutes}M",
                    labelWidth = 68.dp,
                    modifier = Modifier.fillMaxWidth(0.55f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val minLvl = effectiveSamples.minOfOrNull { it.levelPercent } ?: 0
        val maxLvl = effectiveSamples.maxOfOrNull { it.levelPercent } ?: 100
        TextFramedBox(
            title = "BATTERY LEVEL (%) - ${selectedRange.label}",
            rightHeaderTag = "MIN $minLvl% / MAX $maxLvl%"
        ) {
            TextBlockGraph(
                values = levelBuckets,
                minY = 0f,
                maxY = 100f,
                unitSuffix = "%",
                startLabel = startLabel,
                midLabel = midLabel,
                endLabel = endLabel,
                barColor = colors.good,
                rowsCount = 6
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val tempUnit = if (settings.useFahrenheit) "F" else "C"
        val minTemp = tempBuckets.minOrNull() ?: if (settings.useFahrenheit) 68f else 20f
        val maxTemp = tempBuckets.maxOrNull() ?: if (settings.useFahrenheit) 115f else 45f
        val graphLow = (minTemp - 4f).coerceAtLeast(0f)
        val graphHigh = (maxTemp + 4f).coerceAtLeast(graphLow + 10f)

        TextFramedBox(
            title = "TEMPERATURE ($tempUnit) - ${selectedRange.label}",
            rightHeaderTag = String.format(Locale.US, "PEAK %.1f%s", maxTemp, tempUnit)
        ) {
            TextBlockGraph(
                values = tempBuckets,
                minY = graphLow,
                maxY = graphHigh,
                unitSuffix = tempUnit,
                startLabel = startLabel,
                midLabel = midLabel,
                endLabel = endLabel,
                barColor = colors.warning,
                rowsCount = 6
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val sdf = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
        val tableRows = remember(effectiveSamples, settings.useFahrenheit) {
            effectiveSamples.takeLast(15).reversed().map { s ->
                val tStr = sdf.format(Date(s.timestampMillis))
                val lvlStr = "${s.levelPercent}%"
                val curStr = "${if (s.currentMa > 0) "+" else ""}${s.currentMa}mA"
                val c = s.temperatureDeciC / 10f
                val tmpStr = if (settings.useFahrenheit) {
                    String.format(Locale.US, "%.1fF", c * 9f / 5f + 32f)
                } else {
                    String.format(Locale.US, "%.1fC", c)
                }
                val stateStr = if (s.isCharging) "CHG" else if (s.isScreenOn) "ON" else "OFF"
                listOf(
                    tStr to colors.foreground,
                    lvlStr to colors.good,
                    curStr to if (s.currentMa > 0) colors.good else colors.foreground,
                    tmpStr to colors.warning,
                    stateStr to colors.muted
                )
            }
        }

        TextFramedBox(
            title = "RECENT SAMPLES LOG",
            rightHeaderTag = "LATEST 15"
        ) {
            TextTable(
                columns = listOf(
                    TextTableColumn("TIME", 1.35f),
                    TextTableColumn("LVL", 0.7f, alignRight = true),
                    TextTableColumn("CUR", 1.05f, alignRight = true),
                    TextTableColumn("TEMP", 0.85f, alignRight = true),
                    TextTableColumn("MODE", 0.75f, alignRight = true)
                ),
                rows = tableRows
            )
        }
    }
}

private fun downsampleToBuckets(values: List<Float>, targetBuckets: Int): List<Float> {
    if (values.isEmpty()) return emptyList()
    if (values.size <= targetBuckets) return values
    val result = ArrayList<Float>(targetBuckets)
    val step = values.size.toFloat() / targetBuckets.toFloat()
    for (i in 0 until targetBuckets) {
        val start = (i * step).toInt().coerceIn(0, values.lastIndex)
        val end = ((i + 1) * step).toInt().coerceIn(start + 1, values.size)
        val slice = values.subList(start, end)
        result.add(slice.average().toFloat())
    }
    return result
}
