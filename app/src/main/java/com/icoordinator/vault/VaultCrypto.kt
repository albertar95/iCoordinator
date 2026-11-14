package com.icoordinator.vault

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultPayload(
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val note: String = "",
)

interface EntryCipher {
    fun encrypt(plain: ByteArray): ByteArray
    /** Throws [VaultCryptoException] on any failure. */
    fun decrypt(blob: ByteArray): ByteArray
}

class VaultCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** AES-256-GCM, blob = [1 byte version=1][12 byte random IV][ciphertext+16 byte tag]. */
class AesGcmCipher(private val key: SecretKey) : EntryCipher {
    private val random = SecureRandom()

    override fun encrypt(plain: ByteArray): ByteArray {
        try {
            val iv = ByteArray(IV_LEN)
            random.nextBytes(iv)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            val ct = cipher.doFinal(plain)
            val out = ByteArray(1 + IV_LEN + ct.size)
            out[0] = VERSION
            System.arraycopy(iv, 0, out, 1, IV_LEN)
            System.arraycopy(ct, 0, out, 1 + IV_LEN, ct.size)
            return out
        } catch (e: GeneralSecurityException) {
            throw VaultCryptoException("encrypt failed", e)
        }
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size < 1 + IV_LEN + TAG_BYTES) throw VaultCryptoException("blob too short")
        if (blob[0] != VERSION) throw VaultCryptoException("unsupported blob version")
        try {
            val iv = blob.copyOfRange(1, 1 + IV_LEN)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(blob, 1 + IV_LEN, blob.size - 1 - IV_LEN)
        } catch (e: GeneralSecurityException) {
            throw VaultCryptoException("decrypt failed", e)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_LEN = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = 16
    }
}

/** Token writer/reader: a token is "<n>:<chars>" where n is the decimal UTF-16 length of chars. */
private class TokenWriter {
    private val sb = StringBuilder()
    fun raw(s: String) = apply { sb.append(s) }
    fun token(s: String) = apply { sb.append(s.length).append(':').append(s) }
    fun token(n: Int) = token(n.toString())
    fun toBytes(): ByteArray = sb.toString().toByteArray(Charsets.UTF_8)
}

private class TokenReader(bytes: ByteArray) {
    private val s: String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        throw VaultCryptoException("malformed data")
    }
    private var pos = 0

    fun expectRaw(prefix: String) {
        if (!s.startsWith(prefix, pos)) throw VaultCryptoException("malformed data")
        pos += prefix.length
    }

    fun token(): String {
        var n = 0
        var digits = 0
        while (pos < s.length && s[pos] in '0'..'9') {
            n = n * 10 + (s[pos] - '0')
            pos++
            digits++
            if (digits > 9) throw VaultCryptoException("malformed data")
        }
        if (digits == 0 || pos >= s.length || s[pos] != ':') throw VaultCryptoException("malformed data")
        pos++
        if (n > s.length - pos) throw VaultCryptoException("malformed data")
        val out = s.substring(pos, pos + n)
        pos += n
        return out
    }

    fun intToken(): Int = token().toIntOrNull()?.takeIf { it >= 0 } ?: throw VaultCryptoException("malformed data")

    fun end() {
        if (pos != s.length) throw VaultCryptoException("malformed data")
    }
}

object VaultCodec {
    fun encodePayload(p: VaultPayload): ByteArray =
        TokenWriter().raw("v1").token(p.title).token(p.username).token(p.password).token(p.url).token(p.note).toBytes()

    fun decodePayload(bytes: ByteArray): VaultPayload {
        val r = TokenReader(bytes)
        r.expectRaw("v1")
        val p = VaultPayload(r.token(), r.token(), r.token(), r.token(), r.token())
        r.end()
        return p
    }

    fun encodeName(name: String): ByteArray = TokenWriter().raw("n1").token(name).toBytes()

    fun decodeName(bytes: ByteArray): String {
        val r = TokenReader(bytes)
        r.expectRaw("n1")
        val name = r.token()
        r.end()
        return name
    }
}

data class BackupFolder(val key: Int, val name: String)
data class BackupEntry(val folderKey: Int?, val payload: VaultPayload)
data class VaultBackupData(val folders: List<BackupFolder>, val entries: List<BackupEntry>)

sealed class BackupReadResult {
    data class Ok(val data: VaultBackupData) : BackupReadResult()
    object WrongPassword : BackupReadResult()
    object NotAVaultFile : BackupReadResult()
    object Corrupt : BackupReadResult()
}

object VaultBackupFormat {
    const val MAGIC = "ICVAULT1"
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD = 8

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val HEADER_LEN = 8 + 4 + SALT_LEN + IV_LEN
    private const val TAG_BYTES = 16
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 5_000_000

    fun write(
        data: VaultBackupData,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
        iterations: Int = ITERATIONS,
    ): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC.toByteArray(Charsets.US_ASCII))
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .array()
        val body = encodeBody(data)
        val keyBytes = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            val ct = cipher.doFinal(body)
            return header + ct
        } catch (e: GeneralSecurityException) {
            throw VaultCryptoException("backup encrypt failed", e)
        } finally {
            Arrays.fill(keyBytes, 0)
            Arrays.fill(body, 0)
        }
    }

    fun read(bytes: ByteArray, password: CharArray): BackupReadResult {
        try {
            if (bytes.size < HEADER_LEN) return BackupReadResult.NotAVaultFile
            val magic = String(bytes, 0, 8, Charsets.US_ASCII)
            if (magic != MAGIC) return BackupReadResult.NotAVaultFile
            val buf = ByteBuffer.wrap(bytes, 8, 4)
            val iterations = buf.int
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) return BackupReadResult.Corrupt
            if (bytes.size < HEADER_LEN + TAG_BYTES) return BackupReadResult.Corrupt
            val salt = bytes.copyOfRange(12, 12 + SALT_LEN)
            val iv = bytes.copyOfRange(12 + SALT_LEN, HEADER_LEN)
            val keyBytes = deriveKey(password, salt, iterations)
            val body: ByteArray
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
                cipher.updateAAD(bytes, 0, HEADER_LEN)
                body = cipher.doFinal(bytes, HEADER_LEN, bytes.size - HEADER_LEN)
            } catch (e: AEADBadTagException) {
                return BackupReadResult.WrongPassword
            } finally {
                Arrays.fill(keyBytes, 0)
            }
            try {
                return BackupReadResult.Ok(decodeBody(body))
            } finally {
                Arrays.fill(body, 0)
            }
        } catch (e: VaultCryptoException) {
            return BackupReadResult.Corrupt
        } catch (e: Throwable) {
            return BackupReadResult.Corrupt
        }
    }

    fun passwordProblem(password: CharArray, repeat: CharArray): String? = when {
        password.size < MIN_PASSWORD -> "رمز اصلی باید حداقل ۸ نویسه باشد"
        !password.contentEquals(repeat) -> "تکرار رمز اصلی یکسان نیست"
        else -> null
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (e: GeneralSecurityException) {
            throw VaultCryptoException("key derivation failed", e)
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodeBody(d: VaultBackupData): ByteArray {
        val w = TokenWriter().raw("B1").token(d.folders.size)
        for (f in d.folders) w.token(f.key).token(f.name)
        w.token(d.entries.size)
        for (e in d.entries) {
            w.token(e.folderKey?.toString() ?: "")
                .token(e.payload.title).token(e.payload.username).token(e.payload.password)
                .token(e.payload.url).token(e.payload.note)
        }
        return w.toBytes()
    }

    private fun decodeBody(bytes: ByteArray): VaultBackupData {
        val r = TokenReader(bytes)
        r.expectRaw("B1")
        val folderCount = r.intToken()
        val folders = ArrayList<BackupFolder>()
        repeat(folderCount) { folders.add(BackupFolder(r.intToken(), r.token())) }
        val entryCount = r.intToken()
        val entries = ArrayList<BackupEntry>()
        repeat(entryCount) {
            val fk = r.token().let { if (it.isEmpty()) null else it.toIntOrNull() ?: throw VaultCryptoException("malformed data") }
            entries.add(BackupEntry(fk, VaultPayload(r.token(), r.token(), r.token(), r.token(), r.token())))
        }
        r.end()
        return VaultBackupData(folders, entries)
    }
}
