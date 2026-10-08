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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import foo.pilz.freaklog.R
import foo.pilz.freaklog.data.export.ExportEncryption
import foo.pilz.freaklog.data.export.buildJournalExportJson
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Writes an encrypted full export into the user-chosen folder once a day and
 * keeps the newest [KEEP_COUNT] files. Dependencies come from a Hilt entry
 * point so the default WorkManager factory can construct it.
 */
class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun experienceRepository(): ExperienceRepository
        fun webhookRepository(): WebhookRepository
        fun exportEncryption(): ExportEncryption
        fun userPreferences(): UserPreferences
        fun backupPasswordStore(): BackupPasswordStore
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val userPreferences = deps.userPreferences()
        val treeUri = userPreferences.backupDirUriFlow.firstOrNull()?.let(Uri::parse)
            ?: return Result.failure()
        val sealed = userPreferences.backupPasswordSealedFlow.firstOrNull()
            ?: return Result.failure()
        val password = deps.backupPasswordStore().unseal(sealed)
            ?: return notifyFailureAndFail("Couldn't read the backup password. Set up backups again.")

        return try {
            val plaintext = buildJournalExportJson(deps.experienceRepository(), deps.webhookRepository()).toByteArray()
            val encrypted = deps.exportEncryption().encrypt(plaintext, password)
            writeBackup(treeUri, encrypted)
            pruneOldBackups(treeUri)
            userPreferences.saveLastBackupTime(Instant.now())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                notifyFailureAndFail("Backup failed: ${e.message}. Does the backup folder still exist?")
            }
        }
    }

    private fun writeBackup(treeUri: Uri, encrypted: ByteArray) {
        val resolver = applicationContext.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val fileUri = DocumentsContract.createDocument(
            resolver, parent, "application/octet-stream", backupFileName(Instant.now(), ZoneId.systemDefault())
        ) ?: throw IOException("Could not create backup file")
        resolver.openOutputStream(fileUri)?.use { it.write(encrypted) }
            ?: throw IOException("Could not write backup file")
    }

    private fun pruneOldBackups(treeUri: Uri) {
        val resolver = applicationContext.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val idsByName = mutableMapOf<String, String>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                idsByName[cursor.getString(1)] = cursor.getString(0)
            }
        }
        backupsToPrune(idsByName.keys).forEach { name ->
            DocumentsContract.deleteDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, idsByName.getValue(name))
            )
        }
    }

    private fun notifyFailureAndFail(message: String): Result {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backups", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("Automatic backup failed")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return Result.failure()
    }

    companion object {
        private const val WORK_NAME = "encrypted_backup"
        private const val CHANNEL_ID = "backup"
        private const val NOTIFICATION_ID = 9002
        private const val MAX_RETRIES = 2
        const val KEEP_COUNT = 7
        private val BACKUP_FILE_PATTERN = Regex("journal-backup-.*\\.enc")
        private val FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

        /** Names sort chronologically, which is what pruning relies on. */
        fun backupFileName(time: Instant, zone: ZoneId): String =
            "journal-backup-${FILE_TIME_FORMAT.format(time.atZone(zone))}.enc"

        /** The backup files to delete: everything but the newest [KEEP_COUNT]; other files are never touched. */
        fun backupsToPrune(fileNames: Collection<String>): List<String> =
            fileNames.filter { it.matches(BACKUP_FILE_PATTERN) }.sortedDescending().drop(KEEP_COUNT)

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build()
            )
        }

        fun backupNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<BackupWorker>().build())
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
