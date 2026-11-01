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

package foo.pilz.freaklog.data.backup

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.os.BundleCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import foo.pilz.freaklog.data.export.ExportEncryption
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.room.AppDatabase
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Runs [BackupWorker.doWork] against a directory-backed documents provider, a
 * real in-memory journal and the real [ExportEncryption]; only preferences and
 * the Keystore-backed password store are mocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupWorkerRunTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-31T21:15:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val treeUri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
    private val encryption = ExportEncryption()

    private lateinit var db: AppDatabase
    private lateinit var provider: DirectoryDocumentsProvider
    private lateinit var userPreferences: UserPreferences
    private lateinit var passwordStore: BackupPasswordStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        provider = Robolectric.setupContentProvider(DirectoryDocumentsProvider::class.java, AUTHORITY)
        provider.dir = folder.newFolder("backups")
        userPreferences = mockk {
            every { backupDirUriFlow } returns flowOf(treeUri.toString())
            every { backupPasswordSealedFlow } returns flowOf(SEALED)
            coEvery { saveLastBackupTime(any()) } returns Unit
        }
        passwordStore = mockk { every { unseal(SEALED) } returns PASSWORD }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun worker(
        attempt: Int = 0,
        exportEncryption: ExportEncryption = encryption,
    ): BackupWorker {
        val params = mockk<WorkerParameters>(relaxed = true) {
            every { runAttemptCount } returns attempt
        }
        val dependencies = object : BackupWorker.Dependencies {
            override fun experienceRepository() = ExperienceRepository(
                db = db,
                experienceDao = db.experienceDao(),
                reminderDao = db.reminderDao(),
                webhookDao = db.webhookDao(),
                ingestionWebhookMessageDao = db.ingestionWebhookMessageDao(),
                context = context,
            )
            override fun webhookRepository() = WebhookRepository(db.webhookDao())
            override fun exportEncryption() = exportEncryption
            override fun userPreferences() = userPreferences
            override fun backupPasswordStore() = passwordStore
        }
        return BackupWorker(context, params, { dependencies }, clock)
    }

    private fun backupFiles(): List<String> = provider.dir.list().orEmpty().sorted()

    private fun postedNotifications() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun Notification.text(key: String) = extras.getCharSequence(key)?.toString()

    @Test
    fun `writes an encrypted export of the journal and records the backup time`() = runTest {
        db.experienceDao().insert(EntityBuilders.experience(id = 1, title = "Backed up trip"))

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val name = BackupWorker.backupFileName(now, ZoneOffset.UTC)
        assertEquals(listOf(name), backupFiles())
        val plaintext = encryption.decrypt(File(provider.dir, name).readBytes(), PASSWORD)
        val export = Json.decodeFromString<JournalExport>(plaintext.decodeToString())
        assertEquals(listOf("Backed up trip"), export.experiences.map { it.title })
        coVerify(exactly = 1) { userPreferences.saveLastBackupTime(now) }
        assertTrue(postedNotifications().isEmpty())
    }

    @Test
    fun `keeps the newest backups and never touches other files`() = runTest {
        val old = (1..BackupWorker.KEEP_COUNT).map { day ->
            BackupWorker.backupFileName(Instant.parse("2026-10-%02dT08:00:00Z".format(day)), ZoneOffset.UTC)
        }
        (old + "holiday.jpg").forEach { File(provider.dir, it).writeText("old") }

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        val newest = BackupWorker.backupFileName(now, ZoneOffset.UTC)
        assertEquals((old.drop(1) + newest + "holiday.jpg").sorted(), backupFiles())
    }

    @Test
    fun `a failed prune does not fail the backup`() = runTest {
        val old = (1..BackupWorker.KEEP_COUNT + 2).map { day ->
            BackupWorker.backupFileName(Instant.parse("2026-10-%02dT08:00:00Z".format(day)), ZoneOffset.UTC)
        }
        old.forEach { File(provider.dir, it).writeText("old") }
        provider.failDeletes = true

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(BackupWorker.backupFileName(now, ZoneOffset.UTC) in backupFiles())
        coVerify(exactly = 1) { userPreferences.saveLastBackupTime(now) }
    }

    @Test
    fun `fails quietly while backups are not set up`() = runTest {
        every { userPreferences.backupDirUriFlow } returns flowOf(null)
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())

        every { userPreferences.backupDirUriFlow } returns flowOf(treeUri.toString())
        every { userPreferences.backupPasswordSealedFlow } returns flowOf(null)
        assertEquals(ListenableWorker.Result.failure(), worker().doWork())

        assertTrue(backupFiles().isEmpty())
        assertTrue(postedNotifications().isEmpty())
        coVerify(exactly = 0) { userPreferences.saveLastBackupTime(any()) }
    }

    @Test
    fun `an unreadable password fails with a notification instead of retrying`() = runTest {
        every { passwordStore.unseal(SEALED) } returns null

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertTrue(backupFiles().isEmpty())
        val notification = postedNotifications().single()
        assertEquals("Automatic backup failed", notification.text(NotificationCompat.EXTRA_TITLE))
        assertTrue(notification.text(NotificationCompat.EXTRA_TEXT)!!.contains("backup password"))
    }

    @Test
    fun `a failed write removes the partial file and is retried`() = runTest {
        provider.failWrites = true

        val result = worker(attempt = 0).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(backupFiles().isEmpty())
        assertTrue(postedNotifications().isEmpty())
        coVerify(exactly = 0) { userPreferences.saveLastBackupTime(any()) }
    }

    @Test
    fun `the last attempt gives up and notifies`() = runTest {
        provider.failWrites = true

        val result = worker(attempt = 2).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        val text = postedNotifications().single().text(NotificationCompat.EXTRA_TEXT)!!
        assertTrue(text.startsWith("Backup failed: "))
    }

    @Test
    fun `a backup folder that is not a document tree is rejected`() = runTest {
        every { userPreferences.backupDirUriFlow } returns flowOf(Uri.fromFile(provider.dir).toString())

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertTrue(backupFiles().isEmpty())
    }

    @Test
    fun `a provider returning a document outside the tree is rejected`() = runTest {
        provider.createdDocumentUri = Uri.parse("content://$AUTHORITY/document/elsewhere")

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { userPreferences.saveLastBackupTime(any()) }
    }

    @Test
    fun `cancellation is propagated instead of being retried`() {
        val cancelled = mockk<ExportEncryption> {
            every { encrypt(any(), PASSWORD) } throws CancellationException("stopped")
        }

        assertThrows(CancellationException::class.java) {
            runBlocking { worker(exportEncryption = cancelled).doWork() }
        }
        assertTrue(backupFiles().isEmpty())
    }

    /**
     * A minimal Storage Access Framework provider: one tree [ROOT_ID] whose
     * documents are the files in [dir], with the file name as document id.
     * It implements the `call` methods [DocumentsContract] uses directly, so
     * it doesn't need the `MANAGE_DOCUMENTS` setup of a real `DocumentsProvider`.
     */
    class DirectoryDocumentsProvider : ContentProvider() {
        lateinit var dir: File
        var failWrites = false
        var failDeletes = false
        var createdDocumentUri: Uri? = null

        override fun onCreate() = true

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
            val uri = extras?.let { BundleCompat.getParcelable(it, "uri", Uri::class.java) }
                ?: return super.call(method, arg, extras)
            return when (method) {
                "android:createDocument" -> {
                    val name = extras.getString(DocumentsContract.Document.COLUMN_DISPLAY_NAME)!!
                    File(dir, name).createNewFile()
                    val created = createdDocumentUri
                        ?: DocumentsContract.buildDocumentUriUsingTree(uri, name)
                    Bundle().apply { putParcelable("uri", created) }
                }
                "android:deleteDocument" -> {
                    if (failDeletes) error("delete refused")
                    File(dir, DocumentsContract.getDocumentId(uri)).delete()
                    Bundle()
                }
                else -> super.call(method, arg, extras)
            }
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val columns = projection ?: arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            return MatrixCursor(columns).apply {
                dir.list().orEmpty().forEach { name -> addRow(columns.map { name }) }
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (failWrites) throw FileNotFoundException("disk full")
            val file = File(dir, DocumentsContract.getDocumentId(uri))
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }

    private companion object {
        const val AUTHORITY = "foo.pilz.freaklog.test.documents"
        const val ROOT_ID = "backups"
        const val SEALED = "sealed-password"
        const val PASSWORD = "correct horse battery staple"
    }
}
