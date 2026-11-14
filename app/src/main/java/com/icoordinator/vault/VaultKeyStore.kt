package com.icoordinator.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM key in Android Keystore (D76). The key never leaves the Keystore and is created lazily on first use.
 * Blob layout is the same as [AesGcmCipher]: [1 byte version = 1][12 byte IV][ciphertext + 16 byte tag].
 * The Keystore forbids a caller-supplied IV, so the IV is taken from the Cipher after init.
 */
object VaultKeyStore {
    const val ALIAS = "icoordinator_vault_v1"

    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION: Byte = 1
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = 16

    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    /** The key is fetched/created lazily inside encrypt/decrypt, not here. */
    fun cipher(): EntryCipher = KeystoreCipher

    private fun key(): SecretKey {
        val cached = cachedKey
        if (cached != null) return cached
        synchronized(lock) {
            val again = cachedKey
            if (again != null) return again
            try {
                val store = KeyStore.getInstance(PROVIDER)
                store.load(null)
                val existing = store.getKey(ALIAS, null) as? SecretKey
                val key = existing ?: createKey()
                cachedKey = key
                return key
            } catch (e: VaultCryptoException) {
                throw e
            } catch (e: Exception) {
                throw VaultCryptoException("keystore unavailable", e)
            }
        }
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(spec)
        return generator.generateKey()
    }

    private object KeystoreCipher : EntryCipher {
        override fun encrypt(plain: ByteArray): ByteArray {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val iv = cipher.iv
                if (iv == null || iv.size != IV_LEN) throw VaultCryptoException("unexpected iv")
                val ct = cipher.doFinal(plain)
                val out = ByteArray(1 + IV_LEN + ct.size)
                out[0] = VERSION
                System.arraycopy(iv, 0, out, 1, IV_LEN)
                System.arraycopy(ct, 0, out, 1 + IV_LEN, ct.size)
                return out
            } catch (e: VaultCryptoException) {
                throw e
            } catch (e: Exception) {
                throw VaultCryptoException("encrypt failed", e)
            }
        }

        override fun decrypt(blob: ByteArray): ByteArray {
            if (blob.size < 1 + IV_LEN + TAG_BYTES) throw VaultCryptoException("blob too short")
            if (blob[0] != VERSION) throw VaultCryptoException("unsupported blob version")
            try {
                val iv = blob.copyOfRange(1, 1 + IV_LEN)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                return cipher.doFinal(blob, 1 + IV_LEN, blob.size - 1 - IV_LEN)
            } catch (e: VaultCryptoException) {
                throw e
            } catch (e: Exception) {
                throw VaultCryptoException("decrypt failed", e)
            }
        }
    }
}
