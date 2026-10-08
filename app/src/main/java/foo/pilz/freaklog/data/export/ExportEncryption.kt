/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.export

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase-based encryption for journal exports and backups.
 *
 * Layout: `salt(32) || iv(12) || AES-256-GCM ciphertext+tag`, with the key
 * derived by PBKDF2-HMAC-SHA256 (310k iterations) from the passphrase. This
 * is byte-compatible with the Codeberg fork's "v1" export format, which that
 * app can still import. Its newer Argon2/ChaCha20 format (magic `PJE2`)
 * needs a native library and is not supported here.
 */
@Singleton
class ExportEncryption @Inject constructor() {

    class EncryptedExport(val data: ByteArray, val passphrase: String)

    /** Encrypts with a freshly generated passphrase, which the caller must show to the user. */
    fun encrypt(plaintext: ByteArray): EncryptedExport {
        val passphrase = generatePassphrase()
        return EncryptedExport(data = encrypt(plaintext, passphrase), passphrase = passphrase)
    }

    fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            return salt + iv + cipher.doFinal(plaintext)
        } finally {
            Arrays.fill(key, 0.toByte())
        }
    }

    /** @throws GeneralSecurityException on a wrong passphrase, corrupted data or an unsupported format. */
    fun decrypt(encryptedData: ByteArray, passphrase: String): ByteArray {
        if (isUnsupportedV2Export(encryptedData)) {
            throw GeneralSecurityException("This file uses an encryption format this app cannot read")
        }
        if (encryptedData.size < SALT_LENGTH + IV_LENGTH + TAG_BITS / 8) {
            throw GeneralSecurityException("Invalid encrypted data")
        }
        val salt = encryptedData.copyOfRange(0, SALT_LENGTH)
        val key = deriveKey(passphrase, salt)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, encryptedData, SALT_LENGTH, IV_LENGTH)
            )
            val offset = SALT_LENGTH + IV_LENGTH
            return cipher.doFinal(encryptedData, offset, encryptedData.size - offset)
        } finally {
            Arrays.fill(key, 0.toByte())
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.trim().toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Six groups of five unambiguous characters: about 148 bits of entropy. */
    private fun generatePassphrase(): String =
        (1..PASSPHRASE_GROUPS).joinToString("-") {
            (1..PASSPHRASE_GROUP_LENGTH).map { PASSPHRASE_ALPHABET[random.nextInt(PASSPHRASE_ALPHABET.length)] }
                .joinToString("")
        }

    companion object {
        private const val SALT_LENGTH = 32
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private const val KEY_BITS = 256
        private const val PBKDF2_ITERATIONS = 310_000
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PASSPHRASE_GROUPS = 6
        private const val PASSPHRASE_GROUP_LENGTH = 5
        private const val PASSPHRASE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
        private val V2_MAGIC = byteArrayOf(0x50, 0x4A, 0x45, 0x32)
        private val random = SecureRandom()

        fun isUnsupportedV2Export(data: ByteArray): Boolean =
            data.size >= V2_MAGIC.size && data.copyOfRange(0, V2_MAGIC.size).contentEquals(V2_MAGIC)

        /** Plaintext exports are JSON objects; anything else is treated as encrypted. */
        fun looksLikeJson(bytes: ByteArray): Boolean {
            val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() } ?: return false
            return first == '{'.code.toByte() || first == '['.code.toByte()
        }
    }
}
