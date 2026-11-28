/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bloodtype
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExposurePlus2
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.TimedNote
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.FULL_STOMACH_DISCLAIMER
import foo.pilz.freaklog.ui.YOU
import foo.pilz.freaklog.ui.components.NavigateBackButton
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.Interaction
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardTitle
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CumulativeDoseRow
import foo.pilz.freaklog.ui.tabs.journal.experience.components.IngestionTimeOrDurationText
import foo.pilz.freaklog.ui.tabs.journal.experience.components.InteractionRow
import foo.pilz.freaklog.ui.tabs.journal.experience.components.NoteOrRatingTimeOrDurationText
import foo.pilz.freaklog.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.getDurationText
import foo.pilz.freaklog.ui.tabs.journal.experience.components.ingestion.IngestionRow
import foo.pilz.freaklog.ui.tabs.journal.experience.components.rating.OverallRatingRow
import foo.pilz.freaklog.ui.tabs.journal.experience.components.rating.TimedRatingRow
import foo.pilz.freaklog.ui.tabs.journal.experience.components.timednote.TimedNoteRow
import foo.pilz.freaklog.ui.tabs.journal.experience.models.ConsumerWithIngestions
import foo.pilz.freaklog.ui.tabs.journal.experience.models.CumulativeDose
import foo.pilz.freaklog.ui.tabs.journal.experience.models.IngestionElement
import foo.pilz.freaklog.ui.tabs.journal.experience.models.OneExperienceScreenModel
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelines
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureReading
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.HeartRateSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepHealthConnect
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.SleepSessionSample
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.VitalSeries
import foo.pilz.freaklog.ui.tabs.settings.combinations.BloodPressureDisplay
import foo.pilz.freaklog.ui.tabs.settings.funny.AchievementDef
import foo.pilz.freaklog.ui.tabs.settings.funny.TierBadge
import foo.pilz.freaklog.ui.theme.JournalTheme
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.getDateWithWeekdayText
import foo.pilz.freaklog.ui.utils.rememberHaptic
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun ExperienceScreen(
    viewModel: ExperienceViewModel = hiltViewModel(),
    navigateToAddIngestionSearch: () -> Unit,
    navigateToEditExperienceScreen: () -> Unit,
    navigateToExplainTimeline: () -> Unit,
    navigateToIngestionScreen: (ingestionId: Int) -> Unit,
    navigateToAddRatingScreen: () -> Unit,
    navigateToAddTimedNoteScreen: () -> Unit,
    navigateToEditRatingScreen: (ratingId: Int) -> Unit,
    navigateToEditTimedNoteScreen: (timedNoteId: Int) -> Unit,
    navigateToTimelineScreen: (consumerName: String) -> Unit,
    navigateToRecordBloodPressureScreen: () -> Unit,
    navigateToEditBloodPressureScreen: (recordId: String) -> Unit,
    navigateToAiSettings: () -> Unit,
    navigateBack: () -> Unit,
) {
  LaunchedEffect(Unit) { viewModel.runTeamsSkin() }
  val ingestionsWithCompanions =
      viewModel.ingestionsWithCompanionsFlow.collectAsStateWithLifecycle().value
  val experience = viewModel.experienceFlow.collectAsStateWithLifecycle().value
  val isFavorite = viewModel.isFavoriteFlow.collectAsStateWithLifecycle().value
  val isAiAssistantEnabled = viewModel.aiAssistantEnabledFlow.collectAsStateWithLifecycle().value
  val oneExperienceScreenModel =
      OneExperienceScreenModel(
          id = experience?.id ?: 0,
          isFavorite = isFavorite,
          title = experience?.title ?: "",
          firstIngestionTime =
              ingestionsWithCompanions.minOfOrNull { it.ingestion.time }
                  ?: experience?.sortDate
                  ?: Instant.now(),
          notes = experience?.text ?: "",
          locationName = experience?.location?.name ?: "",
          isCurrentExperience =
              viewModel.isCurrentExperienceFlow.collectAsStateWithLifecycle().value,
          ingestionElements = viewModel.ingestionElementsFlow.collectAsStateWithLifecycle().value,
          cumulativeDoses = viewModel.cumulativeDosesFlow.collectAsStateWithLifecycle().value,
          interactions = viewModel.interactionsFlow.collectAsStateWithLifecycle().value,
          interactionExplanations =
              viewModel.interactionExplanationsFlow.collectAsStateWithLifecycle().value,
          ratings = viewModel.ratingsFlow.collectAsStateWithLifecycle().value,
          timedNotesSorted = viewModel.timedNotesSortedFlow.collectAsStateWithLifecycle().value,
          consumersWithIngestions =
              viewModel.consumersWithIngestionsFlow.collectAsStateWithLifecycle().value,
          dataForEffectLines =
              viewModel.dataForEffectTimelinesFlow.collectAsStateWithLifecycle().value,
      )

  var showAiChat by remember { mutableStateOf(false) }
  val context = androidx.compose.ui.platform.LocalContext.current
  val notificationPermissionLauncher =
      androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
      ) { granted ->
        if (granted) viewModel.toggleNotification(context)
      }
  val isNotificationActive = viewModel.isNotificationActiveFlow.collectAsStateWithLifecycle().value

  val timelineDisplayOption =
      viewModel.timelineDisplayOptionFlow.collectAsStateWithLifecycle().value
  val healthPermissionLauncher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { granted ->
        if (
            granted.containsAll(HeartRateHealthConnect.permissions) ||
                granted.containsAll(SleepHealthConnect.permissions)
        ) {
          viewModel.toggleHeartRate(context)
        } else {
          Toast.makeText(context, "Health Connect permission denied", Toast.LENGTH_SHORT).show()
        }
      }
  val isVitalsEnabled = viewModel.isVitalsEnabledFlow.collectAsStateWithLifecycle().value
  val isHeartRateAvailable = remember { HeartRateHealthConnect.isAvailable(context) }
  val bloodPressurePermissionLauncher =
      rememberLauncherForActivityResult(
          PermissionController.createRequestPermissionResultContract()
      ) { granted ->
        if (granted.containsAll(BloodPressureHealthConnect.permissions)) {
          viewModel.loadBloodPressure(context)
        } else {
          Toast.makeText(context, "Health Connect permission denied", Toast.LENGTH_SHORT).show()
        }
      }
  val bloodPressureDisplay = viewModel.bloodPressureDisplayFlow.collectAsStateWithLifecycle().value
  val isBloodPressureAvailable = remember { BloodPressureHealthConnect.isAvailable(context) }
  val isBloodPressureActive =
      isVitalsEnabled &&
          bloodPressureDisplay != BloodPressureDisplay.OFF &&
          isBloodPressureAvailable
  val isBloodPressureInSubstanceList = bloodPressureDisplay == BloodPressureDisplay.COMBINED
  LaunchedEffect(isBloodPressureActive, timelineDisplayOption) {
    if (isBloodPressureActive && timelineDisplayOption is TimelineDisplayOption.Shown) {
      viewModel.loadBloodPressure(context) {
        bloodPressurePermissionLauncher.launch(BloodPressureHealthConnect.permissions)
      }
    } else if (!isBloodPressureActive) {
      viewModel.clearBloodPressure()
    }
  }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    if (isBloodPressureActive) {
      viewModel.loadBloodPressure(context)
    }
  }

  if (showAiChat && isAiAssistantEnabled) {
    foo.pilz.freaklog.ui.tabs.journal.experience.recommendations.AiChatBottomSheet(
        experienceId = experience?.id,
        onDismiss = { showAiChat = false },
        onOpenSettings = {
          showAiChat = false
          navigateToAiSettings()
        },
    )
  }

  ExperienceScreen(
      oneExperienceScreenModel = oneExperienceScreenModel,
      timelineUi =
          ExperienceTimelineUi(
              timelineDisplayOption = timelineDisplayOption,
              timeDisplayOption =
                  viewModel.timeDisplayOptionFlow.collectAsStateWithLifecycle().value,
              savedTimeDisplayOption =
                  viewModel.savedTimeDisplayOption.collectAsStateWithLifecycle().value,
              onChangeTimeDisplayOption = viewModel::saveTimeDisplayOption,
              isOralDisclaimerHidden =
                  viewModel.isOralTimelineDisclaimerHidden.collectAsStateWithLifecycle().value,
              onChangeIsOralDisclaimerHidden = viewModel::saveOralDisclaimerIsHidden,
          ),
      displayUi =
          ExperienceDisplayUi(
              areDosageDotsHidden =
                  viewModel.areDosageDotsHiddenFlow.collectAsStateWithLifecycle().value,
              isTimelineHidden = viewModel.isTimelineHiddenFlow.collectAsStateWithLifecycle().value,
              matchedAchievements =
                  viewModel.matchedAchievementsFlow.collectAsStateWithLifecycle().value,
              isAiAssistantEnabled = isAiAssistantEnabled,
              isNotificationActive = isNotificationActive,
              onToggleNotification = {
                val needsPermission =
                    !isNotificationActive &&
                        android.os.Build.VERSION.SDK_INT >=
                            android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                  notificationPermissionLauncher.launch(
                      android.Manifest.permission.POST_NOTIFICATIONS
                  )
                } else {
                  viewModel.toggleNotification(context)
                }
              },
              onShareExperience = {
                (context as? androidx.activity.ComponentActivity)?.let(viewModel::shareExperience)
              },
              onToggleBloodPressureInSubstanceList = {
                viewModel.saveBloodPressureDisplay(
                    if (isBloodPressureInSubstanceList) BloodPressureDisplay.SEPARATE
                    else BloodPressureDisplay.COMBINED
                )
              },
          ),
      navCallbacks =
          ExperienceNavCallbacks(
              addIngestion = {
                viewModel.saveLastIngestionTimeOfExperience()
                navigateToAddIngestionSearch()
              },
              deleteExperience = { viewModel.deleteExperience(context, onDeleted = navigateBack) },
              navigateToEditExperienceScreen = navigateToEditExperienceScreen,
              navigateToExplainTimeline = navigateToExplainTimeline,
              navigateToIngestionScreen = navigateToIngestionScreen,
              navigateToAddRatingScreen = navigateToAddRatingScreen,
              navigateToAddTimedNoteScreen = navigateToAddTimedNoteScreen,
              navigateToTimelineScreen = navigateToTimelineScreen,
          ),
      extrasCallbacks =
          ExperienceExtrasCallbacks(
              saveIsFavorite = viewModel::saveIsFavorite,
              openAiChat = { showAiChat = true },
              navigateToEditRatingScreen = navigateToEditRatingScreen,
              navigateToEditTimedNoteScreen = navigateToEditTimedNoteScreen,
              navigateToRecordBloodPressureScreen = navigateToRecordBloodPressureScreen,
              navigateToEditBloodPressureScreen = navigateToEditBloodPressureScreen,
          ),
      vitalsUi =
          ExperienceVitalsUi(
              heartRateSamples = viewModel.heartRateSamplesFlow.collectAsStateWithLifecycle().value,
              sleepSamples = viewModel.sleepSamplesFlow.collectAsStateWithLifecycle().value,
              sleepStagesEnabled =
                  viewModel.sleepStagesEnabledFlow.collectAsStateWithLifecycle().value,
              isHeartRateButtonVisible = isVitalsEnabled && isHeartRateAvailable,
              onToggleHeartRate = {
                viewModel.toggleHeartRate(context) {
                  healthPermissionLauncher.launch(
                      HeartRateHealthConnect.permissions + SleepHealthConnect.permissions
                  )
                }
              },
              vitalSeries = viewModel.vitalSeriesFlow.collectAsStateWithLifecycle().value,
              bloodPressureReadings =
                  viewModel.bloodPressureReadingsFlow.collectAsStateWithLifecycle().value,
              isBloodPressureAvailable = isBloodPressureAvailable,
              isBloodPressureInSubstanceList = isBloodPressureInSubstanceList,
          ),
  )
}

private data class ExperienceTimelineUi(
    val timelineDisplayOption: TimelineDisplayOption,
    val timeDisplayOption: TimeDisplayOption,
    val savedTimeDisplayOption: SavedTimeDisplayOption,
    val onChangeTimeDisplayOption: (SavedTimeDisplayOption) -> Unit,
    val isOralDisclaimerHidden: Boolean,
    val onChangeIsOralDisclaimerHidden: (Boolean) -> Unit,
)

private data class ExperienceDisplayUi(
    val areDosageDotsHidden: Boolean,
    val isTimelineHidden: Boolean,
    val matchedAchievements: List<AchievementDef> = emptyList(),
    val isAiAssistantEnabled: Boolean = false,
    val isNotificationActive: Boolean = false,
    val onToggleNotification: () -> Unit = {},
    val onShareExperience: () -> Unit = {},
    val onToggleBloodPressureInSubstanceList: () -> Unit = {},
)

private data class ExperienceNavCallbacks(
    val addIngestion: () -> Unit,
    val deleteExperience: () -> Unit,
    val navigateToEditExperienceScreen: () -> Unit,
    val navigateToExplainTimeline: () -> Unit,
    val navigateToIngestionScreen: (ingestionId: Int) -> Unit,
    val navigateToAddRatingScreen: () -> Unit,
    val navigateToAddTimedNoteScreen: () -> Unit,
    val navigateToTimelineScreen: (consumerName: String) -> Unit,
)

private data class ExperienceExtrasCallbacks(
    val saveIsFavorite: (Boolean) -> Unit,
    val openAiChat: () -> Unit,
    val navigateToEditRatingScreen: (ratingId: Int) -> Unit,
    val navigateToEditTimedNoteScreen: (timedNoteId: Int) -> Unit,
    val navigateToRecordBloodPressureScreen: () -> Unit = {},
    val navigateToEditBloodPressureScreen: (recordId: String) -> Unit = {},
)

private data class ExperienceVitalsUi(
    val heartRateSamples: List<HeartRateSample> = emptyList(),
    val sleepSamples: List<SleepSessionSample> = emptyList(),
    val sleepStagesEnabled: Boolean = false,
    val isHeartRateButtonVisible: Boolean = false,
    val onToggleHeartRate: () -> Unit = {},
    val vitalSeries: List<VitalSeries> = emptyList(),
    val bloodPressureReadings: List<BloodPressureReading> = emptyList(),
    val isBloodPressureAvailable: Boolean = false,
    val isBloodPressureInSubstanceList: Boolean = false,
)

@Preview
@Composable
fun ExperienceScreenPreview(
    @PreviewParameter(
        OneExperienceScreenPreviewProvider::class,
        limit = 1,
    )
    oneExperienceScreenModel: OneExperienceScreenModel
) {
  JournalTheme {
    ExperienceScreen(
        oneExperienceScreenModel = oneExperienceScreenModel,
        timelineUi =
            ExperienceTimelineUi(
                timelineDisplayOption = TimelineDisplayOption.Loading,
                timeDisplayOption = TimeDisplayOption.RELATIVE_TO_START,
                savedTimeDisplayOption = SavedTimeDisplayOption.RELATIVE_TO_START,
                onChangeTimeDisplayOption = {},
                isOralDisclaimerHidden = false,
                onChangeIsOralDisclaimerHidden = {},
            ),
        displayUi =
            ExperienceDisplayUi(
                areDosageDotsHidden = false,
                isTimelineHidden = false,
            ),
        navCallbacks =
            ExperienceNavCallbacks(
                addIngestion = {},
                deleteExperience = {},
                navigateToEditExperienceScreen = {},
                navigateToExplainTimeline = {},
                navigateToIngestionScreen = {},
                navigateToAddRatingScreen = {},
                navigateToAddTimedNoteScreen = {},
                navigateToTimelineScreen = {},
            ),
        extrasCallbacks =
            ExperienceExtrasCallbacks(
                saveIsFavorite = {},
                openAiChat = {},
                navigateToEditRatingScreen = {},
                navigateToEditTimedNoteScreen = {},
            ),
        vitalsUi = ExperienceVitalsUi(),
    )
  }
}

@Composable
private fun ExperienceScreen(
    oneExperienceScreenModel: OneExperienceScreenModel,
    timelineUi: ExperienceTimelineUi,
    displayUi: ExperienceDisplayUi,
    navCallbacks: ExperienceNavCallbacks,
    extrasCallbacks: ExperienceExtrasCallbacks,
    vitalsUi: ExperienceVitalsUi,
) {
  Scaffold(
      topBar = {
        ExperienceTopBar(
            oneExperienceScreenModel = oneExperienceScreenModel,
            savedTimeDisplayOption = timelineUi.savedTimeDisplayOption,
            isAiAssistantEnabled = displayUi.isAiAssistantEnabled,
            callbacks =
                ExperienceTopBarCallbacks(
                    onChangeTimeDisplayOption = timelineUi.onChangeTimeDisplayOption,
                    deleteExperience = navCallbacks.deleteExperience,
                    openAiChat = extrasCallbacks.openAiChat,
                    navigateToEditExperienceScreen = navCallbacks.navigateToEditExperienceScreen,
                    saveIsFavorite = extrasCallbacks.saveIsFavorite,
                    navigateToAddTimedNoteScreen = navCallbacks.navigateToAddTimedNoteScreen,
                    navigateToAddRatingScreen = navCallbacks.navigateToAddRatingScreen,
                    addIngestion = navCallbacks.addIngestion,
                ),
            extras =
                ExperienceTopBarExtras(
                    isNotificationActive = displayUi.isNotificationActive,
                    onToggleNotification = displayUi.onToggleNotification,
                    onShareExperience = displayUi.onShareExperience,
                    isBloodPressureAvailable = vitalsUi.isBloodPressureAvailable,
                    navigateToRecordBloodPressureScreen =
                        extrasCallbacks.navigateToRecordBloodPressureScreen,
                ),
        )
      },
      floatingActionButton = {
        AddIngestionFAB(oneExperienceScreenModel, navCallbacks.addIngestion)
      },
  ) { padding ->
    Column(
        modifier =
            Modifier.verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = horizontalPadding)
    ) {
      ExperiencePrimarySections(
          oneExperienceScreenModel = oneExperienceScreenModel,
          timelineUi = timelineUi,
          displayUi = displayUi,
          navCallbacks = navCallbacks,
          extrasCallbacks = extrasCallbacks,
          vitalsUi = vitalsUi,
      )
      ExperienceSecondarySections(
          oneExperienceScreenModel = oneExperienceScreenModel,
          timelineUi = timelineUi,
          displayUi = displayUi,
          navCallbacks = navCallbacks,
          extrasCallbacks = extrasCallbacks,
          vitalsUi = vitalsUi,
      )
      Spacer(modifier = Modifier.height(60.dp))
    }
  }
}

@Composable
private fun ExperiencePrimarySections(
    oneExperienceScreenModel: OneExperienceScreenModel,
    timelineUi: ExperienceTimelineUi,
    displayUi: ExperienceDisplayUi,
    navCallbacks: ExperienceNavCallbacks,
    extrasCallbacks: ExperienceExtrasCallbacks,
    vitalsUi: ExperienceVitalsUi,
) {
  val verticalCardPadding = 4.dp
  MyTimelineSection(
      timelineDisplayOption = timelineUi.timelineDisplayOption,
      verticalCardPadding = verticalCardPadding,
      navigateToExplainTimeline = navCallbacks.navigateToExplainTimeline,
      navigateToTimelineScreen = navCallbacks.navigateToTimelineScreen,
      oneExperienceScreenModel = oneExperienceScreenModel,
      timeDisplayOption = timelineUi.timeDisplayOption,
      isOralDisclaimerHidden = timelineUi.isOralDisclaimerHidden,
      onChangeIsOralDisclaimerHidden = timelineUi.onChangeIsOralDisclaimerHidden,
      heartRateSamples = vitalsUi.heartRateSamples,
      sleepSamples = vitalsUi.sleepSamples,
      sleepStagesEnabled = vitalsUi.sleepStagesEnabled,
      isHeartRateButtonVisible = vitalsUi.isHeartRateButtonVisible,
      onToggleHeartRate = vitalsUi.onToggleHeartRate,
      vitalSeries = vitalsUi.vitalSeries,
      bloodPressureReadings = vitalsUi.bloodPressureReadings,
      isBloodPressureInSubstanceList = vitalsUi.isBloodPressureInSubstanceList,
      onToggleBloodPressureInSubstanceList = displayUi.onToggleBloodPressureInSubstanceList,
  )
  val bloodPressureInSubstances =
      vitalsUi.isBloodPressureInSubstanceList && vitalsUi.bloodPressureReadings.isNotEmpty()
  if (oneExperienceScreenModel.ingestionElements.isNotEmpty() || bloodPressureInSubstances) {
    MyIngestionList(
        verticalCardPadding = verticalCardPadding,
        oneExperienceScreenModel = oneExperienceScreenModel,
        areDosageDotsHidden = displayUi.areDosageDotsHidden,
        navigateToIngestionScreen = navCallbacks.navigateToIngestionScreen,
        timeDisplayOption = timelineUi.timeDisplayOption,
        bloodPressureReadings =
            if (bloodPressureInSubstances) vitalsUi.bloodPressureReadings else emptyList(),
        navigateToEditBloodPressureScreen = extrasCallbacks.navigateToEditBloodPressureScreen,
    )
  }
  val cumulativeDoses = oneExperienceScreenModel.cumulativeDoses
  if (cumulativeDoses.isNotEmpty()) {
    CumulativeDosesSection(
        verticalCardPadding = verticalCardPadding,
        cumulativeDoses = cumulativeDoses,
        areDosageDotsHidden = displayUi.areDosageDotsHidden,
    )
  }
}

@Composable
private fun ExperienceSecondarySections(
    oneExperienceScreenModel: OneExperienceScreenModel,
    timelineUi: ExperienceTimelineUi,
    displayUi: ExperienceDisplayUi,
    navCallbacks: ExperienceNavCallbacks,
    extrasCallbacks: ExperienceExtrasCallbacks,
    vitalsUi: ExperienceVitalsUi,
) {
  val verticalCardPadding = 4.dp
  val timedNotesSorted = oneExperienceScreenModel.timedNotesSorted
  if (timedNotesSorted.isNotEmpty()) {
    TimedNotesSection(
        verticalCardPadding = verticalCardPadding,
        timedNotesSorted = timedNotesSorted,
        navigateToEditTimedNoteScreen = extrasCallbacks.navigateToEditTimedNoteScreen,
        timeDisplayOption = timelineUi.timeDisplayOption,
        firstIngestionTime = oneExperienceScreenModel.firstIngestionTime,
    )
  }
  if (oneExperienceScreenModel.ratings.isNotEmpty()) {
    ShulginRatingsSection(
        verticalCardPadding = verticalCardPadding,
        oneExperienceScreenModel = oneExperienceScreenModel,
        navigateToEditRatingScreen = extrasCallbacks.navigateToEditRatingScreen,
        timeDisplayOption = timelineUi.timeDisplayOption,
    )
  }
  if (vitalsUi.bloodPressureReadings.isNotEmpty() && !vitalsUi.isBloodPressureInSubstanceList) {
    BloodPressureReadingsSection(
        verticalCardPadding = verticalCardPadding,
        readings = vitalsUi.bloodPressureReadings,
        navigateToEditBloodPressureScreen = extrasCallbacks.navigateToEditBloodPressureScreen,
        timeDisplayOption = timelineUi.timeDisplayOption,
        firstIngestionTime = oneExperienceScreenModel.firstIngestionTime,
    )
  }
  if (oneExperienceScreenModel.notes.isNotBlank()) {
    NotesSection(
        verticalCardPadding = verticalCardPadding,
        navigateToEditExperienceScreen = navCallbacks.navigateToEditExperienceScreen,
        oneExperienceScreenModel = oneExperienceScreenModel,
    )
  }
  oneExperienceScreenModel.consumersWithIngestions.forEach { consumerWithIngestions ->
    ConsumerSection(
        verticalCardPadding = verticalCardPadding,
        consumerWithIngestions = consumerWithIngestions,
        navigateToTimelineScreen = navCallbacks.navigateToTimelineScreen,
        timeDisplayOption = timelineUi.timeDisplayOption,
        areDosageDotsHidden = displayUi.areDosageDotsHidden,
        navigateToIngestionScreen = navCallbacks.navigateToIngestionScreen,
        isTimelineHidden = displayUi.isTimelineHidden,
    )
  }
  val interactions = oneExperienceScreenModel.interactions
  AnimatedVisibility(visible = interactions.isNotEmpty()) {
    ExperienceInteractionsSection(
        verticalCardPadding = verticalCardPadding,
        interactions = interactions,
        oneExperienceScreenModel = oneExperienceScreenModel,
    )
  }
  AnimatedVisibility(visible = displayUi.matchedAchievements.isNotEmpty()) {
    ExperienceAchievementsSection(
        verticalCardPadding = verticalCardPadding,
        matchedAchievements = displayUi.matchedAchievements,
    )
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ExperienceInteractionsSection(
    verticalCardPadding: Dp,
    interactions: List<Interaction>,
    oneExperienceScreenModel: OneExperienceScreenModel,
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
    CardTitle(title = "Interactions")
    interactions.forEachIndexed { index, interaction ->
      InteractionRow(interaction = interaction)
      if (index < interactions.size - 1) {
        HorizontalDivider()
      }
    }
    Spacer(modifier = Modifier.height(5.dp))
    Text(
        text = "Explanations",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = horizontalPadding),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(horizontal = horizontalPadding),
    ) {
      oneExperienceScreenModel.interactionExplanations.forEach {
        val uriHandler = LocalUriHandler.current
        SuggestionChip(
            onClick = {
              uriHandler.openUri(it.url)
            },
            label = { Text(it.name) },
        )
      }
    }
  }
}

@Composable
private fun ExperienceAchievementsSection(
    verticalCardPadding: Dp,
    matchedAchievements: List<AchievementDef>,
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding).fillMaxWidth()) {
    CardTitle(title = "Achievements")
    HorizontalDivider()
    matchedAchievements.forEachIndexed { index, achievement ->
      Row(
          modifier =
              Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
              text = achievement.title,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.primary,
          )
          Text(
              text = achievement.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        val tier = achievement.tier
        if (tier != null) {
          TierBadge(tier = tier)
        }
      }
      if (index < matchedAchievements.size - 1) {
        HorizontalDivider()
      }
    }
  }
}

@Composable
private fun ConsumerSection(
    verticalCardPadding: Dp,
    consumerWithIngestions: ConsumerWithIngestions,
    navigateToTimelineScreen: (consumerName: String) -> Unit,
    timeDisplayOption: TimeDisplayOption,
    areDosageDotsHidden: Boolean,
    navigateToIngestionScreen: (ingestionId: Int) -> Unit,
    isTimelineHidden: Boolean,
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
    val redoseChipState by
        foo.pilz.freaklog.ui.tabs.journal.experience.redose.rememberRedoseChipState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      CardTitle(title = consumerWithIngestions.consumerName)
      if (!isTimelineHidden) {
        IconButton(onClick = { navigateToTimelineScreen(consumerWithIngestions.consumerName) }) {
          Icon(
              Icons.Default.OpenInFull,
              contentDescription = "Expand timeline",
          )
        }
      }
    }
    when (val timelineDisplayOption = consumerWithIngestions.timelineDisplayOption) {
      TimelineDisplayOption.Hidden -> {}
      TimelineDisplayOption.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      TimelineDisplayOption.NotWorthDrawing -> {}
      is TimelineDisplayOption.Shown -> {
        val timelineModel = timelineDisplayOption.allTimelinesModel
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          AllTimelines(
              model = timelineModel,
              timeDisplayOption = timeDisplayOption,
              isShowingCurrentTime = true,
              modifier = Modifier.fillMaxWidth().height(200.dp),
          )
        }
      }
    }
    HorizontalDivider()
    val allTimesSortedMap =
        remember(consumerWithIngestions.ingestionElements) {
          consumerWithIngestions.ingestionElements.map {
            it.ingestionWithCompanionAndCustomUnit.ingestion.time
          }
        }
    consumerWithIngestions.ingestionElements.forEachIndexed { index, ingestionElement ->
      IngestionRow(
          ingestionElement = ingestionElement,
          areDosageDotsHidden = areDosageDotsHidden,
          modifier =
              Modifier.clickable {
                    navigateToIngestionScreen(
                        ingestionElement.ingestionWithCompanionAndCustomUnit.ingestion.id
                    )
                  }
                  .fillMaxWidth()
                  .padding(vertical = 5.dp, horizontal = horizontalPadding),
      ) {
        val ingestion = ingestionElement.ingestionWithCompanionAndCustomUnit.ingestion
        IngestionTimeOrDurationText(
            time = ingestion.time,
            endTime = ingestion.endTime,
            index = index,
            timeDisplayOption = timeDisplayOption,
            allTimesSortedMap = allTimesSortedMap,
        )
      }
      foo.pilz.freaklog.ui.tabs.journal.experience.redose.RedoseChip(
          ingestionTime = ingestionElement.ingestionWithCompanionAndCustomUnit.ingestion.time,
          roaDuration = ingestionElement.roaDuration,
          state = redoseChipState,
          modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
      )
      if (index < consumerWithIngestions.ingestionElements.size - 1) {
        HorizontalDivider()
      }
    }
  }
}

@Composable
private fun NotesSection(
    verticalCardPadding: Dp,
    navigateToEditExperienceScreen: () -> Unit,
    oneExperienceScreenModel: OneExperienceScreenModel,
) {
  ElevatedCard(
      modifier =
          Modifier.padding(vertical = verticalCardPadding).fillMaxWidth().clickable {
            navigateToEditExperienceScreen()
          }
  ) {
    CardTitle(title = "Notes")
    Column(modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 10.dp)) {
      Text(text = oneExperienceScreenModel.notes)
      if (oneExperienceScreenModel.locationName.isNotBlank()) {
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = "Location: ${oneExperienceScreenModel.locationName}")
      }
    }
  }
}

@Composable
private fun ShulginRatingsSection(
    verticalCardPadding: Dp,
    oneExperienceScreenModel: OneExperienceScreenModel,
    navigateToEditRatingScreen: (ratingId: Int) -> Unit,
    timeDisplayOption: TimeDisplayOption,
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
    CardTitle(title = "Shulgin ratings")
    HorizontalDivider()
    val ratingsWithTime =
        remember(oneExperienceScreenModel.ratings) {
          oneExperienceScreenModel.ratings
              .mapNotNull { rating ->
                val time = rating.time
                if (time != null) {
                  Pair(time, rating)
                } else {
                  null
                }
              }
              .sortedBy { it.first }
        }
    ratingsWithTime.forEachIndexed { index, pair ->
      TimedRatingRow(
          modifier =
              Modifier.clickable {
                    navigateToEditRatingScreen(pair.second.id)
                  }
                  .fillMaxWidth()
                  .padding(vertical = 8.dp, horizontal = horizontalPadding),
          ratingSign = pair.second.option.sign,
      ) {
        NoteOrRatingTimeOrDurationText(
            time = pair.first,
            timeDisplayOption = timeDisplayOption,
            firstIngestionTime = oneExperienceScreenModel.firstIngestionTime,
        )
      }
      if (index < ratingsWithTime.size - 1) {
        HorizontalDivider()
      }
    }
    val overallRating = oneExperienceScreenModel.ratings.firstOrNull { it.time == null }
    if (overallRating != null) {
      if (ratingsWithTime.isNotEmpty()) {
        HorizontalDivider()
      }
      OverallRatingRow(
          modifier =
              Modifier.clickable {
                    navigateToEditRatingScreen(overallRating.id)
                  }
                  .fillMaxWidth()
                  .padding(vertical = 8.dp, horizontal = horizontalPadding),
          ratingSign = overallRating.option.sign,
      )
    }
  }
}

@Composable
private fun TimedNotesSection(
    verticalCardPadding: Dp,
    timedNotesSorted: List<TimedNote>,
    navigateToEditTimedNoteScreen: (timedNoteId: Int) -> Unit,
    timeDisplayOption: TimeDisplayOption,
    firstIngestionTime: Instant,
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
    CardTitle(title = "Timed notes")
    if (timedNotesSorted.isNotEmpty()) {
      HorizontalDivider()
    }
    timedNotesSorted.forEachIndexed { index, timedNote ->
      TimedNoteRow(
          timedNote = timedNote,
          modifier =
              Modifier.clickable {
                    navigateToEditTimedNoteScreen(timedNote.id)
                  }
                  .fillMaxWidth()
                  .padding(vertical = 5.dp, horizontal = horizontalPadding),
      ) {
        NoteOrRatingTimeOrDurationText(
            time = timedNote.time,
            timeDisplayOption = timeDisplayOption,
            firstIngestionTime = firstIngestionTime,
        )
      }
      if (index < timedNotesSorted.size - 1) {
        HorizontalDivider()
      }
    }
  }
}

@Composable
private fun CumulativeDosesSection(
    verticalCardPadding: Dp,
    cumulativeDoses: List<CumulativeDose>,
    areDosageDotsHidden: Boolean,
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
    CardTitle(title = "Your cumulative doses")
    if (cumulativeDoses.isNotEmpty()) {
      HorizontalDivider()
    }
    cumulativeDoses.forEachIndexed { index, cumulativeDose ->
      CumulativeDoseRow(
          cumulativeDose = cumulativeDose,
          areDosageDotsHidden = areDosageDotsHidden,
          modifier =
              Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = horizontalPadding),
      )
      if (index < cumulativeDoses.size - 1) {
        HorizontalDivider()
      }
    }
  }
}

@Composable
private fun MyIngestionList(
    verticalCardPadding: Dp,
    oneExperienceScreenModel: OneExperienceScreenModel,
    areDosageDotsHidden: Boolean,
    navigateToIngestionScreen: (ingestionId: Int) -> Unit,
    timeDisplayOption: TimeDisplayOption,
    bloodPressureReadings: List<BloodPressureReading> = emptyList(),
    navigateToEditBloodPressureScreen: (recordId: String) -> Unit = {},
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
    val redoseChipState by
        foo.pilz.freaklog.ui.tabs.journal.experience.redose.rememberRedoseChipState()
    CardTitle(title = oneExperienceScreenModel.firstIngestionTime.getDateWithWeekdayText())
    if (
        oneExperienceScreenModel.ingestionElements.isNotEmpty() ||
            bloodPressureReadings.isNotEmpty()
    ) {
      HorizontalDivider()
    }
    val ingestionElements = oneExperienceScreenModel.ingestionElements
    val allTimesSortedMap =
        remember(ingestionElements) {
          ingestionElements.map { it.ingestionWithCompanionAndCustomUnit.ingestion.time }
        }
    val partitionedReadings =
        remember(ingestionElements, bloodPressureReadings) {
          partitionBloodPressureReadings(ingestionElements, bloodPressureReadings)
        }
    partitionedReadings.beforeFirstIngestion.forEach { reading ->
      BloodPressureReadingRow(
          reading = reading,
          timeDisplayOption = timeDisplayOption,
          firstIngestionTime = oneExperienceScreenModel.firstIngestionTime,
          onClick = { navigateToEditBloodPressureScreen(reading.id) },
      )
      HorizontalDivider()
    }
    ingestionElements.forEachIndexed { index, ingestionElement ->
      val readingsAfter = partitionedReadings.afterEachIngestion[index]
      IngestionRow(
          ingestionElement = ingestionElement,
          areDosageDotsHidden = areDosageDotsHidden,
          modifier =
              Modifier.clickable {
                    navigateToIngestionScreen(
                        ingestionElement.ingestionWithCompanionAndCustomUnit.ingestion.id
                    )
                  }
                  .fillMaxWidth()
                  .padding(vertical = 5.dp, horizontal = horizontalPadding),
      ) {
        val ingestion = ingestionElement.ingestionWithCompanionAndCustomUnit.ingestion
        IngestionTimeOrDurationText(
            time = ingestion.time,
            endTime = ingestion.endTime,
            index = index,
            timeDisplayOption = timeDisplayOption,
            allTimesSortedMap = allTimesSortedMap,
        )
      }
      foo.pilz.freaklog.ui.tabs.journal.experience.redose.RedoseChip(
          ingestionTime = ingestionElement.ingestionWithCompanionAndCustomUnit.ingestion.time,
          roaDuration = ingestionElement.roaDuration,
          state = redoseChipState,
          modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 2.dp),
      )
      readingsAfter.forEach { reading ->
        HorizontalDivider()
        BloodPressureReadingRow(
            reading = reading,
            timeDisplayOption = timeDisplayOption,
            firstIngestionTime = oneExperienceScreenModel.firstIngestionTime,
            onClick = { navigateToEditBloodPressureScreen(reading.id) },
        )
      }
      val isLastIngestion = index == oneExperienceScreenModel.ingestionElements.size - 1
      if (isLastIngestion) {
        if (oneExperienceScreenModel.isCurrentExperience) {
          if (timeDisplayOption == TimeDisplayOption.TIME_BETWEEN) {
            HorizontalDivider()
            LastIngestionRelativeToNowText(
                lastIngestionTime =
                    ingestionElement.ingestionWithCompanionAndCustomUnit.ingestion.time
            )
          } else if (timeDisplayOption == TimeDisplayOption.RELATIVE_TO_START) {
            HorizontalDivider()
            NowRelativeToStartTimeText(startTime = oneExperienceScreenModel.firstIngestionTime)
          }
        }
      } else {
        HorizontalDivider()
      }
    }
  }
}

@Composable
private fun BloodPressureReadingsSection(
    verticalCardPadding: Dp,
    readings: List<BloodPressureReading>,
    navigateToEditBloodPressureScreen: (recordId: String) -> Unit,
    timeDisplayOption: TimeDisplayOption,
    firstIngestionTime: Instant,
) {
  ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
    CardTitle(title = "Blood pressure")
    HorizontalDivider()
    readings.forEachIndexed { index, reading ->
      BloodPressureReadingRow(
          reading = reading,
          timeDisplayOption = timeDisplayOption,
          firstIngestionTime = firstIngestionTime,
          onClick = { navigateToEditBloodPressureScreen(reading.id) },
      )
      if (index < readings.size - 1) {
        HorizontalDivider()
      }
    }
  }
}

@Composable
private fun BloodPressureReadingRow(
    reading: BloodPressureReading,
    timeDisplayOption: TimeDisplayOption,
    firstIngestionTime: Instant,
    onClick: () -> Unit,
) {
  // Readings stored by older app versions live in the journal database and have no edit screen.
  val isEditable = !reading.id.startsWith(LEGACY_BLOOD_PRESSURE_ID_PREFIX)
  Column(
      modifier =
          Modifier.clickable(enabled = isEditable, onClick = onClick)
              .fillMaxWidth()
              .padding(vertical = 5.dp, horizontal = horizontalPadding),
  ) {
    Text(
        text = "${reading.systolic.roundToInt()}/${reading.diastolic.roundToInt()} mmHg",
        style = MaterialTheme.typography.bodyLarge,
    )
    NoteOrRatingTimeOrDurationText(
        time = reading.time,
        timeDisplayOption = timeDisplayOption,
        firstIngestionTime = firstIngestionTime,
    )
  }
}

@Composable
private fun MyTimelineSection(
    timelineDisplayOption: TimelineDisplayOption,
    verticalCardPadding: Dp,
    navigateToExplainTimeline: () -> Unit,
    navigateToTimelineScreen: (consumerName: String) -> Unit,
    oneExperienceScreenModel: OneExperienceScreenModel,
    timeDisplayOption: TimeDisplayOption,
    isOralDisclaimerHidden: Boolean,
    onChangeIsOralDisclaimerHidden: (Boolean) -> Unit,
    heartRateSamples: List<HeartRateSample> = emptyList(),
    sleepSamples: List<SleepSessionSample> = emptyList(),
    sleepStagesEnabled: Boolean = false,
    isHeartRateButtonVisible: Boolean = false,
    onToggleHeartRate: () -> Unit = {},
    vitalSeries: List<VitalSeries> = emptyList(),
    bloodPressureReadings: List<BloodPressureReading> = emptyList(),
    isBloodPressureInSubstanceList: Boolean = false,
    onToggleBloodPressureInSubstanceList: () -> Unit = {},
) {
  when (timelineDisplayOption) {
    TimelineDisplayOption.Hidden -> {}
    TimelineDisplayOption.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    TimelineDisplayOption.NotWorthDrawing -> {}
    is TimelineDisplayOption.Shown -> {
      val timelineModel = timelineDisplayOption.allTimelinesModel
      ElevatedCard(modifier = Modifier.padding(vertical = verticalCardPadding)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
          CardTitle(title = "Effect timeline")
          Row {
            TextButton(onClick = navigateToExplainTimeline) {
              Text(text = "Info")
            }
            if (isHeartRateButtonVisible) {
              IconButton(onClick = onToggleHeartRate) {
                val isShown =
                    heartRateSamples.isNotEmpty() ||
                        sleepSamples.isNotEmpty() ||
                        vitalSeries.isNotEmpty()
                Icon(
                    if (isShown) Icons.Filled.MonitorHeart else Icons.Outlined.MonitorHeart,
                    contentDescription = if (isShown) "Hide vitals" else "Show vitals",
                )
              }
            }
            if (bloodPressureReadings.isNotEmpty()) {
              IconButton(onClick = onToggleBloodPressureInSubstanceList) {
                val icon =
                    if (isBloodPressureInSubstanceList) {
                      Icons.Filled.Bloodtype
                    } else {
                      Icons.Outlined.Bloodtype
                    }
                Icon(
                    icon,
                    contentDescription =
                        if (isBloodPressureInSubstanceList) {
                          "Separate blood pressure list"
                        } else {
                          "Merge blood pressure into substances list"
                        },
                )
              }
            }
            IconButton(onClick = { navigateToTimelineScreen(YOU) }) {
              Icon(
                  Icons.Default.OpenInFull,
                  contentDescription = "Expand timeline",
              )
            }
          }
        }
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding).padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          AllTimelines(
              model = timelineModel,
              timeDisplayOption = timeDisplayOption,
              isShowingCurrentTime = true,
              heartRateSamples = heartRateSamples,
              sleepSamples = sleepSamples,
              showSleepStages = sleepStagesEnabled,
              bloodPressureReadings = bloodPressureReadings,
              vitalSeries = vitalSeries,
              modifier = Modifier.fillMaxWidth().height(200.dp),
          )
          val hasOralIngestion =
              oneExperienceScreenModel.ingestionElements.any {
                it.ingestionWithCompanionAndCustomUnit.ingestion.administrationRoute ==
                    AdministrationRoute.ORAL
              }
          if (hasOralIngestion && !isOralDisclaimerHidden) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                  text = FULL_STOMACH_DISCLAIMER,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.weight(1f),
              )
              IconButton(onClick = { onChangeIsOralDisclaimerHidden(true) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close disclaimer",
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AddIngestionFAB(
    oneExperienceScreenModel: OneExperienceScreenModel,
    addIngestion: () -> Unit,
) {
  val wasAnyIngestionCreatedInLast4Hours =
      oneExperienceScreenModel.ingestionElements
          .mapNotNull { it.ingestionWithCompanionAndCustomUnit.ingestion.creationDate }
          .any {
            it > Instant.now().minus(4, ChronoUnit.HOURS)
          }
  val performHaptic = rememberHaptic()
  if (oneExperienceScreenModel.isCurrentExperience || wasAnyIngestionCreatedInLast4Hours) {
    ExtendedFloatingActionButton(
        onClick = {
          performHaptic(HapticType.HEAVY_CLICK)
          addIngestion()
        },
        icon = {
          Icon(
              Icons.Filled.Add,
              contentDescription = "Add",
          )
        },
        text = { Text("Ingestion") },
    )
  }
}

private data class ExperienceTopBarCallbacks(
    val onChangeTimeDisplayOption: (SavedTimeDisplayOption) -> Unit,
    val deleteExperience: () -> Unit,
    val openAiChat: () -> Unit,
    val navigateToEditExperienceScreen: () -> Unit,
    val saveIsFavorite: (Boolean) -> Unit,
    val navigateToAddTimedNoteScreen: () -> Unit,
    val navigateToAddRatingScreen: () -> Unit,
    val addIngestion: () -> Unit,
)

private data class ExperienceTopBarExtras(
    val isNotificationActive: Boolean = false,
    val onToggleNotification: () -> Unit = {},
    val onShareExperience: () -> Unit = {},
    val isBloodPressureAvailable: Boolean = false,
    val navigateToRecordBloodPressureScreen: () -> Unit = {},
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExperienceTopBar(
    oneExperienceScreenModel: OneExperienceScreenModel,
    savedTimeDisplayOption: SavedTimeDisplayOption,
    isAiAssistantEnabled: Boolean,
    callbacks: ExperienceTopBarCallbacks,
    extras: ExperienceTopBarExtras = ExperienceTopBarExtras(),
) {
  // Up to six actions leave no room beside them: the title gets its own line below.
  MediumTopAppBar(
      navigationIcon = { NavigateBackButton() },
      title = {
        Text(oneExperienceScreenModel.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
      },
      actions = {
        IconButton(onClick = extras.onShareExperience) {
          Icon(
              androidx.compose.material.icons.Icons.Outlined.Share,
              contentDescription = "Share as image",
          )
        }
        IconButton(onClick = extras.onToggleNotification) {
          Icon(
              if (extras.isNotificationActive) {
                androidx.compose.material.icons.Icons.Filled.Notifications
              } else {
                androidx.compose.material.icons.Icons.Outlined.Notifications
              },
              contentDescription =
                  if (extras.isNotificationActive) {
                    "Stop live notification"
                  } else {
                    "Start live notification"
                  },
          )
        }
        if (isAiAssistantEnabled) {
          IconButton(onClick = callbacks.openAiChat) {
            Icon(
                androidx.compose.material.icons.Icons.Outlined.SmartToy,
                contentDescription = "AI assistant",
            )
          }
        }
        ExperienceTimeDisplayMenu(
            savedTimeDisplayOption = savedTimeDisplayOption,
            onChangeTimeDisplayOption = callbacks.onChangeTimeDisplayOption,
        )
        ExperienceEditMenuButton(
            isFavorite = oneExperienceScreenModel.isFavorite,
            navigateToEditExperienceScreen = callbacks.navigateToEditExperienceScreen,
            saveIsFavorite = callbacks.saveIsFavorite,
            deleteExperience = callbacks.deleteExperience,
        )
        ExperienceAddMenuButton(
            addIngestion = callbacks.addIngestion,
            navigateToAddTimedNoteScreen = callbacks.navigateToAddTimedNoteScreen,
            navigateToAddRatingScreen = callbacks.navigateToAddRatingScreen,
            isBloodPressureAvailable = extras.isBloodPressureAvailable,
            navigateToRecordBloodPressureScreen = extras.navigateToRecordBloodPressureScreen,
        )
      },
  )
}

@Composable
private fun ExperienceTimeDisplayMenu(
    savedTimeDisplayOption: SavedTimeDisplayOption,
    onChangeTimeDisplayOption: (SavedTimeDisplayOption) -> Unit,
) {
  var areTimeOptionsExpanded by remember { mutableStateOf(false) }
  IconButton(onClick = { areTimeOptionsExpanded = true }) {
    Icon(Icons.Outlined.Timer, contentDescription = "Time display option")
  }
  DropdownMenu(
      expanded = areTimeOptionsExpanded,
      onDismissRequest = { areTimeOptionsExpanded = false },
  ) {
    SavedTimeDisplayOption.entries.forEach { option ->
      DropdownMenuItem(
          text = { Text(option.text) },
          onClick = {
            onChangeTimeDisplayOption(option)
            areTimeOptionsExpanded = false
          },
          leadingIcon = {
            if (option == savedTimeDisplayOption) {
              Icon(
                  Icons.Filled.Check,
                  contentDescription = null,
                  modifier = Modifier.size(ButtonDefaults.IconSize),
              )
            }
          },
      )
    }
  }
}

@Composable
private fun ExperienceEditMenuButton(
    isFavorite: Boolean,
    navigateToEditExperienceScreen: () -> Unit,
    saveIsFavorite: (Boolean) -> Unit,
    deleteExperience: () -> Unit,
) {
  var areEditOptionsExpanded by remember { mutableStateOf(false) }
  var isShowingDeleteDialog by remember { mutableStateOf(false) }
  IconButton(onClick = { areEditOptionsExpanded = true }) {
    Icon(
        Icons.Default.Edit,
        contentDescription = "Edit options",
    )
  }
  AnimatedVisibility(visible = isShowingDeleteDialog) {
    DeleteExperienceDialog(
        onConfirm = {
          isShowingDeleteDialog = false
          deleteExperience()
        },
        onDismiss = { isShowingDeleteDialog = false },
    )
  }
  DropdownMenu(
      expanded = areEditOptionsExpanded,
      onDismissRequest = { areEditOptionsExpanded = false },
  ) {
    DropdownMenuItem(
        text = { Text("Edit title/notes/location") },
        onClick = {
          navigateToEditExperienceScreen()
          areEditOptionsExpanded = false
        },
        leadingIcon = {
          Icon(
              Icons.Outlined.Edit,
              contentDescription = "Edit experience",
              modifier = Modifier.size(ButtonDefaults.IconSize),
          )
        },
    )
    DropdownMenuItem(
        text = { Text(if (isFavorite) "Unmark favorite" else "Mark favorite") },
        onClick = {
          saveIsFavorite(!isFavorite)
          areEditOptionsExpanded = false
        },
        leadingIcon = {
          Icon(
              if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
              contentDescription = if (isFavorite) "Unmark favorite" else "Mark favorite",
              modifier = Modifier.size(ButtonDefaults.IconSize),
          )
        },
    )
    DropdownMenuItem(
        text = { Text("Delete experience") },
        onClick = {
          isShowingDeleteDialog = true
          areEditOptionsExpanded = false
        },
        leadingIcon = {
          Icon(
              Icons.Outlined.Delete,
              contentDescription = "Delete experience",
              modifier = Modifier.size(ButtonDefaults.IconSize),
          )
        },
    )
  }
}

@Composable
private fun DeleteExperienceDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(text = "Delete experience?") },
      text = { Text("This will also delete all its ingestions.") },
      confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun ExperienceAddMenuButton(
    addIngestion: () -> Unit,
    navigateToAddTimedNoteScreen: () -> Unit,
    navigateToAddRatingScreen: () -> Unit,
    isBloodPressureAvailable: Boolean,
    navigateToRecordBloodPressureScreen: () -> Unit,
) {
  var areAddOptionsExpanded by remember { mutableStateOf(false) }
  IconButton(onClick = { areAddOptionsExpanded = true }) {
    Icon(
        Icons.Outlined.Add,
        contentDescription = "Add options",
    )
  }
  DropdownMenu(
      expanded = areAddOptionsExpanded,
      onDismissRequest = { areAddOptionsExpanded = false },
  ) {
    DropdownMenuItem(
        text = { Text("Add ingestion") },
        onClick = {
          addIngestion()
          areAddOptionsExpanded = false
        },
        leadingIcon = {
          Icon(
              Icons.Outlined.Add,
              contentDescription = null,
              modifier = Modifier.size(ButtonDefaults.IconSize),
          )
        },
    )
    DropdownMenuItem(
        text = { Text("Add timed note") },
        onClick = {
          navigateToAddTimedNoteScreen()
          areAddOptionsExpanded = false
        },
        leadingIcon = {
          Icon(
              Icons.AutoMirrored.Outlined.NoteAdd,
              contentDescription = "Add timed note",
              modifier = Modifier.size(ButtonDefaults.IconSize),
          )
        },
    )
    DropdownMenuItem(
        text = { Text("Add Shulgin rating") },
        onClick = {
          navigateToAddRatingScreen()
          areAddOptionsExpanded = false
        },
        leadingIcon = {
          Icon(
              Icons.Outlined.ExposurePlus2,
              contentDescription = "Add Shulgin rating",
              modifier = Modifier.size(ButtonDefaults.IconSize),
          )
        },
    )
    if (isBloodPressureAvailable) {
      DropdownMenuItem(
          text = { Text("Record blood pressure") },
          onClick = {
            navigateToRecordBloodPressureScreen()
            areAddOptionsExpanded = false
          },
          leadingIcon = {
            Icon(
                Icons.Outlined.Bloodtype,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
          },
      )
    }
  }
}

@Composable
private fun LastIngestionRelativeToNowText(lastIngestionTime: Instant) {
  val now: MutableState<Instant> = remember { mutableStateOf(Instant.now()) }
  LaunchedEffect(key1 = "updateTime") {
    while (true) {
      delay(10000L) // update every 10 seconds
      now.value = Instant.now()
    }
  }
  val isInPast = lastIngestionTime < now.value
  val relativeTime =
      if (isInPast) {
        "Last ingestion was " +
            getDurationText(
                fromInstant = lastIngestionTime,
                toInstant = now.value,
            ) +
            " ago"
      } else {
        "Last ingestion in " +
            getDurationText(
                fromInstant = lastIngestionTime,
                toInstant = now.value,
            )
      }
  Text(
      text = relativeTime,
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(vertical = 5.dp, horizontal = horizontalPadding),
  )
}

private class PartitionedBloodPressureReadings(
    val beforeFirstIngestion: List<BloodPressureReading>,
    val afterEachIngestion: List<List<BloodPressureReading>>,
)

private fun partitionBloodPressureReadings(
    ingestionElements: List<IngestionElement>,
    readings: List<BloodPressureReading>,
): PartitionedBloodPressureReadings {
  if (readings.isEmpty() || ingestionElements.isEmpty()) {
    return PartitionedBloodPressureReadings(
        beforeFirstIngestion = readings,
        afterEachIngestion = List(ingestionElements.size) { emptyList() },
    )
  }
  val firstTime = ingestionElements.minOf { it.ingestionWithCompanionAndCustomUnit.ingestion.time }
  val before = readings.filter { it.time < firstTime }
  val afterByIndex = ingestionElements.mapIndexed { index, element ->
    val start = element.ingestionWithCompanionAndCustomUnit.ingestion.time
    val next =
        ingestionElements.getOrNull(index + 1)?.ingestionWithCompanionAndCustomUnit?.ingestion?.time
    readings.filter { it.time >= start && (next == null || it.time < next) }
  }
  return PartitionedBloodPressureReadings(before, afterByIndex)
}

@Composable
private fun NowRelativeToStartTimeText(startTime: Instant) {
  val now: MutableState<Instant> = remember { mutableStateOf(Instant.now()) }
  LaunchedEffect(key1 = "updateTime") {
    while (true) {
      delay(10000L) // update every 10 seconds
      now.value = Instant.now()
    }
  }
  val isStartInPast = startTime < now.value
  val relativeTime =
      if (isStartInPast) {
        "Now " +
            getDurationText(
                fromInstant = startTime,
                toInstant = now.value,
            ) +
            " in (since start)"
      } else {
        "Start is in " +
            getDurationText(
                fromInstant = startTime,
                toInstant = now.value,
            )
      }
  Text(
      text = relativeTime,
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(vertical = 5.dp, horizontal = horizontalPadding),
  )
}
