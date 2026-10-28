package foo.pilz.freaklog.ui.tabs.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FileSystemConnectionTest {
    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()
    private val connection = FileSystemConnection(context)

    init {
        every { context.contentResolver } returns resolver
    }

    @Test
    fun `files exactly at the size limit are accepted and closed`() {
        val stream = SizedInputStream(MAX_FILE_SIZE)
        every { resolver.openInputStream(uri) } returns stream

        assertEquals(MAX_FILE_SIZE, connection.getBytesFromUri(uri)!!.size)
        assertTrue(stream.closed)
    }

    @Test
    fun `files above the size limit are rejected and closed`() {
        val stream = SizedInputStream(MAX_FILE_SIZE + 1)
        every { resolver.openInputStream(uri) } returns stream

        val error = assertThrows(IllegalArgumentException::class.java) {
            connection.getBytesFromUri(uri)
        }
        assertEquals("File exceeds maximum allowed size (50MB)", error.message)
        assertTrue(stream.closed)
    }

    @Test
    fun `read failures still close the input stream`() {
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("Read failed")
            override fun close() {
                closed = true
            }
        }
        every { resolver.openInputStream(uri) } returns stream

        assertNull(connection.getBytesFromUri(uri))
        assertTrue(closed)
    }

    @Test
    fun `write failures still close the output stream`() {
        var closed = false
        val stream = object : OutputStream() {
            override fun write(value: Int) {
                throw IOException("Write failed")
            }
            override fun close() {
                closed = true
            }
        }
        every { resolver.openOutputStream(uri) } returns stream

        assertThrows(IOException::class.java) { connection.saveTextInUri(uri, "backup") }
        assertTrue(closed)
    }

    private class SizedInputStream(private var remaining: Int) : InputStream() {
        var closed = false
            private set

        override fun read(): Int = if (remaining > 0) {
            remaining--
            0
        } else {
            -1
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) return -1
            val count = minOf(length, remaining)
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }

        override fun close() {
            closed = true
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024
    }
}
