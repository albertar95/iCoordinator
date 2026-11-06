package com.icoordinator.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.icoordinator.R

private class BarItem(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int,
    val raised: Boolean = false,
)

// Order: first item = rightmost in RTL (D14). The raised centre item is the calendar.
private val barItems = listOf(
    BarItem(Routes.PROJECTS, "پروژه‌ها", R.drawable.ic_tb_home),
    BarItem(Routes.ROUTINES, "روتین‌ها", R.drawable.ic_tb_repeat),
    BarItem(Routes.CALENDAR, "تقویم", R.drawable.ic_tb_calendar, raised = true),
    BarItem(Routes.WEEKLY_LOG, "تسک‌ها", R.drawable.ic_tb_checklist),
    BarItem(Routes.REPORT, "گزارش هفته", R.drawable.ic_tb_report_analytics),
)

private val BarRowHeight = 72.dp
private val RaisedCircleSize = 64.dp
private val RaisedRing = 6.dp
// Part of the raised circle that sticks out above the bar; it is part of this composable's own bounds so taps on it work.
private val RaisedOverhang = 38.dp

/** Custom bottom bar: five equal items, calendar as the raised centre circle; selected = primary colour. */
@Composable
fun AppBottomBar(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val barShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val raisedItem = barItems.firstOrNull { it.raised }

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = RaisedOverhang)
                .background(scheme.surfaceContainerHigh, barShape)
                .border(1.dp, scheme.outlineVariant, barShape)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarRowHeight),
            ) {
                barItems.forEach { item ->
                    BarEntry(
                        item = item,
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (raisedItem != null) {
            val selected = currentRoute == raisedItem.route
            // 6 dp ring in the background colour (the notch look)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -RaisedRing)
                    .size(RaisedCircleSize + RaisedRing * 2)
                    .drawBehind {
                        drawCircle(color = scheme.background)
                    },
            )
            RaisedCircle(
                item = raisedItem,
                selected = selected,
                onClick = { onNavigate(raisedItem.route) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun BarEntry(item: BarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tint: Color = if (selected) scheme.primary else scheme.onSurface
    Column(
        modifier = modifier
            .fillMaxSize()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(top = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (item.raised) {
            // the icon itself is drawn by the raised circle; keep its place so the label lines up
            Spacer(Modifier.height(28.dp))
        } else {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = tint,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else null,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun RaisedCircle(item: BarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (selected) scheme.primary else scheme.surface
    val iconTint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
    val iconSize: Dp = if (selected) 30.dp else 28.dp
    Box(
        modifier = modifier
            .size(RaisedCircleSize)
            .clip(CircleShape)
            .background(fill)
            .then(if (selected) Modifier else Modifier.border(1.dp, scheme.outlineVariant, CircleShape))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = item.label,
            modifier = Modifier.size(iconSize),
            tint = iconTint,
        )
    }
}
