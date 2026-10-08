package foo.pilz.freaklog.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.GeneralSecurityException

class ExportEncryptionTest {

    private val encryption = ExportEncryption()
    private val plaintext = """{"experiences":[]}""".toByteArray()

    @Test
    fun `round trip with a chosen passphrase returns the plaintext`() {
        val encrypted = encryption.encrypt(plaintext, "correct horse")
        assertArrayEquals(plaintext, encryption.decrypt(encrypted, "correct horse"))
    }

    @Test
    fun `round trip with a generated passphrase returns the plaintext`() {
        val export = encryption.encrypt(plaintext)
        assertEquals(6, export.passphrase.split("-").size)
        assertArrayEquals(plaintext, encryption.decrypt(export.data, export.passphrase))
    }

    @Test
    fun `surrounding whitespace in the typed passphrase is ignored`() {
        val encrypted = encryption.encrypt(plaintext, "secret")
        assertArrayEquals(plaintext, encryption.decrypt(encrypted, "  secret\n"))
    }

    @Test
    fun `ciphertext does not contain the plaintext and differs per call`() {
        val first = encryption.encrypt(plaintext, "secret")
        val second = encryption.encrypt(plaintext, "secret")
        assertFalse(String(first, Charsets.ISO_8859_1).contains("experiences"))
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `wrong passphrase is rejected`() {
        val encrypted = encryption.encrypt(plaintext, "secret")
        assertThrows(GeneralSecurityException::class.java) { encryption.decrypt(encrypted, "other") }
    }

    @Test
    fun `tampered data is rejected`() {
        val encrypted = encryption.encrypt(plaintext, "secret")
        encrypted[encrypted.size - 1] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(GeneralSecurityException::class.java) { encryption.decrypt(encrypted, "secret") }
    }

    @Test
    fun `truncated data is rejected`() {
        assertThrows(GeneralSecurityException::class.java) { encryption.decrypt(ByteArray(10), "secret") }
    }

    @Test
    fun `the unsupported v2 format is detected and rejected`() {
        val v2 = "PJE2".toByteArray() + ByteArray(100)
        assertTrue(ExportEncryption.isUnsupportedV2Export(v2))
        assertFalse(ExportEncryption.isUnsupportedV2Export(encryption.encrypt(plaintext, "secret").copyOf().also { it[0] = 0 }))
        assertThrows(GeneralSecurityException::class.java) { encryption.decrypt(v2, "secret") }
    }

    @Test
    fun `json detection tells plaintext from encrypted files`() {
        assertTrue(ExportEncryption.looksLikeJson(plaintext))
        assertTrue(ExportEncryption.looksLikeJson("  \n[1]".toByteArray()))
        assertFalse(ExportEncryption.looksLikeJson(byteArrayOf(0x50, 0x4A)))
        assertFalse(ExportEncryption.looksLikeJson(ByteArray(0)))
    }
}
