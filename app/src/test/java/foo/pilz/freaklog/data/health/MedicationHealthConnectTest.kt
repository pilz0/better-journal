package foo.pilz.freaklog.data.health

import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class MedicationHealthConnectTest {
    private val locked = MutableStateFlow(false)
    private val auth = mockk<BiometricAuthManager> { every { shouldLock } returns locked }
    private val store = mockk<MedicationRecordStore> {
        every { isAvailable() } returns true
        coEvery { hasPermission() } returns true
        coEvery { ownSources() } returns listOf("ours")
        coEvery { upsert(any(), any()) } just Runs
        coEvery { delete(any(), any()) } just Runs
        coEvery { removeSource(any()) } just Runs
    }
    private val medical = MedicationHealthConnect(store, auth)
    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private fun report(dose: Double = 10.0) = MedicationExport.create(
        listOf(
            IngestionWithCompanionAndCustomUnit(
                EntityBuilders.ingestion(id = 1, substanceName = "Medication", time = now, dose = dose),
                null, null,
            )
        ),
        setOf("Medication"), now,
    )

    @Test fun pagesThroughExistingRecordsRemovesDeletedAndDoesNotDuplicateUnchangedDoses() = runTest {
        val unchanged = StoredMedication("freaklog-dose-1", report().statements.single().toString())
        val deleted = StoredMedication("freaklog-dose-2", "{}")
        coEvery { store.readPage("ours", null) } returns MedicationPage(listOf(unchanged), "next")
        coEvery { store.readPage("ours", "next") } returns MedicationPage(listOf(deleted), null)
        assertEquals(1, medical.sync(report()))
        coVerify(exactly = 2) { store.readPage("ours", any()) }
        coVerify { store.delete("ours", listOf(deleted.id)) }
        coVerify { store.upsert("ours", listOf(report().patient.toString())) }
        coVerify(exactly = 0) { store.createSource() }
    }

    @Test fun editedDoseIsUpsertedWithSameStableId() = runTest {
        coEvery { store.readPage("ours", null) } returns MedicationPage(
            listOf(StoredMedication("freaklog-dose-1", report().statements.single().toString())), null,
        )
        medical.sync(report(20.0))
        coVerify { store.upsert("ours", listOf(report().patient.toString(), report(20.0).statements.single().toString())) }
        coVerify(exactly = 0) { store.delete(any(), any()) }
    }

    @Test fun emptySelectionDeletesDedicatedOwnSourceWithoutWriting() = runTest {
        medical.sync(MedicationExport(now, emptyList()))
        coVerify { store.removeSource("ours") }
        coVerify(exactly = 0) { store.readPage(any(), any()) }
        coVerify(exactly = 0) { store.upsert(any(), any()) }
    }

    @Test fun deniedPermissionAndAppLockNeverWrite() = runTest {
        coEvery { store.hasPermission() } returns false
        try {
            medical.sync(report())
            fail("Expected permission failure")
        } catch (_: IllegalStateException) {
            // No platform mutation is allowed.
        }
        locked.value = true
        try {
            medical.removeExport()
            fail("Expected lock failure")
        } catch (_: IllegalStateException) {
            // No platform mutation is allowed.
        }
        coVerify(exactly = 0) { store.upsert(any(), any()) }
        coVerify(exactly = 0) { store.removeSource(any()) }
    }

    @Test fun unsupportedFeaturePreventsPermissionProtectedOperations() = runTest {
        every { store.isAvailable() } returns false
        assertFalse(medical.isAvailable())
        try {
            medical.sync(report())
            fail("Expected unavailable failure")
        } catch (_: IllegalStateException) {
            // No platform operation is allowed.
        }
        coVerify(exactly = 0) { store.ownSources() }
    }

    @Test fun firstExportCreatesSourceAndChunksWrites() = runTest {
        coEvery { store.ownSources() } returns emptyList()
        coEvery { store.createSource() } returns "new"
        coEvery { store.readPage("new", null) } returns MedicationPage(emptyList(), null)
        val rows = (1..250).map { id ->
            IngestionWithCompanionAndCustomUnit(
                EntityBuilders.ingestion(id = id, substanceName = "Medication", time = now), null, null,
            )
        }
        assertEquals(250, medical.sync(MedicationExport.create(rows, setOf("Medication"), now)))
        coVerify(exactly = 1) { store.createSource() }
        coVerify(exactly = 3) { store.upsert("new", match { it.size <= 100 }) }
    }

    @Test fun permissionRevocationBetweenReadAndWriteStopsSync() = runTest {
        coEvery { store.hasPermission() } returnsMany listOf(true, true, true, false)
        coEvery { store.readPage("ours", null) } returns MedicationPage(emptyList(), null)
        try {
            medical.sync(report())
            fail("Expected permission failure")
        } catch (_: IllegalStateException) {
            // Permission was lost before the first write.
        }
        coVerify(exactly = 0) { store.upsert(any(), any()) }
    }

    @Test fun removeDeletesOnlySourcesReturnedByTheStore() = runTest {
        medical.removeExport()
        coVerify(exactly = 1) { store.removeSource("ours") }
    }
}
