package foo.pilz.freaklog.ui.tabs.settings.health

import foo.pilz.freaklog.data.health.HealthSharingPreferences
import foo.pilz.freaklog.data.health.MedicationExport
import foo.pilz.freaklog.data.health.MedicationHealthConnect
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.testing.EntityBuilders
import foo.pilz.freaklog.ui.tabs.settings.FileSystemConnection
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HealthSharingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val locked = MutableStateFlow(false)
    private val rows = MutableStateFlow(
        listOf(
            IngestionWithCompanionAndCustomUnit(
                EntityBuilders.ingestion(id = 1, substanceName = "Medication", time = Instant.now()), null, null,
            )
        )
    )
    private val dao = mockk<ExperienceDao> {
        every { getSortedIngestionsWithSubstanceCompanionsFlow() } returns rows
    }
    private val preferences = mockk<HealthSharingPreferences> {
        every { medications } returns MutableStateFlow(setOf("Medication"))
        every { aiVitals } returns MutableStateFlow(false)
    }
    private val medical = mockk<MedicationHealthConnect>()
    private val auth = mockk<BiometricAuthManager> { every { shouldLock } returns locked }
    private val viewModel get() = HealthSharingViewModel(dao, preferences, medical, mockk<FileSystemConnection>(), auth)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun journalChangesInvalidatePreviewBeforeItCanBeExported() = runTest(dispatcher) {
        val model = viewModel
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        model.preparePreview()
        runCurrent()
        assertNotNull(model.preview.value)
        rows.value = emptyList()
        runCurrent()
        assertNull(model.preview.value)
        model.sync()
        runCurrent()
        coVerify(exactly = 0) { medical.sync(any()) }
    }

    @Test fun lockingPreventsCopyAndSyncOfAnExistingPreview() = runTest(dispatcher) {
        val model = viewModel
        model.preview.value = MedicationExport(Instant.now(), emptyList())
        locked.value = true
        assertNull(model.summary())
        model.sync()
        runCurrent()
        assertFalse(model.busy.value)
        coVerify(exactly = 0) { medical.sync(any()) }
    }

    @Test fun cancellationClearsBusyWithoutLeakingPlatformErrors() = runTest(dispatcher) {
        val model = viewModel
        model.preview.value = MedicationExport(Instant.now(), emptyList())
        coEvery { medical.sync(any()) } throws CancellationException("Private resource content")
        model.sync()
        advanceUntilIdle()
        assertFalse(model.busy.value)
        assertNull(model.message.value)
    }

    @Test fun failuresNeverShowPrivatePlatformExceptionContents() = runTest(dispatcher) {
        val model = viewModel
        model.preview.value = MedicationExport(Instant.now(), emptyList())
        coEvery { medical.sync(any()) } throws IllegalArgumentException("Private medication contents")
        model.sync()
        advanceUntilIdle()
        assertFalse(model.busy.value)
        assertNotNull(model.message.value)
        assertFalse(model.message.value!!.contains("Private medication"))
    }
}
