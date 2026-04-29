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

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestionsTimedNotesAndRatings
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.ExperienceRoute
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.hourLimitToSeparateIngestions
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
import foo.pilz.freaklog.ui.tabs.journal.experience.components.SavedTimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.getStrengthRelativeToCommonDose
import foo.pilz.freaklog.ui.tabs.journal.experience.models.ConsumerWithIngestions
import foo.pilz.freaklog.ui.tabs.journal.experience.models.CumulativeDose
import foo.pilz.freaklog.ui.tabs.journal.experience.models.CumulativeRouteAndDose
import foo.pilz.freaklog.ui.tabs.journal.experience.models.IngestionElement
import foo.pilz.freaklog.ui.tabs.journal.experience.models.InteractionExplanation
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.DataForOneRating
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.DataForOneTimedNote
import foo.pilz.freaklog.ui.tabs.settings.combinations.CombinationSettingsStorage
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.tabs.settings.funny.AchievementContext
import foo.pilz.freaklog.ui.tabs.settings.funny.AchievementDef
import foo.pilz.freaklog.ui.tabs.settings.funny.evaluateAchievement
import foo.pilz.freaklog.ui.tabs.settings.funny.loadAchievements
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class ExperienceViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    private val interactionChecker: InteractionChecker,
    private val userPreferences: UserPreferences,
    combinationSettingsStorage: CombinationSettingsStorage,
    state: SavedStateHandle,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val achievementDefs = loadAchievements(context)

    private val areSubstanceHeightsIndependentFlow =
        userPreferences.areSubstanceHeightsIndependentFlow.stateIn(
            initialValue = false,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    val isTimelineHiddenFlow = userPreferences.isTimelineHiddenFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveTimeDisplayOption(savedTimeDisplayOption: SavedTimeDisplayOption) {
        viewModelScope.launch {
            userPreferences.saveTimeDisplayOption(savedTimeDisplayOption)
        }
    }

    val isOralTimelineDisclaimerHidden = userPreferences.isOralDisclaimerHiddenFlow.stateIn(
        initialValue = true,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val areDosageDotsHiddenFlow = userPreferences.areDosageDotsHiddenFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    fun saveOralDisclaimerIsHidden(isOralDisclaimerHidden: Boolean) {
        viewModelScope.launch {
            userPreferences.saveOralDisclaimerIsHidden(isOralDisclaimerHidden)
        }
    }

    private val experienceId: Int

    private val localIsFavoriteFlow = MutableStateFlow(false)

    val isFavoriteFlow = localIsFavoriteFlow.stateIn(
        initialValue = false,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    init {
        val experienceRoute = state.toRoute<ExperienceRoute>()
        val expId = experienceRoute.experienceId
        experienceId = expId
        viewModelScope.launch {
            val experience = experienceRepo.getExperience(expId)
            val isFavorite = experience?.isFavorite ?: false
            localIsFavoriteFlow.emit(isFavorite)
        }
    }

    fun saveIsFavorite(isFavorite: Boolean) {
        viewModelScope.launch {
            localIsFavoriteFlow.emit(isFavorite)
            val experience = experienceFlow.firstOrNull()
            if (experience != null) {
                experience.isFavorite = isFavorite
                experienceRepo.update(experience)
            }
        }
    }

    val ingestionsWithCompanionsFlow =
        experienceRepo.getIngestionsWithCompanionsFlow(experienceId)
            .stateIn(
                initialValue = emptyList(),
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000)
            )

    val ratingsFlow =
        experienceRepo.getRatingsFlow(experienceId)
            .stateIn(
                initialValue = emptyList(),
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000)
            )

    val timedNotesSortedFlow =
        experienceRepo.getTimedNotesFlowSorted(experienceId)
            .stateIn(
                initialValue = emptyList(),
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000)
            )

    val experienceFlow = experienceRepo.getExperienceFlow(experienceId).stateIn(
        initialValue = null,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    private val currentTimeFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(timeMillis = 1000 * 10)
        }
    }

    val isCurrentExperienceFlow =
        ingestionsWithCompanionsFlow.combine(currentTimeFlow) { ingestionsWithCompanions, currentTime ->
            val ingestionTimes =
                ingestionsWithCompanions.map { it.ingestion.time }
            val lastIngestionTime = ingestionTimes.maxOrNull() ?: return@combine false
            val limitAgo = currentTime.minus(hourLimitToSeparateIngestions, ChronoUnit.HOURS)
            return@combine limitAgo < lastIngestionTime
        }.stateIn(
            initialValue = false,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    val savedTimeDisplayOption = userPreferences.savedTimeDisplayOptionFlow.stateIn(
        initialValue = SavedTimeDisplayOption.REGULAR,
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val timeDisplayOptionFlow =
        userPreferences.savedTimeDisplayOptionFlow.combine(isCurrentExperienceFlow) { savedOption: SavedTimeDisplayOption, isCurrentExperience: Boolean ->
            when (savedOption) {
                SavedTimeDisplayOption.AUTO -> if (isCurrentExperience) TimeDisplayOption.RELATIVE_TO_NOW else TimeDisplayOption.REGULAR
                SavedTimeDisplayOption.RELATIVE_TO_NOW -> TimeDisplayOption.RELATIVE_TO_NOW
                SavedTimeDisplayOption.RELATIVE_TO_START -> TimeDisplayOption.RELATIVE_TO_START
                SavedTimeDisplayOption.TIME_BETWEEN -> TimeDisplayOption.TIME_BETWEEN
                SavedTimeDisplayOption.REGULAR -> TimeDisplayOption.REGULAR
            }
        }.stateIn(
            initialValue = TimeDisplayOption.REGULAR,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    private val sortedIngestionsWithCompanionsFlow =
        ingestionsWithCompanionsFlow.map { ingestionsWithCompanions ->
            ingestionsWithCompanions.sortedBy { it.ingestion.time }
        }

    private val ingestionsWithAssociatedDataFlow: Flow<List<IngestionWithAssociatedData>> =
        sortedIngestionsWithCompanionsFlow.map { ingestionsWithComps ->
            ingestionsWithComps.map { oneIngestionWithComp ->
                val ingestion = oneIngestionWithComp.ingestion
                val roa = substanceRepo.getSubstance(oneIngestionWithComp.ingestion.substanceName)
                    ?.getRoa(ingestion.administrationRoute)
                val roaDuration = roa?.roaDuration
                IngestionWithAssociatedData(
                    ingestionWithCompanionAndCustomUnit = oneIngestionWithComp,
                    roaDuration = roaDuration,
                    roaDose = roa?.roaDose
                )
            }
        }

    private val myIngestionsWithAssociatedDataFlow =
        ingestionsWithAssociatedDataFlow.map { ingestions ->
            ingestions.filter { it.ingestionWithCompanionAndCustomUnit.ingestion.consumerName == null }
        }

    val consumersWithIngestionsFlow = combine(
        ingestionsWithAssociatedDataFlow,
        isTimelineHiddenFlow,
        areSubstanceHeightsIndependentFlow
    ) { ingestions, isTimelineHidden, areSubstanceHeightsIndependent ->
        val otherIngestions =
            ingestions.filter { it.ingestionWithCompanionAndCustomUnit.ingestion.consumerName != null }
        val groupedByConsumer =
            otherIngestions.groupBy { it.ingestionWithCompanionAndCustomUnit.ingestion.consumerName }
        return@combine groupedByConsumer.mapNotNull { entry ->
            val consumerName = entry.key ?: return@mapNotNull null
            val sortedIngestionsWith =
                entry.value.sortedBy { it.ingestionWithCompanionAndCustomUnit.ingestion.time }
            val ingestionElements =
                getIngestionElements(sortedIngestionsWith = sortedIngestionsWith)
            val substances =
                ingestionElements.mapNotNull { substanceRepo.getSubstance(it.ingestionWithCompanionAndCustomUnit.ingestion.substanceName) }
            val dataForEffectLines = getDataForEffectTimelines(ingestionElements, substances)
            val timelineDisplayOption = if (isTimelineHidden) {
                TimelineDisplayOption.Hidden
            } else {
                val isWorthDrawing =
                    ingestionElements.isNotEmpty() && !(ingestionElements.all { it.roaDuration == null })
                if (isWorthDrawing) {
                    val model = AllTimelinesModel(
                        dataForLines = dataForEffectLines,
                        dataForRatings = emptyList(),
                        timedNotes = emptyList(),
                        areSubstanceHeightsIndependent = areSubstanceHeightsIndependent
                    )
                    TimelineDisplayOption.Shown(model)
                } else {
                    TimelineDisplayOption.NotWorthDrawing
                }
            }
            return@mapNotNull ConsumerWithIngestions(
                consumerName = consumerName,
                ingestionElements = ingestionElements,
                dataForEffectLines = dataForEffectLines,
                timelineDisplayOption = timelineDisplayOption
            )
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val ingestionElementsFlow = myIngestionsWithAssociatedDataFlow.map {
        getIngestionElements(it)
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val dataForEffectTimelinesFlow = ingestionElementsFlow.map { ingestionElements ->
        val substances =
            ingestionElements.mapNotNull { substanceRepo.getSubstance(it.ingestionWithCompanionAndCustomUnit.ingestion.substanceName) }
        getDataForEffectTimelines(ingestionElements = ingestionElements, substances = substances)
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val cumulativeDosesFlow = myIngestionsWithAssociatedDataFlow.map {
        getCumulativeDoses(it)
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    private fun getIngestionElements(sortedIngestionsWith: List<IngestionWithAssociatedData>): List<IngestionElement> {
        return sortedIngestionsWith.map { ingestionWith ->
            val numDots =
                if (ingestionWith.ingestionWithCompanionAndCustomUnit.customUnit != null) {
                    ingestionWith.roaDose?.getNumDots(
                        ingestionDose = ingestionWith.ingestionWithCompanionAndCustomUnit.customUnitDose?.calculatedDose,
                        ingestionUnits = ingestionWith.ingestionWithCompanionAndCustomUnit.customUnit?.originalUnit
                    )
                } else {
                    ingestionWith.roaDose?.getNumDots(
                        ingestionWith.ingestionWithCompanionAndCustomUnit.ingestion.dose,
                        ingestionUnits = ingestionWith.ingestionWithCompanionAndCustomUnit.ingestion.units
                    )
                }
            IngestionElement(
                ingestionWithCompanionAndCustomUnit = ingestionWith.ingestionWithCompanionAndCustomUnit,
                roaDuration = ingestionWith.roaDuration,
                numDots = numDots
            )
        }
    }

    val interactionsFlow =
        sortedIngestionsWithCompanionsFlow.combine(combinationSettingsStorage.enabledInteractionsFlow) { ingestions, enabledInteractions ->
            val interactionsToCheck =
                ingestions.filter { it.ingestion.consumerName == null }.map { it.ingestion.substanceName }.plus(enabledInteractions).distinct()
            return@combine interactionsToCheck.flatMapIndexed { index: Int, interaction: String ->
                return@flatMapIndexed interactionsToCheck.drop(index + 1).mapNotNull { other ->
                    interactionChecker.getInteractionBetween(
                        interaction,
                        other
                    )
                }
            }.sortedByDescending { it.interactionType.dangerCount }
        }
            .flowOn(Dispatchers.IO) // if this wasn't on the background the navigation from journal screen to this screen would jump
            .stateIn(
                initialValue = emptyList(),
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000)
            )

    val interactionExplanationsFlow = interactionsFlow.map { interactions ->
        interactions.flatMap {
            listOf(it.aName, it.bName)
        }.distinct().mapNotNull {
            val substance = substanceRepo.getSubstance(substanceName = it) ?: return@mapNotNull null
            return@mapNotNull InteractionExplanation(
                name = substance.name,
                url = substance.interactionExplanationURL
            )
        }
    }.stateIn(
        initialValue = emptyList(),
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000)
    )

    val matchedAchievementsFlow: kotlinx.coroutines.flow.StateFlow<List<AchievementDef>> = combine(
        ingestionsWithCompanionsFlow,
        ratingsFlow,
        timedNotesSortedFlow,
        experienceFlow
    ) { ingestionWithComps, ratings, timedNotes, experience ->
        if (experience == null) return@combine emptyList()
        val ingestions = ingestionWithComps.map { it.ingestion }
        val expWithData = ExperienceWithIngestionsTimedNotesAndRatings(
            experience = experience,
            ingestions = ingestions,
            timedNotes = timedNotes,
            ratings = ratings
        )
        val ctx = AchievementContext(
            experiences = listOf(expWithData),
            allIngestions = ingestions,
            allTimedNotes = timedNotes,
            allRatings = ratings,
            customUnits = emptyList(),
            customRecipes = emptyList(),
            substanceRepo = substanceRepo,
            interactionChecker = interactionChecker
        )
        achievementDefs.filter { evaluateAchievement(it.condition, ctx) }
    }.flowOn(Dispatchers.Default)
        .stateIn(
            initialValue = emptyList(),
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    fun deleteExperience() {
        viewModelScope.launch {
            experienceRepo.deleteEverythingOfExperience(experienceId = experienceId)
        }
    }

    fun saveLastIngestionTimeOfExperience() = viewModelScope.launch {
        val lastIngestionTime =
            ingestionElementsFlow.value.maxOfOrNull { it.ingestionWithCompanionAndCustomUnit.ingestion.time }
        if (lastIngestionTime != null) {
            userPreferences.saveLastIngestionTimeOfExperience(lastIngestionTime)
            userPreferences.saveClonedIngestionTime(null)
        }
    }

    val timelineDisplayOptionFlow = combine(
        dataForEffectTimelinesFlow,
        isTimelineHiddenFlow,
        ratingsFlow,
        timedNotesSortedFlow,
        ingestionElementsFlow,
        areSubstanceHeightsIndependentFlow
    ) { dataForEffectLines, isTimelineHidden, ratings, timedNotesSorted, ingestionElements, areSubstanceHeightsIndependent ->
        if (isTimelineHidden) {
            return@combine TimelineDisplayOption.Hidden
        } else if (dataForEffectLines.isEmpty()) {
            return@combine TimelineDisplayOption.NotWorthDrawing
        } else {
            val dataForRatings = ratings.mapNotNull {
                val ratingTime = it.time
                return@mapNotNull if (ratingTime == null) {
                    null
                } else {
                    DataForOneRating(
                        time = ratingTime,
                        option = it.option
                    )
                }
            }
            val dataForTimedNotes =
                timedNotesSorted.filter { it.isPartOfTimeline }
                    .map {
                        DataForOneTimedNote(time = it.time, color = it.color)
                    }
            val isWorthDrawing =
                ingestionElements.isNotEmpty() && !(ingestionElements.all { it.roaDuration == null } && dataForRatings.isEmpty() && dataForTimedNotes.isEmpty())
            if (isWorthDrawing) {
                val model = AllTimelinesModel(
                    dataForLines = dataForEffectLines,
                    dataForRatings = dataForRatings,
                    timedNotes = dataForTimedNotes,
                    areSubstanceHeightsIndependent = areSubstanceHeightsIndependent
                )
                return@combine TimelineDisplayOption.Shown(model)
            } else {
                return@combine TimelineDisplayOption.NotWorthDrawing
            }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(
            initialValue = TimelineDisplayOption.Loading,
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000)
        )

    companion object {

        fun getDataForEffectTimelines(
            ingestionElements: List<IngestionElement>,
            substances: List<Substance>
        ) =
            ingestionElements.map { oneElement ->
                val horizontalWeight = if (oneElement.numDots == null) {
                    0.5f
                } else if (oneElement.numDots > 4) {
                    1f
                } else {
                    oneElement.numDots.toFloat() / 4f
                }
                val ingestion = oneElement.ingestionWithCompanionAndCustomUnit.ingestion
                DataForOneEffectLine(
                    substanceName = ingestion.substanceName,
                    route = ingestion.administrationRoute,
                    roaDuration = oneElement.roaDuration,
                    height = getStrengthRelativeToCommonDose(
                        ingestion = oneElement.ingestionWithCompanionAndCustomUnit,
                        allIngestions = ingestionElements.map { it.ingestionWithCompanionAndCustomUnit },
                        roaDose = substances.firstOrNull { it.name == ingestion.substanceName }
                            ?.getRoa(ingestion.administrationRoute)?.roaDose
                    ).toFloat(),
                    horizontalWeight = horizontalWeight,
                    color = oneElement.ingestionWithCompanionAndCustomUnit.substanceCompanion?.color
                        ?: AdaptiveColor.RED,
                    startTime = ingestion.time,
                    endTime = ingestion.endTime
                )
            }

        fun getCumulativeDoses(ingestions: List<IngestionWithAssociatedData>): List<CumulativeDose> {
            return ingestions.groupBy { it.ingestionWithCompanionAndCustomUnit.ingestion.substanceName }
                .map { groupedBySubstanceName ->
                    val ingestionsOfSameSubstance = groupedBySubstanceName.value
                    val cumulativeRouteDose =
                        ingestionsOfSameSubstance.groupBy { it.ingestionWithCompanionAndCustomUnit.ingestion.administrationRoute }
                            .mapNotNull { groupedByRoute ->
                                val groupedIngestions = groupedByRoute.value
                                if (groupedIngestions.any { it.ingestionWithCompanionAndCustomUnit.pureDose == null }) return@mapNotNull null
                                val firstIngestion =
                                    groupedIngestions.first().ingestionWithCompanionAndCustomUnit
                                val units = firstIngestion.originalUnit ?: return@mapNotNull null
                                if (groupedIngestions.any { it.ingestionWithCompanionAndCustomUnit.originalUnit != units }) return@mapNotNull null
                                val isEstimate =
                                    groupedIngestions.any { it.ingestionWithCompanionAndCustomUnit.ingestion.isDoseAnEstimate || it.ingestionWithCompanionAndCustomUnit.customUnit?.isEstimate ?: false }
                                val cumulativeDose =
                                    groupedIngestions.mapNotNull { it.ingestionWithCompanionAndCustomUnit.pureDose }
                                        .sum()
                                val cumulativeDoseStandardDeviation =
                                    groupedIngestions.mapNotNull { it.ingestionWithCompanionAndCustomUnit.pureDoseStandardDeviation }
                                        .sum()
                                val numDots = groupedIngestions.first().roaDose?.getNumDots(
                                    ingestionDose = cumulativeDose,
                                    ingestionUnits = units
                                )
                                CumulativeRouteAndDose(
                                    cumulativeDose = cumulativeDose,
                                    units = units,
                                    isEstimate = isEstimate,
                                    cumulativeDoseStandardDeviation = if (cumulativeDoseStandardDeviation > 0) cumulativeDoseStandardDeviation else null,
                                    numDots = numDots,
                                    route = firstIngestion.ingestion.administrationRoute,
                                    hasMoreThanOneIngestion = groupedIngestions.size > 1
                                )
                            }
                    return@map CumulativeDose(
                        substanceName = groupedBySubstanceName.key,
                        cumulativeRouteAndDose = cumulativeRouteDose
                    )
                }
                .filter { cumulativeDose ->
                    cumulativeDose.cumulativeRouteAndDose.isNotEmpty() && cumulativeDose.cumulativeRouteAndDose.any { it.hasMoreThanOneIngestion }
                }
        }
    }
}

inline fun <T1, T2, T3, T4, T5, T6, R> combine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    flow5: Flow<T5>,
    flow6: Flow<T6>,
    crossinline transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): Flow<R> {
    return combine(
        flow,
        flow2,
        flow3,
        flow4,
        flow5,
        flow6
    ) { args: Array<*> ->
        @Suppress("UNCHECKED_CAST")
        transform(
            args[0] as T1,
            args[1] as T2,
            args[2] as T3,
            args[3] as T4,
            args[4] as T5,
            args[5] as T6,
        )
    }
}

sealed class TimelineDisplayOption {
    data object Loading : TimelineDisplayOption()
    data object Hidden : TimelineDisplayOption()
    data object NotWorthDrawing : TimelineDisplayOption()
    data class Shown(val allTimelinesModel: AllTimelinesModel) : TimelineDisplayOption()
}

data class IngestionWithAssociatedData(
    val ingestionWithCompanionAndCustomUnit: IngestionWithCompanionAndCustomUnit,
    val roaDuration: RoaDuration?,
    val roaDose: RoaDose?
)