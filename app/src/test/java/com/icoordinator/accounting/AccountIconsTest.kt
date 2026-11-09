package com.icoordinator.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccountIconsTest {
    private fun acc(id: Long, parent: Long?, icon: String = "") =
        AccountEntity(id = id, parentId = parent, title = "a$id", kindCode = 1, icon = icon)

    private fun map(vararg a: AccountEntity) = a.associateBy { it.id }

    @Test fun ownIcon() {
        val m = map(acc(1, null, "car"), acc(2, 1, "home"))
        assertEquals("home", AccountIcons.resolve(2, m))
    }

    @Test fun inheritedFromParentExample() {
        val m = map(acc(1, null, "tools_kitchen_2"), acc(2, 1, ""))
        assertEquals("tools_kitchen_2", AccountIcons.resolve(2, m))
    }

    @Test fun inheritedFromGrandparent() {
        val m = map(acc(1, null, "bus"), acc(2, 1, ""), acc(3, 2, ""))
        assertEquals("bus", AccountIcons.resolve(3, m))
    }

    @Test fun unknownNameIgnored() {
        val m = map(acc(1, null, "coffee"), acc(2, 1, "Parmis_123"))
        assertEquals("coffee", AccountIcons.resolve(2, m))
        assertNull(AccountIcons.resolve(2, map(acc(1, null, "zzz"), acc(2, 1, "yyy"))))
    }

    @Test fun cycleIsSafe() {
        val m = map(acc(1, 2), acc(2, 1))
        assertNull(AccountIcons.resolve(1, m))
        val m2 = map(acc(1, 2), acc(2, 1, "gift"))
        assertEquals("gift", AccountIcons.resolve(1, m2))
    }

    @Test fun unknownIdAndMissingParent() {
        assertNull(AccountIcons.resolve(99, map(acc(1, null, "car"))))
        assertNull(AccountIcons.resolve(2, map(acc(2, 77))))
    }

    @Test fun namesCatalogMatchesDrawables() {
        assertEquals(46, AccountIcons.NAMES.size)
        assertEquals(46, AccountIcons.NAMES.toSet().size)
        // Gradle runs unit tests from the module folder (app/); the local check runs from the work root
        val dir = listOf("src/main/res/drawable", "app/src/main/res/drawable", "project/app/src/main/res/drawable")
            .map { File(it) }.firstOrNull { it.isDirectory } ?: return
        for (n in AccountIcons.NAMES) {
            val f = File(dir, "ic_tb_$n.xml")
            assertTrue("missing drawable for $n", f.exists())
        }
    }
}
