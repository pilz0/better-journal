package foo.pilz.freaklog.data.substanceshare

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream

/** Each share is immutable. Later shares revoke and remove snapshots older than one day. */
internal fun sharedSnapshot(context: Context, directory: File, prefix: String, suffix: String): File {
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    directory.listFiles().orEmpty().filter { it.isFile && it.lastModified() < cutoff }.forEach { old ->
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", old)
        context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        old.delete()
    }
    return File.createTempFile(prefix.padEnd(3, '_'), suffix, directory)
}

internal fun readSharedPayload(input: InputStream): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        require(out.size() + read <= 2 * 1024 * 1024) { "Shared file is too large" }
        out.write(buffer, 0, read)
    }
    return out.toString("UTF-8")
}
