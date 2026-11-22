package foo.pilz.freaklog.ui.tabs.journal.addingestion.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroup
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.webhook.IngestionWebhookSender
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubstanceGroupFinishViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val externalScope = CoroutineScope(dispatcher)
  private val groupRepo = mockk<SubstanceGroupRepository>()
  private val experienceRepo = mockk<ExperienceRepository>()
  private val userPreferences = mockk<UserPreferences>()
  private val webhookRepository = mockk<WebhookRepository>()
  private val sender = mockk<IngestionWebhookSender>()
  private lateinit var viewModel: SubstanceGroupFinishViewModel

  private val group =
      SubstanceGroupWithItems(
          group = SubstanceGroup(id = 3, name = "Weekend"),
          items =
              listOf(
                  SubstanceGroupItem(
                      id = 1,
                      groupId = 3,
                      sortOrder = 0,
                      substanceName = "MDMA",
                      administrationRoute = AdministrationRoute.ORAL,
                      dose = 100.0,
                      units = "mg",
                  ),
                  SubstanceGroupItem(
                      id = 2,
                      groupId = 3,
                      sortOrder = 1,
                      substanceName = "Caffeine",
                      administrationRoute = AdministrationRoute.ORAL,
                      dose = 80.0,
                      units = "mg",
                      isEstimate = true,
                      estimatedDoseStandardDeviation = 10.0,
                  ),
              ),
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    coEvery { groupRepo.getWithItems(3) } returns group
    every { experienceRepo.getSortedIngestions(any()) } returns flowOf(emptyList())
    every { experienceRepo.getSortedIngestionsFlow(any(), any()) } returns flowOf(emptyList())
    every { experienceRepo.getMaxExperienceIdFlow() } returns flowOf(4)
    coEvery {
      experienceRepo.getSortedExperiencesWithIngestionsWithSortDateBetween(any(), any())
    } returns emptyList()
    coEvery { experienceRepo.getSubstanceCompanion(any()) } answers
        {
          SubstanceCompanion(substanceName = firstArg(), color = AdaptiveColor.BLUE)
        }
    coEvery { experienceRepo.insertIngestionExperienceAndCompanion(any(), any(), any()) } returns
        20L
    coEvery { experienceRepo.insertIngestionAndCompanion(any(), any()) } returns 21L
    every { userPreferences.lastIngestionTimeOfExperienceFlow } returns flowOf(null)
    every { userPreferences.clonedIngestionTimeFlow } returns flowOf(null)
    every { userPreferences.activeNotificationExperienceIdFlow } returns flowOf(null)
    every { userPreferences.isTimelineNotificationAutoStartEnabledFlow } returns flowOf(false)
    every { webhookRepository.getEnabledFlow() } returns flowOf(emptyList())
    coEvery { sender.send(any(), any()) } returns Unit

    viewModel =
        SubstanceGroupFinishViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            groupRepo = groupRepo,
            experienceRepo = experienceRepo,
            userPreferences = userPreferences,
            defaultSubstanceColors = mockk(),
            webhookRepository = webhookRepository,
            ingestionWebhookSender = sender,
            externalScope = externalScope,
            state = SavedStateHandle(mapOf("groupId" to 3)),
        )
    dispatcher.scheduler.runCurrent()
  }

  @After
  fun tearDown() {
    if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
    externalScope.cancel()
    Dispatchers.resetMain()
  }

  @Test
  fun `logging a group sends webhooks for every saved ingestion`() {
    val ingestions = slot<List<Ingestion>>()
    val selection = slot<Map<Int, Boolean>>()
    coEvery { sender.send(capture(ingestions), capture(selection)) } returns Unit

    viewModel.setWebhookSelected(webhookId = 9, selected = false)
    viewModel.confirmAndDismiss {}
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(ingestions.captured.map { it.id to it.substanceName })
        .containsExactly(20 to "MDMA", 21 to "Caffeine")
    assertThat(ingestions.captured.map { it.experienceId }).containsExactly(5, 5)
    assertThat(selection.captured).isEqualTo(mapOf(9 to false))
  }

  @Test
  fun `unticked group items are neither saved nor sent`() {
    val ingestions = slot<List<Ingestion>>()
    coEvery { sender.send(capture(ingestions), any()) } returns Unit

    viewModel.toggleItem(group.items[0])
    viewModel.confirmAndDismiss {}
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(ingestions.captured.map { it.substanceName }).containsExactly("Caffeine")
    coVerify(exactly = 0) { experienceRepo.insertIngestionAndCompanion(any(), any()) }
  }

  @Test
  fun `dose multiplier scales every saved ingestion`() {
    val ingestions = slot<List<Ingestion>>()
    coEvery { sender.send(capture(ingestions), any()) } returns Unit

    viewModel.onDoseMultiplierChange("0.5")
    viewModel.confirmAndDismiss {}
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(ingestions.captured.map { it.dose }).containsExactly(50.0, 40.0)
    assertThat(ingestions.captured.map { it.isDoseAnEstimate }).containsExactly(false, true)
    assertThat(ingestions.captured.map { it.estimatedDoseStandardDeviation })
        .containsExactly(null, 5.0)
  }

  @Test
  fun `invalid dose multiplier prevents saving`() {
    viewModel.onDoseMultiplierChange("0")
    viewModel.confirmAndDismiss {}
    dispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 0) {
      experienceRepo.insertIngestionExperienceAndCompanion(any(), any(), any())
    }
  }

  @Test
  fun `a group that crosses an intake limit asks before saving`() {
    coEvery { experienceRepo.getIntakeLimitsForSubstance("MDMA") } returns
        listOf(
            foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit(
                id = 1,
                substanceName = "MDMA",
                limitType = foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType.DOSE,
                maxDose = 150.0,
                unit = "mg",
                maxCount = null,
                windowSeconds = 86_400,
                warningPercent = 80,
            )
        )
    coEvery { experienceRepo.getIntakeLimitsForSubstance("Caffeine") } returns emptyList()
    coEvery {
      experienceRepo.getIngestionsWithCustomUnitsForSubstanceSince(any(), any())
    } returns emptyList()

    // 100 mg x 2 = 200 mg, over the 150 mg limit.
    viewModel.onDoseMultiplierChange("2")
    viewModel.onDoneClicked {}
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.isShowingLimitWarning).isEqualTo(true)
    coVerify(exactly = 0) {
      experienceRepo.insertIngestionExperienceAndCompanion(any(), any(), any())
    }

    viewModel.confirmLimitWarningAndSave()
    dispatcher.scheduler.advanceUntilIdle()
    coVerify(exactly = 1) {
      experienceRepo.insertIngestionExperienceAndCompanion(any(), any(), any())
    }
  }

  @Test
  fun `moving end time before start time updates start time and title and refreshes experiences`() {
    val initialStart = java.time.LocalDateTime.of(2026, 5, 20, 14, 0)
    val initialEnd = java.time.LocalDateTime.of(2026, 5, 20, 15, 0)
    viewModel.onChangeStartDateOrTime(initialStart)
    viewModel.onChangeEndDateOrTime(initialEnd)
    dispatcher.scheduler.advanceUntilIdle()

    // Pick end time at 12:00, which is before the current start time (14:00).
    val earlierEnd = java.time.LocalDateTime.of(2026, 5, 20, 12, 0)
    viewModel.onChangeEndDateOrTime(earlierEnd)
    dispatcher.scheduler.advanceUntilIdle()

    // Start time must have moved to 30 mins before the new end (11:30).
    assertThat(viewModel.localDateTimeEndFlow.value).isEqualTo(earlierEnd)
    assertThat(viewModel.localDateTimeStartFlow.value)
        .isEqualTo(java.time.LocalDateTime.of(2026, 5, 20, 11, 30))
    // Title is updated based on new start time.
    assertThat(viewModel.enteredTitle).isEqualTo("20 May 2026")
  }

  @Test
  fun `moving end time before start time does not overwrite a manually changed title`() {
    viewModel.changeTitle("Custom Weekend Group")
    val initialStart = java.time.LocalDateTime.of(2026, 5, 20, 14, 0)
    viewModel.onChangeStartDateOrTime(initialStart)
    dispatcher.scheduler.advanceUntilIdle()

    val earlierEnd = java.time.LocalDateTime.of(2026, 5, 20, 10, 0)
    viewModel.onChangeEndDateOrTime(earlierEnd)
    dispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.enteredTitle).isEqualTo("Custom Weekend Group")
  }
}
