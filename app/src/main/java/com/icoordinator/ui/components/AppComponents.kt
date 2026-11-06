package com.icoordinator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.input.KeyboardType
import com.icoordinator.R
import com.icoordinator.ui.theme.scoreColor

private val PillShape = RoundedCornerShape(30.dp)

/** Outline icon from an ic_tb_* drawable, tinted with the current content colour by default. */
@Composable
fun TbIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** Screen title row (64 dp): optional back button at the start, title, optional 40 dp outline action circle at the end. */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    @DrawableRes actionIcon: Int? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                TbIcon(R.drawable.ic_tb_arrow_left, "بازگشت", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionIcon != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable(role = Role.Button, onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                TbIcon(
                    icon = actionIcon,
                    contentDescription = actionDescription,
                    size = 22.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Pill-shaped row card: leading circle, text column, optional trailing slot; selected = 2 dp primary border. */
@Composable
fun PillRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), content = content)
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = PillShape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = rowContent,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = PillShape,
            color = MaterialTheme.colorScheme.surface,
            border = border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = rowContent,
        )
    }
}

/** Round container holding an outline icon (default 54 dp, grey icon on surfaceContainer). */
@Composable
fun IconCircle(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    iconSize: Dp = 26.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        TbIcon(icon = icon, contentDescription = null, size = iconSize, tint = tint)
    }
}

/** Round container with the first character of the text (project letter), primaryContainer and primary colours. */
@Composable
fun LetterCircle(text: String, modifier: Modifier = Modifier, size: Dp = 54.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.trim().take(1),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 54 dp grey circle holding an M3 Checkbox. */
@Composable
fun CheckCircle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Pill button: primary fill, outlined (secondary) or destructive (error colours); 56 dp high unless the caller sets a height. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    outlined: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val buttonContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                TbIcon(icon = icon, contentDescription = null, size = 22.dp)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
    val buttonModifier = modifier.height(56.dp)
    val padding = PaddingValues(horizontal = 28.dp)
    if (outlined) {
        val tone = if (destructive) scheme.error else scheme.onSurface
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(1.5.dp, if (enabled) tone else tone.copy(alpha = 0.38f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = tone),
            contentPadding = padding,
        ) {
            buttonContent()
        }
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = CircleShape,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
            } else {
                ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary)
            },
            contentPadding = padding,
        ) {
            buttonContent()
        }
    }
}

/** Header quick action: 64 dp outlined circle with icon and a centred label below (width 76 dp). */
@Composable
fun QuickAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(scheme.surface)
                .border(1.5.dp, if (accent) scheme.primary else scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TbIcon(
                icon = icon,
                contentDescription = null,
                size = 28.dp,
                tint = if (accent) scheme.primary else scheme.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Empty list placeholder: 128 dp rounded block with icon, grey title and message, optional action (for example a PillButton). */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 88.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(scheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            TbIcon(icon = icon, contentDescription = null, size = 64.dp, tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp),
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Small bold label above a group (15 sp Bold, 8 dp start padding, 8 dp above). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 8.dp, top = 8.dp),
    )
}

/** 6 dp rounded progress bar (primary on surfaceVariant), fills from the start side. */
@Composable
fun ProgressLine(percent: Int, modifier: Modifier = Modifier) {
    val fraction = percent.coerceIn(0, 100) / 100f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

// Text colour on a score colour (spec-v3 "on" colours); kept here because Theme exposes only scoreColor().
@Composable
private fun scoreOnColor(percent: Int?): Color {
    val dark = isSystemInDarkTheme()
    return when {
        percent == null -> MaterialTheme.colorScheme.surface
        percent > 80 -> if (dark) Color(0xFF00210A) else Color(0xFFFFFFFF)
        percent >= 50 -> Color(0xFF1A1A1A)
        else -> if (dark) Color(0xFF410002) else Color(0xFFFFFFFF)
    }
}

/** Score chip ("32٪") in the score colour; null shows a dash on grey. */
@Composable
fun ScorePill(percent: Int?, modifier: Modifier = Modifier) {
    val onColor = scoreOnColor(percent)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = scoreColor(percent),
        contentColor = onColor,
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (percent == null) "—" else "$percent٪",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = onColor,
            )
        }
    }
}

/** Circular score ring drawn on a Canvas (round caps, track surfaceVariant) with the percent and "نمره" in the centre. */
@Composable
fun ScoreRing(percent: Int?, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    val scheme = MaterialTheme.colorScheme
    val arcColor = scoreColor(percent)
    val trackColor = scheme.surfaceVariant
    val sweep = (percent ?: 0).coerceIn(0, 100) * 3.6f
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = this.size.minDimension * 0.094f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (sweep > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (percent == null) "—" else "$percent٪",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = (size.value * 0.27f).sp,
                    lineHeight = (size.value * 0.3f).sp,
                ),
                color = scheme.onSurface,
            )
            Text(
                text = "نمره",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 48 dp icon button with the 26 dp trash icon in the error colour. */
@Composable
fun DeleteIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "حذف") {
    IconButton(onClick = onClick, modifier = modifier) {
        TbIcon(R.drawable.ic_tb_trash, contentDescription, size = 26.dp, tint = MaterialTheme.colorScheme.error)
    }
}

/** Small 12 sp tag text with an optional 14 dp icon before it. */
@Composable
fun TagText(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            TbIcon(icon = icon, contentDescription = null, size = 14.dp, tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = color,
        )
    }
}

/** Pill-shaped OutlinedTextField (28 dp corners; multi-line fields use 24 dp corners). */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val scheme = MaterialTheme.colorScheme
    val supporting: (@Composable () -> Unit)? = if (supportingText != null) {
        { Text(text = supportingText, style = MaterialTheme.typography.bodySmall) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        isError = isError,
        supportingText = supporting,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        shape = RoundedCornerShape(if (singleLine) 28.dp else 24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = scheme.outlineVariant,
            focusedBorderColor = scheme.primary,
            errorBorderColor = scheme.error,
        ),
    )
}

/** Shared delete confirmation: Dialog + Surface (32 dp corners, 330 dp wide), 32% scrim, cancel (outlined) and delete (error) buttons. */
@Composable
fun ConfirmDeleteDialog(itemName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setDimAmount(0.32f)
        }
        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = Modifier.width(330.dp),
            shape = RoundedCornerShape(32.dp),
            color = scheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    TbIcon(R.drawable.ic_tb_trash, null, size = 32.dp, tint = scheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "حذف «$itemName»؟",
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "این کار قابل برگشت نیست.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PillButton(
                        text = "انصراف",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        outlined = true,
                    )
                    PillButton(
                        text = "حذف",
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(52.dp),
                        destructive = true,
                    )
                }
            }
        }
    }
}
