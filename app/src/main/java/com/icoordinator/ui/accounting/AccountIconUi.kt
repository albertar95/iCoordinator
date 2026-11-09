package com.icoordinator.ui.accounting

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.icoordinator.R
import com.icoordinator.accounting.AccountEntity
import com.icoordinator.accounting.AccountIcons
import com.icoordinator.accounting.AccountKind
import com.icoordinator.ui.components.IconCircle

/** R.drawable id for an AccountIcons name, null if unknown. */
@DrawableRes
fun iconRes(name: String): Int? = when (name) {
    "tools_kitchen_2" -> R.drawable.ic_tb_tools_kitchen_2
    "shopping_cart" -> R.drawable.ic_tb_shopping_cart
    "basket" -> R.drawable.ic_tb_basket
    "coffee" -> R.drawable.ic_tb_coffee
    "pizza" -> R.drawable.ic_tb_pizza
    "home" -> R.drawable.ic_tb_home
    "car" -> R.drawable.ic_tb_car
    "bus" -> R.drawable.ic_tb_bus
    "gas_station" -> R.drawable.ic_tb_gas_station
    "plane" -> R.drawable.ic_tb_plane
    "beach" -> R.drawable.ic_tb_beach
    "shirt" -> R.drawable.ic_tb_shirt
    "scissors" -> R.drawable.ic_tb_scissors
    "pill" -> R.drawable.ic_tb_pill
    "stethoscope" -> R.drawable.ic_tb_stethoscope
    "heart" -> R.drawable.ic_tb_heart
    "barbell" -> R.drawable.ic_tb_barbell
    "baby_carriage" -> R.drawable.ic_tb_baby_carriage
    "paw" -> R.drawable.ic_tb_paw
    "gift" -> R.drawable.ic_tb_gift
    "bolt" -> R.drawable.ic_tb_bolt
    "droplet" -> R.drawable.ic_tb_droplet
    "flame" -> R.drawable.ic_tb_flame
    "wifi" -> R.drawable.ic_tb_wifi
    "device_mobile" -> R.drawable.ic_tb_device_mobile
    "phone" -> R.drawable.ic_tb_phone
    "school" -> R.drawable.ic_tb_school
    "book" -> R.drawable.ic_tb_book
    "movie" -> R.drawable.ic_tb_movie
    "music" -> R.drawable.ic_tb_music
    "tool" -> R.drawable.ic_tb_tool
    "building_store" -> R.drawable.ic_tb_building_store
    "briefcase" -> R.drawable.ic_tb_briefcase
    "user" -> R.drawable.ic_tb_user
    "users" -> R.drawable.ic_tb_users
    "receipt" -> R.drawable.ic_tb_receipt
    "cash" -> R.drawable.ic_tb_cash
    "coin" -> R.drawable.ic_tb_coin
    "moneybag" -> R.drawable.ic_tb_moneybag
    "pig_money" -> R.drawable.ic_tb_pig_money
    "wallet" -> R.drawable.ic_tb_wallet
    "building_bank" -> R.drawable.ic_tb_building_bank
    "credit_card" -> R.drawable.ic_tb_credit_card
    "chart_line" -> R.drawable.ic_tb_chart_line
    "calendar_stats" -> R.drawable.ic_tb_calendar_stats
    "category" -> R.drawable.ic_tb_category
    else -> null
}

/** Icon of an account: AccountIcons.resolve -> iconRes, else accountKindIcon(kind of the account), else ic_tb_category. */
@DrawableRes
fun accountIcon(accountId: Long, byId: Map<Long, AccountEntity>): Int {
    val chosen = AccountIcons.resolve(accountId, byId)?.let { iconRes(it) }
    if (chosen != null) return chosen
    val account = byId[accountId]
    return if (account != null) accountKindIcon(AccountKind.fromCode(account.kindCode)) else R.drawable.ic_tb_category
}

/** Grid (5 columns) of all AccountIcons.NAMES plus a first cell "پیش‌فرض" (returns null = inherit). */
@Composable
fun IconPickerDialog(selected: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val options: List<String?> = listOf<String?>(null) + AccountIcons.NAMES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب آیکون") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().height(300.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(options) { name ->
                    val isSelected = if (name == null) selected.isNullOrEmpty() else name == selected
                    val cellModifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(if (isSelected) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)
                        .clickable(role = Role.Button) { onPick(name) }
                    val res = name?.let { iconRes(it) }
                    if (res != null) {
                        IconCircle(icon = res, modifier = cellModifier, size = 48.dp, iconSize = 24.dp)
                    } else {
                        Box(
                            modifier = cellModifier.background(scheme.surfaceContainer).padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "پیش‌فرض",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
