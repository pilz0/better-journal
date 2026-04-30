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

package foo.pilz.freaklog.ui.tabs.settings

import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.export.*
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAuthManager
import foo.pilz.freaklog.ui.tabs.settings.lock.BiometricAvailability
import foo.pilz.freaklog.ui.tabs.settings.lock.LockTimeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val experienceRepository: ExperienceRepository,
    private val fileSystemConnection: FileSystemConnection,
    private val userPreferences: UserPreferences,
    private val biometricAuthManager: BiometricAuthManager,
) : ViewModel() {

    fun saveDosageDotsAreHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveDosageDotsAreHidden(value)
    }

    fun saveAreSubstanceHeightsIndependent(value: Boolean) = viewModelScope.launch {
        userPreferences.saveAreSubstanceHeightsIndependent(value)
    }

    fun saveIsTimelineHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsTimelineHidden(value)
    }

    val isTimelineHiddenFlow = userPreferences.isTimelineHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveIsStatsHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsStatsHidden(value)
    }

    val isStatsHiddenFlow = userPreferences.isStatsHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveIsDrugsHidden(value: Boolean) = viewModelScope.launch {
        userPreferences.saveIsDrugsHidden(value)
    }

    val isDrugsHiddenFlow = userPreferences.isDrugsHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val areSubstanceHeightsIndependentFlow =
        userPreferences.areSubstanceHeightsIndependentFlow.stateIn(
            initialValue = false,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
    )

    val areDosageDotsHiddenFlow = userPreferences.areDosageDotsHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )


    fun saveActivateSafer(value: Boolean) = viewModelScope.launch {
        userPreferences.saveActivateSafer(value)
    }

    val activateSaferFlow = userPreferences.activateSaferFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val isInventoryEnabledFlow = userPreferences.isInventoryEnabledFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveIsInventoryEnabled(value: Boolean) = viewModelScope.launch {
        userPreferences.saveInventoryEnabled(value)
    }

    val isRedoseShownFlow = userPreferences.isRedoseShownFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveRedoseShown(value: Boolean) = viewModelScope.launch {
        userPreferences.saveRedoseShown(value)
    }

    val redoseOnsetFractionFlow = userPreferences.redoseOnsetFractionFlow.stateIn(
        initialValue = 1.0f,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val redoseComeupFractionFlow = userPreferences.redoseComeupFractionFlow.stateIn(
        initialValue = 1.0f,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val redosePeakFractionFlow = userPreferences.redosePeakFractionFlow.stateIn(
        initialValue = 0.5f,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveRedoseFractions(onset: Float, comeup: Float, peak: Float) = viewModelScope.launch {
        userPreferences.saveRedoseFractions(onset, comeup, peak)
    }

    fun saveHapticFeedbackEnabled(value: Boolean) = viewModelScope.launch {
        userPreferences.saveHapticFeedbackEnabled(value)
    }

    val isHapticFeedbackEnabledFlow = userPreferences.isHapticFeedbackEnabledFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val aiApiKeyFlow = userPreferences.aiApiKeyFlow.stateIn(
        initialValue = "",
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveAiApiKey(value: String) = viewModelScope.launch {
        userPreferences.saveAiApiKey(value)
    }

    val aiModelNameFlow = userPreferences.aiModelNameFlow.stateIn(
        initialValue = "gemini-1.5-flash",
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveAiModelName(value: String) = viewModelScope.launch {
        userPreferences.saveAiModelName(value)
    }

    // ---- App lock ----

    val isLockEnabledFlow = userPreferences.isLockEnabledFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
    )

    val lockTimeOptionFlow = userPreferences.lockTimeOptionFlow.stateIn(
        initialValue = LockTimeOption.DEFAULT,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
    )

    fun biometricAvailability(): BiometricAvailability = biometricAuthManager.availability()

    fun saveLockEnabled(value: Boolean) {
        biometricAuthManager.setLockEnabled(value)
    }

    fun saveLockTimeOption(value: LockTimeOption) {
        biometricAuthManager.setLockTimeOption(value)
    }

    val snackbarHostState = SnackbarHostState()

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val text = fileSystemConnection.getTextFromUri(uri)
            if (text == null) {
                snackbarHostState.showSnackbar(
                    message = "File not found",
                    duration = SnackbarDuration.Short
                )
            } else {
                try {
                    val json = Json { ignoreUnknownKeys = true }
                    val journalExport = json.decodeFromString<JournalExport>(text)
                    experienceRepository.deleteEverything()
                    experienceRepository.insertEverything(journalExport)
                    snackbarHostState.showSnackbar(
                        message = "Import successful",
                        duration = SnackbarDuration.Short
                    )
                } catch (e: Exception) {
                    println("Error when decoding: ${e.message}")
                    snackbarHostState.showSnackbar(
                        message = "Decoding file failed",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    fun exportFile(uri: Uri) {
        viewModelScope.launch {
            val experiencesWithIngestionsAndRatings =
                experienceRepository.getAllExperiencesWithIngestionsTimedNotesAndRatingsSorted()
            val experiencesSerializable = experiencesWithIngestionsAndRatings.map {
                val location = it.experience.location
                return@map ExperienceSerializable(
                    title = it.experience.title,
                    text = it.experience.text,
                    creationDate = it.experience.creationDate,
                    sortDate = it.experience.sortDate,
                    isFavorite = it.experience.isFavorite,
                    ingestions = it.ingestions.map { ingestion ->
                        IngestionSerializable(
                            substanceName = ingestion.substanceName,
                            time = ingestion.time,
                            endTime = ingestion.endTime,
                            creationDate = ingestion.creationDate,
                            administrationRoute = ingestion.administrationRoute,
                            dose = ingestion.dose,
                            estimatedDoseStandardDeviation = ingestion.estimatedDoseStandardDeviation,
                            isDoseAnEstimate = ingestion.isDoseAnEstimate,
                            units = ingestion.units,
                            notes = ingestion.notes,
                            stomachFullness = ingestion.stomachFullness,
                            consumerName = ingestion.consumerName,
                            customUnitId = ingestion.customUnitId,
                            administrationSite = ingestion.administrationSite
                        )
                    },
                    location = if (location != null) {
                        LocationSerializable(
                            name = location.name,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )
                    } else {
                        null
                    },
                    ratings = it.ratings.map { rating ->
                        RatingSerializable(
                            option = rating.option,
                            time = rating.time,
                            creationDate = rating.creationDate
                        )
                    },
                    timedNotes = it.timedNotes.map { timedNote ->
                        TimedNoteSerializable(
                            creationDate = timedNote.creationDate,
                            time = timedNote.time,
                            note = timedNote.note,
                            color = timedNote.color,
                            isPartOfTimeline = timedNote.isPartOfTimeline
                        )
                    }
                )
            }
            val customUnitsSerializable = experienceRepository.getAllCustomUnitsSorted().map {
                CustomUnitSerializable(
                    id = it.id,
                    substanceName = it.substanceName,
                    name = it.name,
                    creationDate = it.creationDate,
                    administrationRoute = it.administrationRoute,
                    dose = it.dose,
                    estimatedDoseStandardDeviation = it.estimatedDoseStandardDeviation,
                    isEstimate = it.isEstimate,
                    isArchived = it.isArchived,
                    unit = it.unit,
                    unitPlural = it.unitPlural,
                    originalUnit = it.originalUnit,
                    note = it.note
                )
            }
            val journalExport = JournalExport(
                experiences = experiencesSerializable,
                substanceCompanions = experienceRepository.getAllSubstanceCompanions(),
                customSubstances = experienceRepository.getAllCustomSubstances(),
                customUnits = customUnitsSerializable,
                reminders = experienceRepository.getAllReminders()
            )
            try {
                val jsonList = Json.encodeToString(journalExport)
                fileSystemConnection.saveTextInUri(uri, text = jsonList)
                snackbarHostState.showSnackbar(
                    message = "Export successful",
                    duration = SnackbarDuration.Short
                )
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(
                    message = "Export failed",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    fun deleteEverything() {
        viewModelScope.launch {
            experienceRepository.deleteEverything()
        }
    }
}