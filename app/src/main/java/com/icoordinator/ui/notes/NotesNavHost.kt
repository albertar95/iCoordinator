package com.icoordinator.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object NotesRoutes {
    const val LIST = "notes_list"
    const val EDITOR = "note_editor/{id}?folder={folder}"

    /** noteId null = new note (-1); folderId null = no folder (-1). */
    fun editor(noteId: Long? = null, folderId: Long? = null): String =
        "note_editor/${noteId ?: -1L}?folder=${folderId ?: -1L}"
}

@Composable
fun NotesNavHost(onExit: () -> Unit) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = NotesRoutes.LIST,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        composable(NotesRoutes.LIST) {
            NotesListScreen(
                onExit = onExit,
                onOpenNote = { id, folderId -> navController.navigate(NotesRoutes.editor(id, folderId)) },
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
