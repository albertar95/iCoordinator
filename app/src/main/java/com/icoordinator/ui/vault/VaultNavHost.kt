package com.icoordinator.ui.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.icoordinator.security.LockManager
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.lock.LockScreen
import com.icoordinator.vault.SecureScreen
import com.icoordinator.vault.VaultSession

/** Routes of the vault section. Only ids travel in routes (-1 = none), never vault data. */
object VaultRoutes {
    const val LIST = "vault_list"
    const val ENTRY = "vault_entry/{id}?folder={folder}"

    fun entry(id: Long?, folderId: Long?): String = "vault_entry/${id ?: -1L}?folder=${folderId ?: -1L}"
}

/**
 * Password manager section: asks the app PIN / biometric on every entry (VaultSession), blocks screenshots
 * while it is shown (SecureScreen) and locks again when it is left.
 */
@Composable
fun VaultNavHost(onExit: () -> Unit) {
    val context = LocalContext.current
    val lock = remember { LockManager(context) }
    val activity = remember(context) { context.findActivity() }
    val vm: VaultViewModel = viewModel(key = "vault")
    val unlocked by VaultSession.unlocked.collectAsState()

    DisposableEffect(Unit) {
        SecureScreen.acquire()
        onDispose {
            SecureScreen.release()
            // rotation recreates the screen; only a real exit from the section locks it (D78)
            if (activity?.isChangingConfigurations != true) VaultSession.lock()
        }
    }

    LaunchedEffect(unlocked) {
        if (unlocked) vm.start() else vm.stop()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (unlocked) {
            Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                VaultUnlockedHost(vm = vm, onExit = onExit)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                PageHeader(title = "مدیریت رمزها", onBack = onExit)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LockScreen(lock) { VaultSession.unlock() }
                }
            }
        }
    }
}

// The inner NavController lives only while unlocked, so locking always returns to the list after the next unlock.
@Composable
private fun VaultUnlockedHost(vm: VaultViewModel, onExit: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = VaultRoutes.LIST) {
        composable(VaultRoutes.LIST) {
            VaultListScreen(
                vm = vm,
                onExit = onExit,
                onOpenEntry = { id, folderId ->
                    navController.navigate(VaultRoutes.entry(id, folderId)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = VaultRoutes.ENTRY,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("folder") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val rawId = backStackEntry.arguments?.getLong("id") ?: -1L
            val rawFolder = backStackEntry.arguments?.getLong("folder") ?: -1L
            VaultEntryScreen(
                vm = vm,
                entryId = if (rawId == -1L) null else rawId,
                initialFolderId = if (rawFolder == -1L) null else rawFolder,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
