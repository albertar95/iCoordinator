package com.icoordinator.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reference-counted request for "no screenshots" (FLAG_SECURE). MainActivity combines [forced] with the
 * user's screenshot setting. release() never goes below zero.
 */
object SecureScreen {
    private val guard = Any()
    private var count = 0
    private val _forced = MutableStateFlow(false)

    val forced: StateFlow<Boolean> = _forced.asStateFlow()

    fun acquire() {
        synchronized(guard) {
            count++
            _forced.value = true
        }
    }

    fun release() {
        synchronized(guard) {
            if (count > 0) count--
            _forced.value = count > 0
        }
    }
}

/** Whether the password manager is unlocked right now. Starts locked; MainActivity and the vault screens lock it again. */
object VaultSession {
    private val _unlocked = MutableStateFlow(false)

    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}
