package com.icoordinator.accounting

/** Catalog of icon names a user can pick for an account; each name N has drawable R.drawable.ic_tb_N. */
object AccountIcons {
    val NAMES: List<String> = listOf(
        "tools_kitchen_2", "shopping_cart", "basket", "coffee", "pizza", "home", "car", "bus", "gas_station", "plane", "beach",
        "shirt", "scissors", "pill", "stethoscope", "heart", "barbell", "baby_carriage", "paw", "gift", "bolt", "droplet", "flame",
        "wifi", "device_mobile", "phone", "school", "book", "movie", "music", "tool", "building_store", "briefcase", "user", "users",
        "receipt", "cash", "coin", "moneybag", "pig_money", "wallet", "building_bank", "credit_card", "chart_line",
        "calendar_stats", "category",
    )

    private val NAME_SET: Set<String> = NAMES.toHashSet()

    /** The icon of the account if its icon is in NAMES, else the nearest ancestor's icon that is in NAMES; null if none. Cycle-safe. */
    fun resolve(accountId: Long, byId: Map<Long, AccountEntity>): String? {
        val seen = HashSet<Long>()
        var cur: AccountEntity? = byId[accountId]
        while (cur != null && seen.add(cur.id)) {
            if (cur.icon in NAME_SET) return cur.icon
            val p = cur.parentId ?: return null
            cur = byId[p]
        }
        return null
    }
}
