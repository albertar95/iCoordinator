package com.icoordinator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.icoordinator.R
import com.icoordinator.accounting.AccountingFormat

/** One folder tile: id (colour comes from it), name and the number of items inside. */
data class FolderTileData(val id: Long, val name: String, val count: Int)

/** Folder colours, derived from the folder id so no database column is needed. */
object FolderColors {
    val palette: List<Color> = listOf(
        Color(0xFFF59E0B), // amber
        Color(0xFFF97316), // orange
        Color(0xFFF43F5E), // rose
        Color(0xFF8B5CF6), // violet
        Color(0xFF3B82F6), // blue
        Color(0xFF06B6D4), // cyan
        Color(0xFF10B981), // emerald
        Color(0xFF84CC16), // lime
    )

    fun forId(id: Long): Color = palette[(((id % 8) + 8) % 8).toInt()]
}

private const val TileAspect = 1.25f

/**
 * Grid of coloured folder tiles ([columns] per row). Not lazy: it is built from Rows so it can sit inside one
 * LazyColumn item. When [onAdd] is not null the last tile is an "add folder" tile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGrid(
    folders: List<FolderTileData>,
    onOpen: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    val cols = columns.coerceAtLeast(1)
    val cells: List<FolderTileData?> = if (onAdd != null) folders + listOf<FolderTileData?>(null) else folders
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in cells.chunked(cols)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until cols) {
                    val cell = row.getOrNull(i)
                    Box(modifier = Modifier.weight(1f)) {
                        if (i < row.size) {
                            if (cell != null) {
                                FolderTile(cell, { onOpen(cell.id) }, { onLongPress(cell.id) })
                            } else if (onAdd != null) {
                                AddFolderTile(onAdd)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Draws the folder: back panel with a small tab at the start corner, lighter front panel below. Mirrors in RTL. */
private fun DrawScope.drawFolder(color: Color, rtl: Boolean) {
    val w = size.width
    val h = size.height
    val tabW = w * 0.42f
    val tabH = h * 0.22f
    val r = h * 0.12f
    val tabX = if (rtl) w - tabW else 0f
    // tab + back body
    drawRoundRect(color, Offset(tabX, 0f), Size(tabW, tabH + r * 2), CornerRadius(r, r))
    drawRoundRect(color, Offset(0f, tabH), Size(w, h - tabH), CornerRadius(r, r))
    // front panel
    val frontTop = h * 0.30f
    drawRoundRect(
        lerp(color, Color.White, 0.18f),
        Offset(0f, frontTop),
        Size(w, h - frontTop),
        CornerRadius(r, r),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(folder: FolderTileData, onClick: () -> Unit, onLongClick: () -> Unit) {
    val color = FolderColors.forId(folder.id)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = folder.name }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(TileAspect)) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawFolder(color, rtl) }
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.70f).align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = AccountingFormat.digits(folder.count.toString()),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun AddFolderTile(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val label = "پوشه‌ی جدید"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(TileAspect), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.height * 0.12f
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    cornerRadius = CornerRadius(r, r),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            }
            TbIcon(R.drawable.ic_tb_plus, null, tint = outline)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        )
    }
}
