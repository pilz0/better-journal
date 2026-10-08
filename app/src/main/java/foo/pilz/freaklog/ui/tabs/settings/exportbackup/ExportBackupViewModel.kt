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

package foo.pilz.freaklog.ui.tabs.settings.exportbackup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.backup.BackupPasswordStore
import foo.pilz.freaklog.data.backup.BackupWorker
import foo.pilz.freaklog.data.export.ExportEncryption
import foo.pilz.freaklog.data.export.ExportFilter
import foo.pilz.freaklog.data.export.JournalExport
import foo.pilz.freaklog.data.export.buildJournalExportJson
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.ui.tabs.settings.FileSystemConnection
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class ExportBackupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val experienceRepository: ExperienceRepository,
    private val webhookRepository: WebhookRepository,
    private val fileSystemConnection: FileSystemConnection,
    private val userPreferences: UserPreferences,
    private val exportEncryption: ExportEncryption,
    private val backupPasswordStore: BackupPasswordStore,
    private val customSubstanceRepository: foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository,
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()

    private val lenientJson = Json { ignoreUnknownKeys = true }

    val exportFilterFlow = MutableStateFlow(ExportFilter())
    val usedSubstanceNamesFlow = MutableStateFlow<List<String>>(emptyList())

    /** Set after an encrypted export so the UI can show the one-time passphrase. */
    private val _exportPassphrase = MutableStateFlow<String?>(null)
    val exportPassphrase: StateFlow<String?> = _exportPassphrase.asStateFlow()

    /** An encrypted file waiting for the user to type its passphrase. */
    private val _pendingEncryptedImport = MutableStateFlow<Uri?>(null)
    val pendingEncryptedImport: StateFlow<Uri?> = _pendingEncryptedImport.asStateFlow()

    val backupDirUriFlow = userPreferences.backupDirUriFlow.stateInVm(viewModelScope, null)
    val lastBackupTimeFlow = userPreferences.lastBackupTimeFlow.stateInVm(viewModelScope, null)

    init {
        viewModelScope.launch {
            usedSubstanceNamesFlow.value = experienceRepository.getAllSubstanceCompanions()
                .map { it.substanceName }
                .sorted()
        }
    }

    fun setExportFilter(filter: ExportFilter) {
        exportFilterFlow.value = filter
    }

    fun clearExportPassphrase() {
        _exportPassphrase.value = null
    }

    fun clearPendingImport() {
        _pendingEncryptedImport.value = null
    }

    fun exportPlaintextFile(uri: Uri) = export { json ->
        fileSystemConnection.saveBytesInUri(uri, json.toByteArray())
    }

    fun exportEncryptedFile(uri: Uri) = export { json ->
        val encrypted = exportEncryption.encrypt(json.toByteArray())
        fileSystemConnection.saveBytesInUri(uri, encrypted.data)
        _exportPassphrase.value = encrypted.passphrase
    }

    @Suppress("TooGenericExceptionCaught")
    private fun export(write: (json: String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.Default) {
                    write(buildJournalExportJson(experienceRepository, webhookRepository, exportFilterFlow.value))
                }
                "Export successful"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "Export failed"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    /** Imports a plaintext export right away; an encrypted one waits for [importWithPassphrase]. */
    fun handleImportFile(uri: Uri) {
        viewModelScope.launch {
            val bytes = fileSystemConnection.getBytesFromUri(uri)
            when {
                bytes == null -> snackbarHostState.showSnackbar("File not found")
                ExportEncryption.looksLikeJson(bytes) -> importPlaintext(bytes.decodeToString())
                else -> _pendingEncryptedImport.value = uri
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun importWithPassphrase(passphrase: String) {
        viewModelScope.launch {
            val uri = _pendingEncryptedImport.value ?: return@launch
            _pendingEncryptedImport.value = null
            val bytes = fileSystemConnection.getBytesFromUri(uri)
            if (bytes == null) {
                snackbarHostState.showSnackbar("File not found")
                return@launch
            }
            val text = try {
                withContext(Dispatchers.Default) { exportEncryption.decrypt(bytes, passphrase).decodeToString() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(
                    if (ExportEncryption.isUnsupportedV2Export(bytes)) {
                        "This file uses a newer encryption format that this app cannot read"
                    } else {
                        "Import failed - check your passphrase"
                    }
                )
                return@launch
            }
            importPlaintext(text)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun importPlaintext(text: String) {
        val message = try {
            val journalExport = withContext(Dispatchers.Default) {
                lenientJson.decodeFromString<JournalExport>(text)
            }
            experienceRepository.deleteEverything()
            experienceRepository.insertEverything(journalExport)
            "Import successful"
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "Decoding file failed"
        }
        snackbarHostState.showSnackbar(message)
    }

    /** A shared substance whose name is already taken, waiting for the user to pick replace or keep both. */
    private val _substanceCollision = MutableStateFlow<foo.pilz.freaklog.data.substanceshare.SharedSubstance?>(null)
    val substanceCollision: StateFlow<foo.pilz.freaklog.data.substanceshare.SharedSubstance?> = _substanceCollision.asStateFlow()

    fun importSubstanceFile(uri: Uri) {
        viewModelScope.launch {
            when (val result = foo.pilz.freaklog.data.substanceshare.parseAndImport(appContext, uri, customSubstanceRepository)) {
                is foo.pilz.freaklog.data.substanceshare.ImportResult.Imported -> snackbarHostState.showSnackbar("Imported ${result.name}")
                is foo.pilz.freaklog.data.substanceshare.ImportResult.CollisionPending -> _substanceCollision.value = result.payload
                is foo.pilz.freaklog.data.substanceshare.ImportResult.Failed -> snackbarHostState.showSnackbar(
                    when (result.reason) {
                        foo.pilz.freaklog.data.substanceshare.ImportFailure.OpenStream -> "File not found"
                        foo.pilz.freaklog.data.substanceshare.ImportFailure.Parse -> "This is not a substance file"
                        foo.pilz.freaklog.data.substanceshare.ImportFailure.UnsupportedFormat -> "This substance file needs a newer app version"
                    }
                )
            }
        }
    }

    fun resolveSubstanceCollision(replace: Boolean) {
        val payload = _substanceCollision.value ?: return
        _substanceCollision.value = null
        viewModelScope.launch {
            val name = if (replace) {
                foo.pilz.freaklog.data.substanceshare.resolveCollisionReplace(payload, customSubstanceRepository)
                payload.name
            } else {
                val existingNames = experienceRepository.getAllCustomSubstances().map { it.name }.toSet()
                foo.pilz.freaklog.data.substanceshare.resolveCollisionKeepBoth(
                    payload,
                    existingNames,
                    customSubstanceRepository,
                )
            }
            snackbarHostState.showSnackbar("Imported $name")
        }
    }

    fun dismissSubstanceCollision() {
        _substanceCollision.value = null
    }

    fun enableAutomaticBackups(dirUri: Uri, password: String) {
        viewModelScope.launch {
            // Without a persisted grant the worker loses access to the folder after a reboot.
            appContext.contentResolver.takePersistableUriPermission(
                dirUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            userPreferences.saveBackupConfig(dirUri.toString(), backupPasswordStore.seal(password))
            BackupWorker.schedule(appContext)
            BackupWorker.backupNow(appContext)
        }
    }

    fun disableAutomaticBackups() {
        viewModelScope.launch {
            BackupWorker.cancel(appContext)
            userPreferences.clearBackupConfig()
        }
    }

    fun backupNow() {
        BackupWorker.backupNow(appContext)
        viewModelScope.launch { snackbarHostState.showSnackbar("Backup started") }
    }
}
