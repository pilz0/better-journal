@file:OptIn(androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi::class)

package foo.pilz.freaklog.data.health

import androidx.health.connect.client.permission.HealthPermission
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Manual, idempotent reconciliation of our own dedicated source, including edits and deletions. */
@Singleton
class MedicationHealthConnect @Inject constructor(
    private val store: MedicationRecordStore,
    private val auth: BiometricAuthManager,
) {
    private val mutex = Mutex()

    fun isAvailable() = store.isAvailable()

    suspend fun sync(report: MedicationExport): Int = mutex.withLock {
        checkAccess()
        val existingSource = store.ownSources().singleOrNull()
        if (report.statements.isEmpty()) {
            existingSource?.let {
                checkAccess()
                store.removeSource(it)
            }
            return@withLock 0
        }
        checkAccess()
        val source = existingSource ?: store.createSource()
        val existing = mutableListOf<StoredMedication>()
        var pageToken: String? = null
        do {
            checkAccess()
            val response = store.readPage(source, pageToken)
            existing.addAll(response.records)
            pageToken = response.nextPageToken
        } while (pageToken != null)

        val desired = report.statements.associateBy { it["id"]!!.jsonPrimitive.content }
        val obsolete = existing.filter { it.id !in desired }.map { it.id }
        obsolete.chunked(100).forEach {
            checkAccess()
            store.delete(source, it)
        }
        val existingJson = existing.associate { it.id to it.json }
        val changed = report.statements.filter {
            existingJson[it["id"]!!.jsonPrimitive.content] != it.toString()
        }
        // The minimal patient contains no name, identifier, birth date, or other personal details.
        (listOf(report.patient) + changed).chunked(100).forEach { resources ->
            checkAccess()
            store.upsert(source, resources.map { it.toString() })
        }
        report.statements.size
    }

    suspend fun removeExport() = mutex.withLock {
        checkAccess()
        store.ownSources().forEach {
            checkAccess()
            store.removeSource(it)
        }
    }

    private suspend fun checkAccess() {
        check(!auth.shouldLock.value) { "Unlock the journal first." }
        check(isAvailable()) { "Medical records are unavailable on this device. Use the summary or file export." }
        check(store.hasPermission()) { "Allow medical record writing in Health Connect first." }
    }

    companion object {
        const val SOURCE_NAME = "Freaklog — self-reported medications"
        val permissions = setOf(HealthPermission.PERMISSION_WRITE_MEDICAL_DATA)
    }
}
