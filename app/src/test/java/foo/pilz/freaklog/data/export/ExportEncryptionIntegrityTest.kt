package foo.pilz.freaklog.data.export

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.GeneralSecurityException

class ExportEncryptionIntegrityTest {
    private val encryption = ExportEncryption()
    private val passphrase = "integration-test-passphrase"

    @Test
    fun emptyUnicodeAndBinaryPayloadsRoundTripWithoutChangingTheirBytes() {
        val payloads = listOf(
            byteArrayOf(),
            "{\"notes\":\"雪 🌈 café\\n\\u0000\"}".toByteArray(),
            ByteArray(4096) { it.toByte() },
        )
        for (payload in payloads) {
            val encrypted = encryption.encrypt(payload, passphrase)
            assertEquals(payload.size + 32 + 12 + 16, encrypted.size)
            assertArrayEquals(payload, encryption.decrypt(encrypted, passphrase))
        }
    }

    @Test
    fun changingSaltIvCiphertextOrTagIsRejected() {
        val encrypted = encryption.encrypt(ByteArray(64) { it.toByte() }, passphrase)
        // Salt, IV, first and last ciphertext bytes, and authentication tag.
        for (offset in listOf(0, 31, 32, 43, 44, encrypted.size - 17, encrypted.lastIndex)) {
            val tampered = encrypted.copyOf()
            tampered[offset] = (tampered[offset].toInt() xor 1).toByte()
            assertThrows("offset=$offset", GeneralSecurityException::class.java) {
                encryption.decrypt(tampered, passphrase)
            }
        }
    }

    @Test
    fun everyTooShortHeaderIsRejected() {
        for (size in 0 until 60) {
            assertThrows("size=$size", GeneralSecurityException::class.java) {
                encryption.decrypt(ByteArray(size), passphrase)
            }
        }
    }

    @Test
    fun removingOrAppendingCiphertextIsRejected() {
        val encrypted = encryption.encrypt("journal".toByteArray(), passphrase)
        for (invalid in listOf(encrypted.copyOf(encrypted.size - 1), encrypted + byteArrayOf(0))) {
            assertThrows(GeneralSecurityException::class.java) {
                encryption.decrypt(invalid, passphrase)
            }
        }
    }

    @Test
    fun embeddedPassphraseWhitespaceIsSignificant() {
        val encrypted = encryption.encrypt("journal".toByteArray(), "two words")
        assertThrows(GeneralSecurityException::class.java) {
            encryption.decrypt(encrypted, "twowords")
        }
    }
}
