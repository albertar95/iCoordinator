package com.icoordinator.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.icoordinator.ICoordinatorApp
import com.icoordinator.vault.VaultEntry
import com.icoordinator.vault.VaultPayload
import com.icoordinator.vault.VaultSession
import com.icoordinator.vault.VaultSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the decrypted vault snapshot in memory, and only while the vault is unlocked.
 * start() is called after the unlock, stop() on lock; the snapshot is then dropped.
 * Never logs or prints anything from the vault, and error messages carry no vault data.
 */
class VaultViewModel : ViewModel() {

    private val app = ICoordinatorApp.instance

    private val _snapshot = MutableStateFlow<VaultSnapshot?>(null)

    /** null until the first decrypted snapshot after start(); stays null while locked. */
    val snapshot: StateFlow<VaultSnapshot?> = _snapshot.asStateFlow()

    private val _loadError = MutableStateFlow(false)

    /** true when collecting the repository snapshot failed; reset by start(). */
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private var job: Job? = null

    init {
        // drop the decrypted data as soon as the session locks, even if the UI is not composing (activity stopped)
        viewModelScope.launch {
            VaultSession.unlocked.collect { unlocked -> if (!unlocked) stop() }
        }
    }

    /** Begins collecting the repository snapshot (idempotent). */
    fun start() {
        if (job?.isActive == true) return
        _loadError.value = false
        job = viewModelScope.launch {
            try {
                app.vaultRepository.observeSnapshot().collect { _snapshot.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snapshot.value = null
                _loadError.value = true
            }
        }
    }

    /** Starts over after a failed load. */
    fun retry() {
        stop()
        start()
    }

    /** Cancels the collection and forgets the decrypted data. */
    fun stop() {
        job?.cancel()
        job = null
        _snapshot.value = null
        _loadError.value = false
    }

    fun entry(id: Long): VaultEntry? = _snapshot.value?.entries?.firstOrNull { it.id == id }

    /** onResult gets null on success, otherwise a Persian error text. */
    fun saveEntry(id: Long?, folderId: Long?, payload: VaultPayload, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error: String? = try {
                withContext(Dispatchers.IO) {
                    app.vaultRepository.saveEntry(id, folderId, payload, System.currentTimeMillis())
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is IllegalArgumentException && e.message == "title") MSG_TITLE else MSG_SAVE_FAILED
            }
            onResult(error)
        }
    }

    fun deleteEntry(id: Long) {
        launchQuietly { app.vaultRepository.deleteEntry(id) }
    }

    fun addFolder(name: String, onResult: (String?) -> Unit) {
        folderCall(onResult) { app.vaultRepository.addFolder(name) }
    }

    fun renameFolder(id: Long, name: String, onResult: (String?) -> Unit) {
        folderCall(onResult) { app.vaultRepository.renameFolder(id, name) }
    }

    fun deleteFolder(id: Long) {
        launchQuietly { app.vaultRepository.deleteFolder(id) }
    }

    private fun folderCall(onResult: (String?) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            val error: String? = try {
                withContext(Dispatchers.IO) { block() }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is IllegalArgumentException) MSG_FOLDER_NAME else MSG_SAVE_FAILED
            }
            onResult(error)
        }
    }

    private fun launchQuietly(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the snapshot flow shows the real state; nothing is logged
            }
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private companion object {
        const val MSG_TITLE = "عنوان را وارد کن"
        const val MSG_FOLDER_NAME = "نام پوشه را وارد کن"
        const val MSG_SAVE_FAILED = "ذخیره ممکن نشد"
    }
}
