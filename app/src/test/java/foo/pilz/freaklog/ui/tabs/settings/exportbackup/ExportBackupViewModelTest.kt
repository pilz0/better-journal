package foo.pilz.freaklog.ui.tabs.settings.exportbackup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import foo.pilz.freaklog.data.backup.BackupPasswordStore
import foo.pilz.freaklog.data.export.ExportEncryption
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.scheduled.NotificationScheduler
import foo.pilz.freaklog.ui.tabs.settings.FileSystemConnection
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportBackupViewModelTest {

  private val dispatcher = UnconfinedTestDispatcher()
  private val context = mockk<Context>(relaxed = true)
  private val experienceRepository = mockk<ExperienceRepository>(relaxed = true)
  private val webhookRepository = mockk<WebhookRepository>(relaxed = true)
  private val fileSystemConnection = mockk<FileSystemConnection>(relaxed = true)
  private val userPreferences = mockk<UserPreferences>(relaxed = true)
  private val exportEncryption = ExportEncryption()
  private val backupPasswordStore = mockk<BackupPasswordStore>(relaxed = true)
  private val auth = mockk<BiometricAuthManager>(relaxed = true)
  private val customSubstanceRepository = mockk<CustomSubstanceRepository>(relaxed = true)
  private val notificationScheduler = mockk<NotificationScheduler>(relaxed = true)
  private val shouldLock = MutableStateFlow(false)
  private val viewModels = mutableListOf<ExportBackupViewModel>()

  private fun createViewModel() =
      ExportBackupViewModel(
              appContext = context,
              experienceRepository = experienceRepository,
              webhookRepository = webhookRepository,
              fileSystemConnection = fileSystemConnection,
              userPreferences = userPreferences,
              exportEncryption = exportEncryption,
              backupPasswordStore = backupPasswordStore,
              auth = auth,
              customSubstanceRepository = customSubstanceRepository,
              notificationScheduler = notificationScheduler,
          )
          .also { viewModels += it }

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    every { auth.shouldLock } returns shouldLock
    every { experienceRepository.getAllSubstanceCompanionsFlow() } returns flowOf(emptyList())
    every { userPreferences.backupDirUriFlow } returns flowOf(null)
    every { userPreferences.lastBackupTimeFlow } returns flowOf(null)
  }

  @After
  fun tearDown() {
    viewModels.forEach { it.viewModelScope.cancel() }
    Dispatchers.resetMain()
  }

  @Test
  fun `handleImportFile imports plaintext JSON directly and resyncs reminders`() {
    val uri = mockk<Uri>()
    val existingReminder =
        Reminder(
            id = 7,
            title = "Caffeine",
            hour = 8,
            minute = 0,
            isEnabled = true,
        )
    coEvery { experienceRepository.getAllReminders() } returns listOf(existingReminder)
    every { fileSystemConnection.getBytesFromUri(uri) } returns "{}".toByteArray()

    val vm = createViewModel()
    vm.handleImportFile(uri)

    coVerify(timeout = 5_000) { notificationScheduler.rescheduleAll() }
    verify { notificationScheduler.cancelReminder(7) }
    coVerify { experienceRepository.replaceEverything(any()) }
    assertThat(vm.pendingEncryptedImport.value).isNull()
  }

  @Test
  fun `exportEncryptedFile and importWithPassphrase round-trip encrypted archive`() {
    val exportUri = mockk<Uri>()
    var savedBytes: ByteArray? = null
    every { fileSystemConnection.saveBytesInUri(exportUri, any()) } answers
        {
          savedBytes = secondArg()
        }

    val vm = createViewModel()
    vm.exportEncryptedFile(exportUri)

    verify(timeout = 5_000) { fileSystemConnection.saveBytesInUri(exportUri, any()) }
    val passphrase = vm.exportPassphrase.value
    assertThat(passphrase).isNotNull()
    assertThat(savedBytes).isNotNull()

    val importUri = mockk<Uri>()
    every { fileSystemConnection.getBytesFromUri(importUri) } answers { savedBytes }
    vm.handleImportFile(importUri)

    val deadline = System.currentTimeMillis() + 5_000
    while (vm.pendingEncryptedImport.value == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(10)
    }
    assertThat(vm.pendingEncryptedImport.value).isEqualTo(importUri)

    vm.importWithPassphrase(passphrase!!)
    coVerify(timeout = 5_000) { experienceRepository.replaceEverything(any()) }
    assertThat(vm.pendingEncryptedImport.value).isNull()
  }
}
