package com.batttext.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.batttext.app.ui.theme.BattTextTheme
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Full-width horizontal text rule made of repeated `─` or `═` (or `-` / `=` in pure ASCII mode).
 * Uses `maxLines = 1, softWrap = false, overflow = TextOverflow.Clip` so it fills the exact
 * available width under any font (System or Monospace) without needing SubcomposeLayout.
 */
@Composable
fun TextDivider(
    doubleLine: Boolean = false,
    color: Color = BattTextTheme.colors.border,
    modifier: Modifier = Modifier
) {
    val charset = BattTextTheme.charset
    val ch = if (doubleLine) charset.doubleHorizontal else charset.horizontal
    val lineStr = remember(ch) { ch.toString().repeat(220) }
    Text(
        text = lineStr,
        color = color,
        fontFamily = BattTextTheme.fontFamily,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Plain-text framed section box inspired by plaintextsports.com.
 *
 * Uses `drawBehind` on the middle content container to draw the left and right vertical border
 * rails, avoiding `IntrinsicSize.Min` and `BoxWithConstraints` so it is 100% immune to nested
 * scroll or intrinsic measurement crashes while staying pixel-aligned on proportional system fonts.
 */
@Composable
fun TextFramedBox(
    title: String,
    modifier: Modifier = Modifier,
    rightHeaderTag: String? = null,
    borderColor: Color = BattTextTheme.colors.border,
    titleColor: Color = BattTextTheme.colors.accent,
    content: @Composable ColumnScope.() -> Unit
) {
    val charset = BattTextTheme.charset
    val fontFamily = BattTextTheme.fontFamily
    val repeatedRule = remember(charset.horizontal) { charset.horizontal.toString().repeat(220) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Top border row: ┌─ TITLE ─────────────── [TAG] ─┐
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${charset.topLeft}${charset.horizontal} ",
                color = borderColor,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = title.uppercase(),
                color = titleColor,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = " $repeatedRule",
                color = borderColor,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
            if (!rightHeaderTag.isNullOrBlank()) {
                Text(
                    text = " $rightHeaderTag ",
                    color = BattTextTheme.colors.muted,
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = "${charset.horizontal}${charset.topRight}",
                color = borderColor,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
            )
        }

        // Middle content with left & right vertical rails drawn directly on the Column canvas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val strokePx = 1.5.dp.toPx()
                    val leftX = 4.dp.toPx()
                    val rightX = size.width - 4.dp.toPx()
                    drawLine(
                        color = borderColor,
                        start = Offset(leftX, 0f),
                        end = Offset(leftX, size.height),
                        strokeWidth = strokePx
                    )
                    drawLine(
                        color = borderColor,
                        start = Offset(rightX, 0f),
                        end = Offset(rightX, size.height),
                        strokeWidth = strokePx
                    )
                }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            content = content
        )

        // Bottom border row: └──────────────────────────────┘
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = charset.bottomLeft.toString(),
                color = borderColor,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = repeatedRule,
                color = borderColor,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = charset.bottomRight.toString(),
                color = borderColor,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Aligned label/value row inside a framed box.
 */
@Composable
fun TextKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 108.dp,
    valueColor: Color = BattTextTheme.colors.foreground,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = BattTextTheme.colors
    val fontFamily = BattTextTheme.fontFamily
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label.uppercase(),
            color = colors.label,
            fontFamily = fontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth)
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = fontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (trailingContent == null) Modifier.weight(1f) else Modifier.padding(end = 8.dp)
        )
        if (trailingContent != null) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                trailingContent()
            }
        }
    }
}

/**
 * Block-character progress bar: `[███████▌░░]` or `[#######=..]`
 */
@Composable
fun TextProgressBar(
    fraction: Float,
    segments: Int = 12,
    fillColor: Color = BattTextTheme.colors.good,
    emptyColor: Color = BattTextTheme.colors.muted
) {
    val charset = BattTextTheme.charset
    val fontFamily = BattTextTheme.fontFamily
    val clamped = fraction.coerceIn(0f, 1f)
    val exactBlocks = clamped * segments
    val fullBlocks = floor(exactBlocks).toInt()
    val hasHalf = (exactBlocks - fullBlocks) >= 0.45f && fullBlocks < segments
    val emptyBlocks = (segments - fullBlocks - (if (hasHalf) 1 else 0)).coerceAtLeast(0)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("[", color = BattTextTheme.colors.border, fontFamily = fontFamily, fontSize = 13.sp)
        if (fullBlocks > 0) {
            Text(
                text = charset.barFull.toString().repeat(fullBlocks),
                color = fillColor,
                fontFamily = fontFamily,
                fontSize = 13.sp
            )
        }
        if (hasHalf) {
            Text(
                text = charset.barHalf.toString(),
                color = fillColor,
                fontFamily = fontFamily,
                fontSize = 13.sp
            )
        }
        if (emptyBlocks > 0) {
            Text(
                text = charset.barEmpty.toString().repeat(emptyBlocks),
                color = emptyColor,
                fontFamily = fontFamily,
                fontSize = 13.sp
            )
        }
        Text("]", color = BattTextTheme.colors.border, fontFamily = fontFamily, fontSize = 13.sp)
    }
}

/**
 * Bracketed text button `[ LABEL ]` with no background elevation or ripple.
 */
@Composable
fun TextButtonBracket(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = BattTextTheme.colors.link,
    active: Boolean = false
) {
    val fontFamily = BattTextTheme.fontFamily
    val labelText = if (active) "[*${text.uppercase()}*]" else "[ ${text.uppercase()} ]"
    Text(
        text = labelText,
        color = if (active) BattTextTheme.colors.good else color,
        fontFamily = fontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 2.dp)
    )
}

/**
 * Plain-text navigation bar: `[*DASH*]  [ HISTORY ]  [ SESSIONS ]  [ SETTINGS ]`
 */
@Composable
fun TextTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BattTextTheme.colors
    val fontFamily = BattTextTheme.fontFamily
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            val formatted = if (selected) "[*${title.uppercase()}*]" else " ${title.uppercase()} "
            Text(
                text = formatted,
                color = if (selected) colors.good else colors.foreground,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (selected) TextDecoration.None else TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(vertical = 2.dp, horizontal = 2.dp)
            )
        }
    }
}

/**
 * Plain-text `[x]` / `[ ]` toggle row.
 */
@Composable
fun TextToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null
) {
    val colors = BattTextTheme.colors
    val fontFamily = BattTextTheme.fontFamily
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                color = colors.foreground,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = colors.muted,
                    fontFamily = fontFamily,
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text = if (checked) "[x] ON " else "[ ] OFF",
            color = if (checked) colors.good else colors.muted,
            fontFamily = fontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Plain-text `< VALUE >` stepper row for numeric thresholds and enum options.
 */
@Composable
fun TextStepperRow(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    subtitle: String? = null
) {
    val colors = BattTextTheme.colors
    val fontFamily = BattTextTheme.fontFamily
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                color = colors.foreground,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = colors.muted,
                    fontFamily = fontFamily,
                    fontSize = 11.sp
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = " < ",
                color = colors.link,
                fontFamily = fontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onDecrement)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Text(
                text = valueText,
                color = colors.accent,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(76.dp)
            )
            Text(
                text = " > ",
                color = colors.link,
                fontFamily = fontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onIncrement)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Multi-column plain-text table where columns use weighted Compose layout cells
 * so proportional system fonts stay aligned to exact pixel columns without jagged drift.
 */
data class TextTableColumn(
    val header: String,
    val weight: Float,
    val alignRight: Boolean = false
)

@Composable
fun TextTable(
    columns: List<TextTableColumn>,
    rows: List<List<Pair<String, Color?>>>,
    emptyMessage: String = "NO RECORDS YET"
) {
    val colors = BattTextTheme.colors
    val fontFamily = BattTextTheme.fontFamily

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEach { col ->
                Text(
                    text = col.header.uppercase(),
                    color = colors.label,
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (col.alignRight) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(col.weight)
                        .padding(horizontal = 2.dp)
                )
            }
        }
        TextDivider(doubleLine = false, color = colors.border)

        if (rows.isEmpty()) {
            Text(
                text = emptyMessage,
                color = colors.muted,
                fontFamily = fontFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            rows.forEach { rowCells ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columns.forEachIndexed { idx, col ->
                        val cell = rowCells.getOrNull(idx) ?: ("" to null)
                        Text(
                            text = cell.first,
                            color = cell.second ?: colors.foreground,
                            fontFamily = fontFamily,
                            fontSize = 12.sp,
                            textAlign = if (col.alignRight) TextAlign.End else TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(col.weight)
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Multi-row plain-text block graph that stays column-aligned under any font family.
 */
@Composable
fun TextBlockGraph(
    values: List<Float>,
    minY: Float,
    maxY: Float,
    unitSuffix: String,
    startLabel: String,
    midLabel: String,
    endLabel: String,
    barColor: Color = BattTextTheme.colors.good,
    rowsCount: Int = 6
) {
    val colors = BattTextTheme.colors
    val charset = BattTextTheme.charset
    val fontFamily = BattTextTheme.fontFamily
    val safeSpan = (maxY - minY).takeIf { it > 0.01f } ?: 1f

    if (values.isEmpty()) {
        Text(
            text = "WAITING FOR TELEMETRY SAMPLES...",
            color = colors.muted,
            fontFamily = fontFamily,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        for (rowIdx in (rowsCount - 1) downTo 0) {
            val rowLow = minY + (safeSpan * rowIdx / rowsCount.toFloat())
            val rowHigh = minY + (safeSpan * (rowIdx + 1) / rowsCount.toFloat())
            val axisText = if (rowIdx == rowsCount - 1 || rowIdx == rowsCount / 2 || rowIdx == 0) {
                "${rowHigh.roundToInt()}$unitSuffix"
            } else {
                ""
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = axisText,
                    color = colors.muted,
                    fontFamily = fontFamily,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .width(42.dp)
                        .padding(end = 4.dp)
                )
                Text(
                    text = charset.vertical.toString(),
                    color = colors.border,
                    fontFamily = fontFamily,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    values.forEach { v ->
                        val cellFill = ((v - rowLow) / (rowHigh - rowLow)).coerceIn(0f, 1f)
                        val glyph = when {
                            cellFill <= 0.05f -> "·"
                            else -> {
                                val chars = charset.sparkChars
                                val idx = ((cellFill * (chars.length - 1)).roundToInt())
                                    .coerceIn(0, chars.length - 1)
                                chars[idx].toString()
                            }
                        }
                        val glyphColor = if (cellFill <= 0.05f) colors.panel else barColor
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = glyph,
                                color = glyphColor,
                                fontFamily = fontFamily,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // X-axis baseline
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(42.dp))
            Text(
                text = charset.bottomLeft.toString(),
                color = colors.border,
                fontFamily = fontFamily,
                fontSize = 12.sp
            )
            Box(modifier = Modifier.weight(1f)) {
                TextDivider(doubleLine = false, color = colors.border)
            }
        }

        // X-axis time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 46.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(startLabel, color = colors.muted, fontFamily = fontFamily, fontSize = 11.sp)
            Text(midLabel, color = colors.muted, fontFamily = fontFamily, fontSize = 11.sp)
            Text(endLabel, color = colors.muted, fontFamily = fontFamily, fontSize = 11.sp)
        }
    }
}

/**
 * Plain-text modal dialog drawn with box-drawing borders and bracketed action buttons.
 */
@Composable
fun TextModalDialog(
    title: String,
    bodyLines: List<String>,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "CANCEL",
    onDismiss: () -> Unit
) {
    val colors = BattTextTheme.colors
    val fontFamily = BattTextTheme.fontFamily

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(4.dp)
        ) {
            TextFramedBox(
                title = title,
                borderColor = colors.accent,
                titleColor = colors.warning
            ) {
                bodyLines.forEach { line ->
                    Text(
                        text = line,
                        color = colors.foreground,
                        fontFamily = fontFamily,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButtonBracket(
                        text = dismissLabel,
                        onClick = onDismiss,
                        color = colors.muted
                    )
                    TextButtonBracket(
                        text = confirmLabel,
                        onClick = onConfirm,
                        color = colors.good
                    )
                }
            }
        }
    }
}
