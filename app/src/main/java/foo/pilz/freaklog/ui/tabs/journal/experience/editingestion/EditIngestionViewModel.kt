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

package foo.pilz.freaklog.ui.tabs.journal.experience.editingestion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.webhooks.IngestionWebhookMessageRepository
import foo.pilz.freaklog.data.room.webhooks.WebhookRepository
import foo.pilz.freaklog.data.room.webhooks.entities.IngestionWebhookMessage
import foo.pilz.freaklog.data.room.webhooks.entities.Webhook
import foo.pilz.freaklog.ui.main.navigation.graphs.EditIngestionRoute
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.IngestionTimePickerOption
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toPreservedString
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.utils.getInstant
import foo.pilz.freaklog.ui.utils.getLocalDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class EditIngestionViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    private val userPreferences: UserPreferences,
    private val webhookService: foo.pilz.freaklog.data.webhook.WebhookService,
    private val webhookRepository: WebhookRepository,
    private val ingestionWebhookMessageRepository: IngestionWebhookMessageRepository,
    @ApplicationScope private val externalScope: CoroutineScope,
    state: SavedStateHandle
) : ViewModel() {
    private var ingestionFlow: MutableStateFlow<Ingestion?> = MutableStateFlow(null)
    var ingestion: Ingestion? = null
    var note by mutableStateOf("")
    var isEstimate by mutableStateOf(false)
    var isKnown by mutableStateOf(true)
    var dose by mutableStateOf("")
    var estimatedDoseStandardDeviation by mutableStateOf("")
    var units by mutableStateOf("")
    var experienceId by mutableIntStateOf(1)
    val ingestionTimePickerOptionFlow = MutableStateFlow(IngestionTimePickerOption.POINT_IN_TIME)
    var localDateTimeStartFlow = MutableStateFlow(LocalDateTime.now())
    var localDateTimeEndFlow = MutableStateFlow(LocalDateTime.now())
    var consumerName by mutableStateOf("")
    var administrationSite by mutableStateOf("")
    var customUnit: CustomUnit? by mutableStateOf(null)
    val otherCustomUnits = experienceRepo.getAllCustomUnitsFlow().combine(ingestionFlow) { customUnits, ing ->
        customUnits.filter {customUnit ->
            customUnit.administrationRoute == ing?.administrationRoute && customUnit.substanceName == ing.substanceName && customUnit.id != ing.customUnitId
        }
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun onDoseChange(newDoseText: String) {
        dose = newDoseText
    }

    fun onChangeEstimatedDoseStandardDeviation(newEstimatedDoseStandardDeviation: String) {
        estimatedDoseStandardDeviation = newEstimatedDoseStandardDeviation
    }

    init {
        val editIngestionRoute = state.toRoute<EditIngestionRoute>()
        viewModelScope.launch {
            val ingestionAndCustomUnit =
                experienceRepo.getIngestionFlow(id = editIngestionRoute.ingestionId).first() ?: return@launch
            val ing = ingestionAndCustomUnit.ingestion
            ingestionFlow.emit(ing)
            ingestion = ing
            note = ing.notes ?: ""
            isEstimate = ing.isDoseAnEstimate
            estimatedDoseStandardDeviation = ing.estimatedDoseStandardDeviation?.toPreservedString() ?: ""
            experienceId = ing.experienceId
            dose = ing.dose?.toPreservedString() ?: ""
            isKnown = ing.dose != null
            units = ing.units ?: ""
            consumerName = ing.consumerName ?: ""
            administrationSite = ing.administrationSite ?: ""
            localDateTimeStartFlow.emit(ing.time.getLocalDateTime())
            val endTime = ing.endTime
            if (endTime != null) {
                ingestionTimePickerOptionFlow.emit(IngestionTimePickerOption.TIME_RANGE)
                localDateTimeEndFlow.emit(endTime.getLocalDateTime())
            } else {
                localDateTimeEndFlow.emit(ing.time.plus(30, ChronoUnit.MINUTES).getLocalDateTime())
            }
            customUnit = ingestionAndCustomUnit.customUnit
        }
    }

    val sortedConsumerNamesFlow =
        experienceRepo.getSortedIngestions(limit = 200).map { ingestions ->
            return@map ingestions.mapNotNull { it.consumerName }.distinct()
        }.stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    fun onChangeCustomUnit(newCustomUnit: CustomUnit?) {
        customUnit = newCustomUnit
        newCustomUnit?.unit?.let {
            units = it
        }
    }

    fun onChangeTimePickerOption(ingestionTimePickerOption: IngestionTimePickerOption) =
        viewModelScope.launch {
            ingestionTimePickerOptionFlow.emit(ingestionTimePickerOption)
        }

    fun onChangeStartTime(newLocalDateTime: LocalDateTime) = viewModelScope.launch {
        localDateTimeStartFlow.emit(newLocalDateTime)
        val startTime = newLocalDateTime.atZone(ZoneId.systemDefault()).toInstant()
        val endTime = localDateTimeEndFlow.first().atZone(ZoneId.systemDefault()).toInstant()
        if (startTime > endTime) {
            val newEndTime = startTime.plus(30, ChronoUnit.MINUTES)
            localDateTimeEndFlow.emit(newEndTime.getLocalDateTime())
        }
    }

    fun onChangeEndTime(newLocalDateTime: LocalDateTime) = viewModelScope.launch {
        localDateTimeEndFlow.emit(newLocalDateTime)
        val endTime = newLocalDateTime.atZone(ZoneId.systemDefault()).toInstant()
        val startTime =
            localDateTimeStartFlow.first().atZone(ZoneId.systemDefault()).toInstant()
        if (startTime > endTime) {
            val newStartTime = endTime.minus(30, ChronoUnit.MINUTES)
            localDateTimeStartFlow.emit(newStartTime.getLocalDateTime())
        }
    }

    fun onChangeConsumerName(newName: String) {
        consumerName = newName
    }

    fun onChangeAdministrationSite(newSite: String) {
        administrationSite = newSite
    }

    // Check if site selection is relevant for the current administration route
    val showSiteSelection: Boolean
        get() = ingestion?.administrationRoute?.showSiteSelection ?: false

    // Get the appropriate site options for the current administration route
    val siteOptions: List<String>
        get() = ingestion?.administrationRoute?.siteOptions ?: emptyList()

    fun toggleIsKnown() {
        isKnown = isKnown.not()
    }

    fun onChangeIsEstimate(newIsEstimate: Boolean) {
        isEstimate = newIsEstimate
    }

    fun saveClonedIngestionTime() = viewModelScope.launch {
        userPreferences.saveClonedIngestionTime(ingestion?.time)
    }

    val relevantExperiences: StateFlow<List<ExperienceOption>> = localDateTimeStartFlow.map {
        val selectedInstant = it.getInstant()
        val fromDate = selectedInstant.minus(2, ChronoUnit.DAYS)
        val toDate = selectedInstant.plus(2, ChronoUnit.DAYS)
        return@map experienceRepo.getIngestionsWithExperiencesFlow(fromDate, toDate).firstOrNull()
            ?: emptyList()
    }.map { list ->
        return@map list.map {
            ExperienceOption(id = it.experience.id, title = it.experience.title)
        }.distinct()
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun onDoneTap() {
        viewModelScope.launch {
            val selectedStartInstant = localDateTimeStartFlow.firstOrNull()?.getInstant() ?: return@launch
            val selectedEndInstant = localDateTimeEndFlow.firstOrNull()?.getInstant()
            val timePickerOption = ingestionTimePickerOptionFlow.first()
            val endTime = if (timePickerOption == IngestionTimePickerOption.TIME_RANGE) selectedEndInstant else null
            ingestion?.let {
                it.notes = note
                it.isDoseAnEstimate = isEstimate
                it.experienceId = experienceId
                it.dose = if (isKnown) dose.toDoubleOrNull() else null
                it.estimatedDoseStandardDeviation = if (isEstimate) estimatedDoseStandardDeviation.toDoubleOrNull() else null
                it.units = units
                it.customUnitId = customUnit?.id
                it.time = selectedStartInstant
                it.endTime = endTime
                it.consumerName = consumerName.ifBlank { null }
                it.administrationSite = administrationSite.ifBlank { null }
                experienceRepo.update(it)
                
                // Send webhook edit notification in background
                externalScope.launch {
                    editWebhookForIngestion(it)
                }
            }
        }
    }

    fun deleteIngestion() {
        viewModelScope.launch {
            ingestion?.let {
                // Delete webhook message if it exists (in background)
                externalScope.launch {
                    deleteWebhookForIngestion(it)
                }
                experienceRepo.delete(ingestion = it)
            }
        }
    }

    private suspend fun editWebhookForIngestion(ingestion: Ingestion) {
        val links = ingestionWebhookMessageRepository.getByIngestion(ingestion.id)
        if (links.isEmpty()) return

        val (displayDose, displayUnits) = try {
            experienceRepo.getWebhookDisplayValues(ingestion)
        } catch (e: Exception) {
            android.util.Log.w(TAG_EDIT, "Failed to compute webhook display values: ${e.message}", e)
            Pair(ingestion.dose, ingestion.units)
        }
        val route = ingestion.administrationRoute.displayText

        for (link in links) {
            val webhook: Webhook = webhookRepository.getById(link.webhookId)
                ?.takeIf { it.isEnabled }
                ?: continue

            val template = webhook.template.ifBlank {
                foo.pilz.freaklog.data.webhook.WebhookService.DEFAULT_TEMPLATE
            }
            val user = webhook.displayName.ifBlank { "User" }
            try {
                val result = webhookService.editWebhook(
                    url = webhook.url,
                    messageId = link.messageId,
                    user = user,
                    substance = ingestion.substanceName,
                    dose = displayDose,
                    units = displayUnits,
                    isEstimate = ingestion.isDoseAnEstimate,
                    route = route,
                    site = ingestion.administrationSite,
                    note = ingestion.notes,
                    template = template,
                    isHyperlinked = webhook.isHyperlinked
                )
                if (!result.success) {
                    android.util.Log.w(
                        TAG_EDIT,
                        "Webhook edit failed for \"${webhook.name}\": " +
                            (result.error?.message ?: "Unknown error")
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG_EDIT, "Webhook edit exception for \"${webhook.name}\": ${e.message}", e)
            }
        }
    }

    private suspend fun deleteWebhookForIngestion(ingestion: Ingestion) {
        val links = ingestionWebhookMessageRepository.getByIngestion(ingestion.id)
        for (link in links) {
            val webhook = webhookRepository.getById(link.webhookId)
            if (webhook == null) {
                // Webhook itself was already deleted; just drop the link row.
                ingestionWebhookMessageRepository.delete(link)
                continue
            }
            try {
                val success = webhookService.deleteWebhookMessage(
                    url = webhook.url,
                    messageId = link.messageId
                )
                if (!success) {
                    android.util.Log.w(
                        TAG_EDIT,
                        "Webhook delete failed for \"${webhook.name}\" message ${link.messageId}"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    TAG_EDIT,
                    "Webhook delete exception for \"${webhook.name}\": ${e.message}",
                    e
                )
            }
            // Drop the local link row regardless of remote success — the
            // ingestion is being deleted and FK CASCADE would do the same
            // when the row is hit. Doing it explicitly keeps the table tidy
            // when callers haven't deleted the ingestion yet.
            ingestionWebhookMessageRepository.delete(link)
        }
    }

    fun resendWebhook() {
        viewModelScope.launch {
            val currentIngestion = ingestion ?: return@launch

            // Launch in external scope to ensure it completes even if screen closes
            externalScope.launch {
                val enabled = webhookRepository.getEnabled()
                if (enabled.isEmpty()) return@launch

                val (displayDose, displayUnits) = try {
                    experienceRepo.getWebhookDisplayValues(currentIngestion)
                } catch (e: Exception) {
                    android.util.Log.w(TAG_EDIT, "Failed to compute webhook display values: ${e.message}", e)
                    Pair(currentIngestion.dose, currentIngestion.units)
                }
                val route = currentIngestion.administrationRoute.displayText

                for (webhook in enabled) {
                    val template = webhook.template.ifBlank {
                        foo.pilz.freaklog.data.webhook.WebhookService.DEFAULT_TEMPLATE
                    }
                    val user = webhook.displayName.ifBlank { "User" }
                    try {
                        val result = webhookService.sendWebhook(
                            url = webhook.url,
                            user = user,
                            substance = currentIngestion.substanceName,
                            dose = displayDose,
                            units = displayUnits,
                            isEstimate = currentIngestion.isDoseAnEstimate,
                            route = route,
                            site = currentIngestion.administrationSite,
                            note = currentIngestion.notes,
                            template = template,
                            isHyperlinked = webhook.isHyperlinked
                        )
                        if (result.success && result.messageId != null) {
                            ingestionWebhookMessageRepository.insert(
                                IngestionWebhookMessage(
                                    ingestionId = currentIngestion.id,
                                    webhookId = webhook.id,
                                    messageId = result.messageId
                                )
                            )
                        } else {
                            android.util.Log.w(
                                TAG_EDIT,
                                "Webhook resend failed for \"${webhook.name}\": " +
                                    (result.error?.message ?: "Unknown error")
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG_EDIT, "Resend exception for \"${webhook.name}\": ${e.message}", e)
                    }
                }
            }
        }
    }
}

private const val TAG_EDIT = "EditIngestionViewModel"

data class ExperienceOption(
    val id: Int,
    val title: String
)