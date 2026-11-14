package com.icoordinator.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object NotesRoutes {
    const val LIST = "notes_list"
    const val FOLDER = "notes_folder/{folderId}"
    const val EDITOR = "note_editor/{id}?folder={folder}"

    fun folder(folderId: Long): String = "notes_folder/$folderId"

    /** noteId null = new note (-1); folderId null = no folder (-1). */
    fun editor(noteId: Long? = null, folderId: Long? = null): String =
        "note_editor/${noteId ?: -1L}?folder=${folderId ?: -1L}"
}

@Composable
fun NotesNavHost(onExit: () -> Unit) {
    val navController = rememberNavController()
    // one ViewModel for the main page and the folder pages
    val vm: NotesViewModel = viewModel(key = "notes")
    NavHost(
        navController = navController,
        startDestination = NotesRoutes.LIST,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        composable(NotesRoutes.LIST) {
            NotesListScreen(
                vm = vm,
                onExit = onExit,
                onOpenNote = { id, folderId -> navController.navigate(NotesRoutes.editor(id, folderId)) },
                onOpenFolder = { folderId -> navController.navigate(NotesRoutes.folder(folderId)) },
            )
        }
        composable(
            NotesRoutes.FOLDER,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType }),
        ) { entry ->
            val folderId = entry.arguments?.getLong("folderId") ?: -1L
            NotesFolderScreen(
                vm = vm,
                folderId = folderId,
                // guarded: the screen can ask twice (after delete and when the folder disappears)
                onBack = {
                    if (navController.currentBackStackEntry?.destination?.route == NotesRoutes.FOLDER) {
                        navController.popBackStack()
                    }
                },
                onOpenNote = { id, fid -> navController.navigate(NotesRoutes.editor(id, fid)) },
            )
        }
        composable(
            NotesRoutes.EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("folder") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            val folder = entry.arguments?.getLong("folder") ?: -1L
            NoteEditorScreen(
                noteId = id.takeIf { it > 0L },
                folderId = folder.takeIf { it > 0L },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
