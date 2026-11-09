package com.icoordinator.ui.accounting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.MonthTotals
import com.icoordinator.accounting.TxType

/** One row of [HorizontalBars]: label, numeric value (negative is drawn in the error colour), value text, bar colour. */
internal class BarItem(val label: String, val value: Long, val valueText: String, val color: Color)

/** Donut chart: slice i has size values[i] (non-positive values are skipped) and colour colors[i]. Give it a fixed size. */
@Composable
internal fun PieChart(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = values.sumOf { if (it > 0L) it else 0L }
    Canvas(modifier = modifier) {
        if (total > 0L) {
            val strokeWidth = size.minDimension * 0.28f
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var start = -90f
            values.forEachIndexed { index, value ->
                if (value > 0L) {
                    val sweep = (value.toDouble() / total.toDouble() * 360.0).toFloat()
                    drawArc(
                        color = colors.getOrElse(index) { Color.Gray },
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = strokeWidth),
                    )
                    start += sweep
                }
            }
        }
    }
}

/**
 * Income / expense bar pairs, oldest month first (at the start side of the layout direction), with the Jalali month
 * number in Persian digits under each pair.
 */
@Composable
internal fun BarPairChart(months: List<MonthTotals>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val incomeColor = txTypeColor(TxType.RECEIVE)
    val expenseColor = scheme.error
    val axisColor = scheme.outlineVariant
    val maxValue = months.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0L
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val count = months.size
            val baseY = size.height
            drawLine(color = axisColor, start = Offset(0f, baseY), end = Offset(size.width, baseY), strokeWidth = 2f)
            if (count > 0 && maxValue > 0L) {
                val slot = size.width / count
                val barWidth = slot * 0.3f
                val gap = slot * 0.04f
                val usable = baseY - 4f
                months.forEachIndexed { index, m ->
                    val groupLeft = slot * index + (slot - barWidth * 2f - gap) / 2f
                    val bars = listOf(m.income to incomeColor, m.expense to expenseColor)
                    bars.forEachIndexed { position, (value, color) ->
                        if (value > 0L) {
                            val h = maxOf(2f, (value.toDouble() / maxValue.toDouble() * usable).toFloat())
                            val x = groupLeft + position * (barWidth + gap)
                            val drawX = if (rtl) size.width - x - barWidth else x
                            drawRect(
                                color = color,
                                topLeft = Offset(drawX, baseY - h),
                                size = Size(barWidth, h),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            months.forEach { m ->
                Text(
                    text = AccountingFormat.digits(m.month.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One labelled bar per item; bar length is relative to the largest absolute value. */
@Composable
internal fun HorizontalBars(items: List<BarItem>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val maxAbs = items.maxOfOrNull { if (it.value < 0L) -it.value else it.value } ?: 0L
    Column(modifier = modifier) {
        items.forEach { item ->
            val abs = if (item.value < 0L) -item.value else item.value
            val fraction = if (maxAbs <= 0L || abs == 0L) 0f else (abs.toDouble() / maxAbs.toDouble()).toFloat().coerceIn(0.02f, 1f)
            val barColor = if (item.value < 0L) scheme.error else item.color
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.valueText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.value < 0L) scheme.error else scheme.onSurface,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant),
                ) {
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(barColor),
                        )
                    }
                }
            }
        }
    }
}
