package foo.pilz.freaklog.data.backup

import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupUriSecurityTest {
    @Test
    fun documentMustBelongToTheSelectedProviderAndTree() {
        val tree = Uri.parse("content://documents.example/tree/primary%3ABackups")
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Backups/backup.enc")
        BackupWorker.requireBackupDocumentUri(tree, document)
        listOf(
            "file:///data/user/0/foo.pilz.freaklog/databases/journal",
            "content://other.provider/tree/primary%3ABackups/document/file",
            "content://documents.example/tree/other/document/file",
            "content://documents.example/document/file",
            "content://documents.example/tree/primary%3ABackups/document/file/extra",
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                BackupWorker.requireBackupDocumentUri(tree, Uri.parse(invalid))
            }
        }
    }
}
