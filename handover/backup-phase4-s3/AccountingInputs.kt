package com.icoordinator.ui.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.icoordinator.R
import com.icoordinator.accounting.AccountEntity
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AmountExpression
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.PillButton
import com.icoordinator.ui.components.TbIcon

/**
 * Full-screen account chooser with search and an expandable tree. Only accounts with allowed(account) == true can be
 * picked (others are shown greyed if they have allowed descendants, hidden otherwise).
 */
@Composable
fun AccountPickerDialog(
    title: String,
    accounts: List<AccountEntity>,
    allowed: (AccountEntity) -> Boolean,
    onDismiss: () -> Unit,
    onPick: (AccountEntity) -> Unit,
) {
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val childrenOf = remember(accounts) {
        accounts.groupBy { a -> a.parentId?.takeIf { it in byId } }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    }
    // ids of accounts that are allowed or have an allowed descendant (cycle-safe)
    val visible = remember(accounts, allowed) {
        val result = HashSet<Long>()
        fun visit(a: AccountEntity, path: MutableSet<Long>): Boolean {
            if (!path.add(a.id)) return false
            var any = allowed(a)
            for (child in childrenOf[a.id].orEmpty()) {
                if (visit(child, path)) any = true
            }
            path.remove(a.id)
            if (any) result.add(a.id)
            return any
        }
        for (root in childrenOf[null].orEmpty()) visit(root, HashSet())
        result
    }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<Long>()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                PageHeader(title = title, onBack = onDismiss)
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "جستجو",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                val q = query.trim()
                if (q.isNotEmpty()) {
                    val matches = accounts.filter { allowed(it) && it.title.contains(q, ignoreCase = true) }
                    if (matches.isEmpty()) {
                        EmptyHint("حسابی پیدا نشد")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(matches, key = { it.id }) { a ->
                                PickerRow(
                                    account = a,
                                    subtitle = AccountingFormat.accountPath(a.id, byId),
                                    depth = 0,
                                    enabled = true,
                                    canExpand = false,
                                    isExpanded = false,
                                    onToggle = {},
                                    onClick = { onPick(a) },
                                )
                            }
                        }
                    }
                } else {
                    val rows = buildList {
                        fun walk(a: AccountEntity, depth: Int, path: MutableSet<Long>) {
                            if (a.id !in visible || !path.add(a.id)) return
                            add(a to depth)
                            if (a.id in expanded) {
                                for (child in childrenOf[a.id].orEmpty()) walk(child, depth + 1, path)
                            }
                            path.remove(a.id)
                        }
                        for (root in childrenOf[null].orEmpty()) walk(root, 0, HashSet())
                    }
                    if (rows.isEmpty()) {
                        EmptyHint("حسابی برای انتخاب نیست")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(rows, key = { it.first.id }) { (a, depth) ->
                                val hasKids = childrenOf[a.id].orEmpty().any { it.id in visible }
                                val ok = allowed(a)
                                val toggle = {
                                    expanded = if (a.id in expanded) expanded - a.id else expanded + a.id
                                }
                                PickerRow(
                                    account = a,
                                    subtitle = null,
                                    depth = depth,
                                    enabled = ok,
                                    canExpand = hasKids,
                                    isExpanded = a.id in expanded,
                                    onToggle = toggle,
                                    onClick = { if (ok) onPick(a) else if (hasKids) toggle() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PickerRow(
    account: AccountEntity,
    subtitle: String?,
    depth: Int,
    enabled: Boolean,
    canExpand: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = (20 + depth * 20).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(
            icon = accountKindIcon(AccountKind.fromCode(account.kindCode)),
            size = 44.dp,
            iconSize = 24.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = account.title, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            if (!subtitle.isNullOrEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        if (canExpand) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(8.dp),
            ) {
                TbIcon(
                    icon = R.drawable.ic_tb_chevron_left,
                    contentDescription = if (isExpanded) "بستن" else "باز کردن",
                    modifier = Modifier.rotate(if (isExpanded) 90f else 0f),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_EXPRESSION = 30
private val OPERATORS = setOf('+', '-', '×', '÷')

/** Calculator keypad in a ModalBottomSheet. Digits are kept as Latin digits internally and shown in Persian digits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountKeypadSheet(initial: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var expr by remember { mutableStateOf(initial?.takeIf { it > 0 }?.toString() ?: "") }
    val result = if (expr.isBlank()) null else AmountExpression.evaluate(expr)
    val scheme = MaterialTheme.colorScheme

    fun append(text: String) {
        if (expr.length + text.length <= MAX_EXPRESSION) expr += text
    }
    fun digit(d: String) {
        val last = expr.lastOrNull()
        if (d == "00" && (last == null || last in OPERATORS)) append("0") else append(d)
    }
    fun operator(op: Char) {
        val last = expr.lastOrNull() ?: return
        if (last in OPERATORS) expr = expr.dropLast(1) + op else append(op.toString())
    }
    fun point() {
        val segment = expr.takeLastWhile { it !in OPERATORS }
        if ('.' in segment) return
        append(if (segment.isEmpty()) "0." else ".")
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            // display line: C, backspace, expression (numbers read left to right)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeyButton("C", { expr = "" }, Modifier.width(56.dp).height(48.dp), scheme.errorContainer, scheme.onErrorContainer)
                    Spacer(Modifier.width(8.dp))
                    KeyButton("⌫", { expr = expr.dropLast(1) }, Modifier.width(56.dp).height(48.dp), scheme.surfaceContainerHigh, scheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = AccountingFormat.digits(expr).replace('-', '−').replace('.', '٫'),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (expr.isBlank()) "۰ تومان" else if (result != null) MoneyFormat.format(result) + " تومان" else "عبارت نامعتبر است",
                style = MaterialTheme.typography.titleMedium,
                color = if (expr.isBlank() || result != null) scheme.primary else scheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            listOf("7", "8", "9"),
                            listOf("4", "5", "6"),
                            listOf("1", "2", "3"),
                            listOf("0", "00", "."),
                        ).forEach { keys ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                keys.forEach { k ->
                                    KeyButton(
                                        label = if (k == ".") "٫" else AccountingFormat.digits(k),
                                        onClick = { if (k == ".") point() else digit(k) },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        container = scheme.surfaceContainer,
                                        content = scheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf('÷', '×', '-', '+').forEach { op ->
                            KeyButton(
                                label = if (op == '-') "−" else op.toString(),
                                onClick = { operator(op) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                container = scheme.primaryContainer,
                                content = scheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = "تایید",
                    onClick = { if (result != null && result > 0L) onConfirm(result) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled = result != null && result > 0L,
                )
                PillButton(
                    text = "لغو",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    outlined = true,
                )
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit, modifier: Modifier, container: Color, content: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge, color = content)
    }
}
