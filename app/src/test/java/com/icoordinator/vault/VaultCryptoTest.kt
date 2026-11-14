package com.icoordinator.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.KeyGenerator

class VaultCryptoTest {
    private val iters = 10_000

    private fun newCipher(): AesGcmCipher {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256)
        return AesGcmCipher(kg.generateKey())
    }

    private fun assertCryptoFails(block: () -> Unit) {
        try {
            block()
            fail("expected VaultCryptoException")
        } catch (e: VaultCryptoException) {
            // expected
        }
    }

    private val sample = VaultBackupData(
        listOf(BackupFolder(1, "بانک"), BackupFolder(2, "شبکه‌های اجتماعی")),
        listOf(
            BackupEntry(1, VaultPayload("کارت", "ali", "p@ss:۱۲۳", "https://x.ir", "یادداشت\nدو خطی 😀")),
            BackupEntry(null, VaultPayload("بدون پوشه")),
            BackupEntry(2, VaultPayload("t", "u", "pw", "url", "note")),
        ),
    )
    private val pw = "master-password-1".toCharArray()

    // ---- cipher ----
    @Test fun cipherRoundTrip() {
        val c = newCipher()
        val plain = "راز 123".toByteArray()
        val blob = c.encrypt(plain)
        assertEquals(1.toByte(), blob[0])
        assertEquals(1 + 12 + plain.size + 16, blob.size)
        assertArrayEquals(plain, c.decrypt(blob))
    }

    @Test fun twoEncryptionsDiffer() {
        val c = newCipher()
        val p = "same".toByteArray()
        assertFalse(c.encrypt(p).contentEquals(c.encrypt(p)))
    }

    @Test fun tamperedByteFails() {
        val c = newCipher()
        val blob = c.encrypt("hello".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertCryptoFails { c.decrypt(blob) }
    }

    @Test fun shortBlobFails() {
        assertCryptoFails { newCipher().decrypt(ByteArray(28)) }
        assertCryptoFails { newCipher().decrypt(ByteArray(0)) }
    }

    @Test fun wrongVersionFails() {
        val c = newCipher()
        val blob = c.encrypt("x".toByteArray())
        blob[0] = 2
        assertCryptoFails { c.decrypt(blob) }
    }

    @Test fun wrongKeyFails() {
        val blob = newCipher().encrypt("x".toByteArray())
        assertCryptoFails { newCipher().decrypt(blob) }
    }

    // ---- payload codec ----
    @Test fun payloadExampleFormat() {
        assertEquals("v11:a1:b3:c:10:0:", String(VaultCodec.encodePayload(VaultPayload("a", "b", "c:1", "", "")), Charsets.UTF_8))
    }

    @Test fun payloadRoundTripTricky() {
        val p = VaultPayload("بانک ملی", "user:12", "9:abc:0:", "http://a/b?c=1:2", "خط۱\nخط۲ 😀🔑")
        assertEquals(p, VaultCodec.decodePayload(VaultCodec.encodePayload(p)))
    }

    @Test fun payloadRoundTripEmptyFields() {
        val p = VaultPayload("t")
        assertEquals(p, VaultCodec.decodePayload(VaultCodec.encodePayload(p)))
    }

    @Test fun malformedPayloadThrows() {
        assertCryptoFails { VaultCodec.decodePayload("v1".toByteArray()) }
        assertCryptoFails { VaultCodec.decodePayload("x11:a1:b1:c1:d1:e".toByteArray()) }
        assertCryptoFails { VaultCodec.decodePayload("v15:ab1:b1:c1:d1:e".toByteArray()) }
        assertCryptoFails { VaultCodec.decodePayload("v11:a1:b1:c1:d1:eX".toByteArray()) }
        assertCryptoFails { VaultCodec.decodePayload(byteArrayOf(0x76, 0x31, 0xC3.toByte())) }
        assertCryptoFails { VaultCodec.decodePayload(ByteArray(0)) }
    }

    @Test fun nameCodecRoundTrip() {
        assertEquals("بانک:۱ 😀", VaultCodec.decodeName(VaultCodec.encodeName("بانک:۱ 😀")))
        assertEquals("", VaultCodec.decodeName(VaultCodec.encodeName("")))
        assertCryptoFails { VaultCodec.decodeName("n1".toByteArray()) }
        assertCryptoFails { VaultCodec.decodeName("v11:a".toByteArray()) }
    }

    // ---- backup ----
    @Test fun backupRoundTrip() {
        val bytes = VaultBackupFormat.write(sample, pw, iterations = iters)
        assertEquals("ICVAULT1", String(bytes, 0, 8, Charsets.US_ASCII))
        val r = VaultBackupFormat.read(bytes, pw)
        assertTrue(r is BackupReadResult.Ok)
        assertEquals(sample, (r as BackupReadResult.Ok).data)
    }

    @Test fun backupEmptyData() {
        val empty = VaultBackupData(emptyList(), emptyList())
        val r = VaultBackupFormat.read(VaultBackupFormat.write(empty, pw, iterations = iters), pw)
        assertEquals(BackupReadResult.Ok(empty), r)
    }

    @Test fun backupNullFolderKeyRoundTrip() {
        val d = VaultBackupData(emptyList(), listOf(BackupEntry(null, VaultPayload("a"))))
        val r = VaultBackupFormat.read(VaultBackupFormat.write(d, pw, iterations = iters), pw) as BackupReadResult.Ok
        assertNull(r.data.entries.single().folderKey)
    }

    @Test fun backupWrongPassword() {
        val bytes = VaultBackupFormat.write(sample, pw, iterations = iters)
        assertEquals(BackupReadResult.WrongPassword, VaultBackupFormat.read(bytes, "another-password".toCharArray()))
    }

    @Test fun randomBytesNotAVaultFile() {
        val junk = ByteArray(500).also { SecureRandom().nextBytes(it) }
        junk[0] = 0
        assertEquals(BackupReadResult.NotAVaultFile, VaultBackupFormat.read(junk, pw))
        assertEquals(BackupReadResult.NotAVaultFile, VaultBackupFormat.read(ByteArray(3), pw))
        assertEquals(BackupReadResult.NotAVaultFile, VaultBackupFormat.read(ByteArray(0), pw))
    }

    @Test fun truncatedAfterHeaderNotOk() {
        val bytes = VaultBackupFormat.write(sample, pw, iterations = iters)
        assertFalse(VaultBackupFormat.read(bytes.copyOf(40), pw) is BackupReadResult.Ok)
        assertFalse(VaultBackupFormat.read(bytes.copyOf(bytes.size - 5), pw) is BackupReadResult.Ok)
    }

    @Test fun flippedIterationsNotOk() {
        val bytes = VaultBackupFormat.write(sample, pw, iterations = iters)
        val a = bytes.copyOf(); a[8] = (a[8].toInt() xor 0x7F).toByte()      // iterations out of range -> Corrupt
        assertFalse(VaultBackupFormat.read(a, pw) is BackupReadResult.Ok)
        val b = bytes.copyOf(); b[11] = (b[11].toInt() xor 1).toByte()       // still in range, AAD/key mismatch
        assertFalse(VaultBackupFormat.read(b, pw) is BackupReadResult.Ok)
    }

    @Test fun iterationsOutOfRangeIsCorrupt() {
        val bytes = VaultBackupFormat.write(sample, pw, iterations = iters)
        val low = bytes.copyOf(); low[8] = 0; low[9] = 0; low[10] = 0; low[11] = 5
        assertEquals(BackupReadResult.Corrupt, VaultBackupFormat.read(low, pw))
    }

    @Test fun tamperedHeaderSaltNotOk() {
        val bytes = VaultBackupFormat.write(sample, pw, iterations = iters)
        bytes[20] = (bytes[20].toInt() xor 1).toByte()
        assertFalse(VaultBackupFormat.read(bytes, pw) is BackupReadResult.Ok)
    }

    @Test fun backupDoesNotContainPlaintext() {
        val secret = "TopSecret-Passw0rd!"
        val d = VaultBackupData(emptyList(), listOf(BackupEntry(null, VaultPayload("title-xyz", "usr", secret))))
        val bytes = VaultBackupFormat.write(d, pw, iterations = iters)
        assertFalse(indexOf(bytes, secret.toByteArray(Charsets.UTF_8)) >= 0)
        assertFalse(indexOf(bytes, "title-xyz".toByteArray(Charsets.UTF_8)) >= 0)
    }

    @Test fun twoBackupsDiffer() {
        val a = VaultBackupFormat.write(sample, pw, iterations = iters)
        val b = VaultBackupFormat.write(sample, pw, iterations = iters)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test fun passwordProblemCases() {
        assertEquals("رمز اصلی باید حداقل ۸ نویسه باشد", VaultBackupFormat.passwordProblem("short".toCharArray(), "short".toCharArray()))
        assertEquals("تکرار رمز اصلی یکسان نیست", VaultBackupFormat.passwordProblem("longenough1".toCharArray(), "longenough2".toCharArray()))
        assertNull(VaultBackupFormat.passwordProblem("longenough1".toCharArray(), "longenough1".toCharArray()))
        assertNull(VaultBackupFormat.passwordProblem("12345678".toCharArray(), "12345678".toCharArray()))
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
