package com.icoordinator.ui.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.ICoordinatorApp
import com.icoordinator.R
import com.icoordinator.accounting.AccountEntity
import com.icoordinator.accounting.AccountKind
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.accounting.AccountingMath
import com.icoordinator.accounting.AccountingRepository
import com.icoordinator.accounting.MoneyFormat
import com.icoordinator.ui.components.AppTextField
import com.icoordinator.ui.components.EmptyState
import com.icoordinator.ui.components.IconCircle
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.TagText
import com.icoordinator.ui.components.TbIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsState(
    val loaded: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    /** accountId -> own balance plus the balances of all descendants (AccountingMath.rolledUp). */
    val rolledBalances: Map<Long, Long> = emptyMap(),
)

private const val ERROR_EMPTY = "نام خالی است"
private const val ERROR_DUPLICATE = "این نام زیر همین حساب وجود دارد"

/** Kinds whose accounts can carry an opening balance. */
private val OPENING_KINDS = setOf(
    AccountKind.FUND, AccountKind.BANK, AccountKind.DEBTOR, AccountKind.CREDITOR,
    AccountKind.PERSON, AccountKind.ASSET, AccountKind.LIABILITY, AccountKind.CAPITAL,
)

class AccountsViewModel(private val repo: AccountingRepository) : ViewModel() {

    val state: StateFlow<AccountsState> =
        combine(repo.observeAccounts(), repo.observeBalances()) { accounts, balances ->
            AccountsState(
                loaded = true,
                accounts = accounts,
                rolledBalances = AccountingMath.rolledUp(accounts, balances),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    /** Persian error text, or null when [title] is usable as a child of [parentId] (ignoring the account [selfId]). */
    fun validateName(parentId: Long?, selfId: Long?, title: String): String? {
        val clean = title.trim()
        if (clean.isEmpty()) return ERROR_EMPTY
        val duplicate = state.value.accounts.any {
            it.parentId == parentId && it.id != selfId && it.title.trim() == clean
        }
        return if (duplicate) ERROR_DUPLICATE else null
    }

    fun addChild(parent: AccountEntity, title: String, openingBalance: Long) {
        save(
            AccountEntity(
                parentId = parent.id,
                title = title.trim(),
                kindCode = parent.kindCode,
                openingBalance = openingBalance,
            ),
        )
    }

    fun rename(account: AccountEntity, title: String) {
        save(account.copy(title = title.trim()))
    }

    fun setEnabled(account: AccountEntity, enabled: Boolean) {
        save(account.copy(isEnabled = enabled))
    }

    private fun save(account: AccountEntity) {
        viewModelScope.launch {
            try {
                repo.saveAccount(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "ذخیره‌ی حساب انجام نشد"
            }
        }
    }
}

@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val vm: AccountsViewModel = viewModel(key = "acc_accounts") {
        AccountsViewModel(ICoordinatorApp.instance.accountingRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    // roots are expanded by default, every other account collapsed; this set holds the ids the user flipped
    var toggled by remember { mutableStateOf(setOf<Long>()) }
    var addParent by remember { mutableStateOf<AccountEntity?>(null) }
    var renameTarget by remember { mutableStateOf<AccountEntity?>(null) }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbar.showSnackbar(text)
            vm.consumeMessage()
        }
    }

    val accounts = state.accounts
    val byId = remember(accounts) { accounts.associateBy { it.id } }
    val childrenOf = remember(accounts) {
        accounts.groupBy { a -> a.parentId?.takeIf { it in byId } }
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
    }
    val q = query.trim()
    val treeRows = remember(accounts, toggled) {
        buildList<Pair<AccountEntity, Int>> {
            fun walk(a: AccountEntity, depth: Int, path: MutableSet<Long>) {
                if (!path.add(a.id)) return
                add(a to depth)
                val expanded = (a.parentId == null) != (a.id in toggled)
                if (expanded) {
                    for (child in childrenOf[a.id].orEmpty()) walk(child, depth + 1, path)
                }
                path.remove(a.id)
            }
            for (root in childrenOf[null].orEmpty()) walk(root, 0, HashSet())
        }
    }
    val searchRows = remember(accounts, q) {
        if (q.isEmpty()) emptyList() else accounts.filter { it.title.contains(q, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            PageHeader(title = "مدیریت حساب‌ها", onBack = onBack)
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "جستجوی حساب",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            when {
                !state.loaded -> Unit
                accounts.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_tb_category,
                    title = "حسابی وجود ندارد",
                    message = "از صفحه‌ی خانه حساب‌های پایه را بسازید یا از پارمیس وارد کنید",
                )
                q.isNotEmpty() && searchRows.isEmpty() -> Text(
                    text = "حسابی پیدا نشد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val rows: List<Pair<AccountEntity, Int>> =
                        if (q.isEmpty()) treeRows else searchRows.map { it to 0 }
                    items(rows, key = { it.first.id }) { (account, depth) ->
                        val hasChildren = childrenOf[account.id].orEmpty().isNotEmpty()
                        val expanded = (account.parentId == null) != (account.id in toggled)
                        AccountRow(
                            account = account,
                            depth = depth,
                            subtitle = if (q.isEmpty()) null else AccountingFormat.accountPath(account.id, byId),
                            balanceText = MoneyFormat.format(state.rolledBalances[account.id] ?: 0L),
                            hasChildren = hasChildren && q.isEmpty(),
                            expanded = expanded,
                            onToggle = {
                                toggled = if (account.id in toggled) toggled - account.id else toggled + account.id
                            },
                            onAdd = { addParent = account },
                            onRename = { renameTarget = account },
                            onEnabledChange = { vm.setEnabled(account, it) },
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    val parent = addParent
    if (parent != null) {
        val kind = AccountKind.fromCode(parent.kindCode)
        NameDialog(
            title = "حساب جدید زیر " + parent.title,
            initialName = "",
            showOpeningBalance = kind in OPENING_KINDS,
            confirmText = "افزودن",
            validate = { vm.validateName(parent.id, null, it) },
            onConfirm = { name, balance ->
                vm.addChild(parent, name, balance)
                addParent = null
                // make the new child visible
                if (parent.parentId == null) {
                    if (parent.id in toggled) toggled = toggled - parent.id
                } else if (parent.id !in toggled) {
                    toggled = toggled + parent.id
                }
            },
            onDismiss = { addParent = null },
        )
    }

    val target = renameTarget
    if (target != null) {
        NameDialog(
            title = "تغییر نام حساب",
            initialName = target.title,
            showOpeningBalance = false,
            confirmText = "ذخیره",
            validate = { vm.validateName(target.parentId, target.id, it) },
            onConfirm = { name, _ ->
                vm.rename(target, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun AccountRow(
    account: AccountEntity,
    depth: Int,
    subtitle: String?,
    balanceText: String,
    hasChildren: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isRoot = account.parentId == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (account.isEnabled) 1f else 0.5f)
            .clickable(enabled = hasChildren, role = Role.Button, onClick = onToggle)
            .padding(start = (16 + depth * 16).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(
            icon = accountKindIcon(AccountKind.fromCode(account.kindCode)),
            size = 40.dp,
            iconSize = 22.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = account.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!account.isEnabled) {
                    Spacer(Modifier.width(6.dp))
                    TagText(text = "غیرفعال")
                }
            }
            if (!subtitle.isNullOrEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Text(text = balanceText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        if (hasChildren) {
            TbIcon(
                icon = R.drawable.ic_tb_chevron_left,
                contentDescription = if (expanded) "بستن" else "باز کردن",
                modifier = Modifier.rotate(if (expanded) 90f else 0f),
                tint = scheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAdd) {
            TbIcon(R.drawable.ic_tb_plus, "افزودن زیرحساب", tint = scheme.primary)
        }
        if (!isRoot) {
            IconButton(onClick = onRename) {
                TbIcon(R.drawable.ic_tb_pencil, "تغییر نام", tint = scheme.onSurfaceVariant)
            }
            Switch(checked = account.isEnabled, onCheckedChange = onEnabledChange)
        }
    }
}

/** Name (and optionally opening balance) dialog; [validate] returns a Persian error or null. */
@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    showOpeningBalance: Boolean,
    confirmText: String,
    validate: (String) -> String?,
    onConfirm: (name: String, openingBalance: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var balanceText by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = "نام حساب",
                    isError = nameError != null,
                    supportingText = nameError,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showOpeningBalance) {
                    AppTextField(
                        value = balanceText,
                        onValueChange = {
                            balanceText = it
                            balanceError = null
                        },
                        label = "موجودی اولیه",
                        isError = balanceError != null,
                        supportingText = balanceError,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val problem = validate(name)
                    val balance: Long? = if (!showOpeningBalance || balanceText.isBlank()) 0L else MoneyFormat.parse(balanceText)
                    if (problem != null) {
                        nameError = problem
                    } else if (balance == null) {
                        balanceError = "مبلغ نامعتبر است"
                    } else {
                        onConfirm(name.trim(), balance)
                    }
                },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
