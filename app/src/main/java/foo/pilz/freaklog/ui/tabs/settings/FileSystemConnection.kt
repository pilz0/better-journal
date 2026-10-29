/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class FileSystemConnection @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getTextFromUri(uri: Uri): String? {
        try {
            return context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            return null
        }
    }

    /** @throws IllegalArgumentException if the file exceeds [MAX_FILE_SIZE]. */
    fun getBytesFromUri(uri: Uri): ByteArray? =
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead = stream.read(buffer)
                while (bytesRead != -1) {
                    require(output.size() + bytesRead <= MAX_FILE_SIZE) {
                        "File exceeds maximum allowed size (50MB)"
                    }
                    output.write(buffer, 0, bytesRead)
                    bytesRead = stream.read(buffer)
                }
                output.toByteArray()
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /** @throws java.io.IOException when the destination cannot be written. */
    fun saveBytesInUri(uri: Uri, bytes: ByteArray) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw java.io.IOException("Failed To Save")
        output.use { it.write(bytes) }
    }

    /** @throws java.io.IOException when the destination cannot be opened or written. */
    fun saveTextInUri(uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw java.io.IOException("Could not open output stream for URI: $uri")
        output.use { stream ->
            stream.bufferedWriter().use { it.write(text) }
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024
    }
}
