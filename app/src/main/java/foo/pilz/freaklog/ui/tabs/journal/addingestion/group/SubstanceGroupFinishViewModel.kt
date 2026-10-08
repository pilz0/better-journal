package foo.pilz.freaklog.ui.tabs.journal.addingestion.group

import foo.pilz.freaklog.data.room.experiences.entities.AdaptiveColor
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.SubstanceGroupRepository
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Ingestion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceCompanion
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.data.room.experiences.relations.ExperienceWithIngestions
import foo.pilz.freaklog.data.room.experiences.relations.SubstanceGroupWithItems
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.data.substances.repositories.DefaultSubstanceColors
import foo.pilz.freaklog.ui.main.navigation.graphs.SubstanceGroupFinishRoute
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.IngestionTimePickerOption
import foo.pilz.freaklog.ui.tabs.journal.experience.notification.TimelineNotificationService
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.utils.getLocalDateTime
import foo.pilz.freaklog.ui.utils.getStringOfPattern
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class SubstanceGroupFinishViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val groupRepo: SubstanceGroupRepository,
    private val experienceRepo: ExperienceRepository,
    private val userPreferences: UserPreferences,
    private val defaultSubstanceColors: DefaultSubstanceColors,
    state: SavedStateHandle,
) : ViewModel() {

    private val route = state.toRoute<SubstanceGroupFinishRoute>()
    private val groupId: Int = route.groupId

    private val _group = MutableStateFlow<SubstanceGroupWithItems?>(null)
    val group = _group.asStateFlow()

    val localDateTimeStartFlow = MutableStateFlow(LocalDateTime.now())
    val localDateTimeEndFlow = MutableStateFlow(LocalDateTime.now().plusMinutes(30))
    val ingestionTimePickerOptionFlow = MutableStateFlow(IngestionTimePickerOption.POINT_IN_TIME)
    val experiencesInRangeFlow = MutableStateFlow<List<ExperienceWithIngestions>>(emptyList())
    val selectedExperienceFlow = MutableStateFlow<ExperienceWithIngestions?>(null)

    val selectedItemIds = mutableStateMapOf<Int, Boolean>()

    var enteredTitle by mutableStateOf(LocalDateTime.now().getStringOfPattern("dd MMMM yyyy"))
        private set
    val isEnteredTitleOk: Boolean get() = enteredTitle.isNotEmpty()
    private var hasTitleBeenChanged = false

    var consumerName by mutableStateOf("")
        private set
    var ingestionCategory by mutableStateOf<IngestionCategory?>(null)
        private set
    var inheritedIngestionCategory by mutableStateOf(IngestionCategory.DEFAULT_INGESTION_CATEGORY)
        private set
    var note by mutableStateOf("")
    var addingWithoutExperience by mutableStateOf(false)
    // This app has no ingestions without an experience, so the upstream toggle stays off.
    val areStandaloneIngestionsEnabledFlow = MutableStateFlow(false).asStateFlow()

    val sortedConsumerNamesFlow =
        experienceRepo.getSortedIngestions(limit = 200).map { ingestions ->
            ingestions.mapNotNull { it.consumerName }.distinct()
        }.stateInVm(viewModelScope, emptyList())

    private val _previousNotesFlow = MutableStateFlow<List<String>>(emptyList())
    val previousNotesFlow = _previousNotesFlow.asStateFlow()

    private var loaded = false
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            val loadedGroup = groupRepo.getWithItems(groupId)
            if (loadedGroup != null) {
                _group.value = loadedGroup
                loadedGroup.sortedItems.forEach { item -> selectedItemIds[item.id] = true }
                seedTimeFromLastIngestion()
                refreshExperiencesInRange()
                refreshPreviousNotes(loadedGroup)
                inheritedIngestionCategory = IngestionCategory.DEFAULT_INGESTION_CATEGORY
            }
            loaded = true
            _isLoaded.value = true
        }
    }

    private suspend fun seedTimeFromLastIngestion() {
        val lastIngestionTime = userPreferences.lastIngestionTimeOfExperienceFlow.first()
        val clonedTime = userPreferences.clonedIngestionTimeFlow.first()
        val seed = clonedTime
            ?: lastIngestionTime?.takeIf {
                it < Instant.now().minus(20, ChronoUnit.HOURS)
            }
        if (seed != null) {
            localDateTimeStartFlow.emit(seed.getLocalDateTime())
            localDateTimeEndFlow.emit(seed.plus(30, ChronoUnit.MINUTES).getLocalDateTime())
            if (!hasTitleBeenChanged) updateTitleBasedOnTime(seed)
        }
    }

    private suspend fun refreshExperiencesInRange() {
        val selectedInstant = localDateTimeStartFlow.value
            .atZone(ZoneId.systemDefault()).toInstant()
        val fromInstant = selectedInstant.minus(3, ChronoUnit.DAYS)
        val toInstant = selectedInstant.plus(1, ChronoUnit.DAYS)
        val experiencesInRange = experienceRepo
            .getSortedExperiencesWithIngestionsWithSortDateBetween(fromInstant, toInstant)
        experiencesInRangeFlow.emit(experiencesInRange)
        val closest = experiencesInRange.firstOrNull { experience ->
            val sortedIngestions = experience.ingestions.sortedBy { it.time }
            val firstIngestionTime =
                sortedIngestions.firstOrNull()?.time ?: return@firstOrNull false
            val upperBoundBasedOnFirstIngestion = firstIngestionTime.plus(15, ChronoUnit.HOURS)
            val lastIngestionTime = sortedIngestions.lastOrNull()?.time ?: return@firstOrNull false
            val upperBoundBasedOnLastIngestion = lastIngestionTime.plus(3, ChronoUnit.HOURS)
            val finalUpperBound =
                maxOf(upperBoundBasedOnFirstIngestion, upperBoundBasedOnLastIngestion)
            val lowerBound = firstIngestionTime.minus(3, ChronoUnit.HOURS)
            selectedInstant in lowerBound..finalUpperBound
        }
        selectedExperienceFlow.emit(closest)
    }

    private suspend fun refreshPreviousNotes(loadedGroup: SubstanceGroupWithItems) {
        val names = loadedGroup.items.map { it.substanceName }.distinct()
        if (names.isEmpty()) {
            _previousNotesFlow.value = emptyList()
            return
        }
        val combined = mutableListOf<String>()
        for (name in names) {
            val notes =
                experienceRepo.getSortedIngestionsFlow(name, limit = 10).firstOrNull().orEmpty()
                    .mapNotNull { it.notes }
                    .filter { it.isNotBlank() }
            combined.addAll(notes)
        }
        _previousNotesFlow.value = combined.distinct().take(10)
    }

    fun onChangeTimePickerOption(option: IngestionTimePickerOption) {
        viewModelScope.launch { ingestionTimePickerOptionFlow.emit(option) }
    }

    fun onChangeStartDateOrTime(newLocalDateTime: LocalDateTime) {
        viewModelScope.launch {
            localDateTimeStartFlow.emit(newLocalDateTime)
            val startTime = newLocalDateTime.atZone(ZoneId.systemDefault()).toInstant()
            if (!hasTitleBeenChanged) updateTitleBasedOnTime(startTime)
            val endTime = localDateTimeEndFlow.first().atZone(ZoneId.systemDefault()).toInstant()
            if (startTime > endTime || Duration.between(startTime, endTime).toHours() > 24) {
                localDateTimeEndFlow.emit(startTime.plus(30, ChronoUnit.MINUTES).getLocalDateTime())
            }
            refreshExperiencesInRange()
        }
    }

    fun onChangeEndDateOrTime(newLocalDateTime: LocalDateTime) {
        viewModelScope.launch {
            localDateTimeEndFlow.emit(newLocalDateTime)
            val endTime = newLocalDateTime.atZone(ZoneId.systemDefault()).toInstant()
            val startTime =
                localDateTimeStartFlow.first().atZone(ZoneId.systemDefault()).toInstant()
            if (startTime > endTime) {
                localDateTimeStartFlow.emit(
                    endTime.minus(30, ChronoUnit.MINUTES).getLocalDateTime()
                )
            }
        }
    }

    fun onChangeOfSelectedExperience(experienceWithIngestions: ExperienceWithIngestions?) {
        viewModelScope.launch {
            selectedExperienceFlow.emit(experienceWithIngestions)
        }
    }

    fun changeTitle(newTitle: String) {
        enteredTitle = newTitle
        hasTitleBeenChanged = true
    }

    fun changeConsumerName(newName: String) {
        consumerName = newName
    }

    fun onIngestionCategoryChange(newCategory: IngestionCategory?) {
        ingestionCategory = newCategory
    }

    fun toggleItem(item: SubstanceGroupItem) {
        selectedItemIds[item.id] = !(selectedItemIds[item.id] ?: true)
    }

    private fun updateTitleBasedOnTime(time: Instant) {
        enteredTitle = time.getStringOfPattern("dd MMMM yyyy")
    }

    fun confirmAndDismiss(dismiss: () -> Unit) {
        val loadedGroup = _group.value ?: return
        val checkedItems = loadedGroup.sortedItems.filter { selectedItemIds[it.id] ?: true }
        if (checkedItems.isEmpty()) return
        viewModelScope.launch {
            val startTime = localDateTimeStartFlow.value.atZone(ZoneId.systemDefault()).toInstant()
            val endTime =
                if (ingestionTimePickerOptionFlow.value == IngestionTimePickerOption.TIME_RANGE) {
                    localDateTimeEndFlow.value.atZone(ZoneId.systemDefault()).toInstant()
                } else null
            val savedExperienceId = insertGroupIngestions(checkedItems, startTime, endTime)
            TimelineNotificationService.refreshOrAutoStart(appContext, userPreferences, savedExperienceId)
            withContext(Dispatchers.Main) { dismiss() }
        }
    }

    private suspend fun insertGroupIngestions(
        items: List<SubstanceGroupItem>,
        startTime: Instant,
        endTime: Instant?,
    ): Int {
        val existingId = selectedExperienceFlow.value?.experience?.id
        val targetId = existingId ?: createNewExperience(startTime, items.first(), endTime)
        val itemsToInsert = if (existingId == null) items.drop(1) else items
        for (item in itemsToInsert) {
            insertIngestion(item, targetId, startTime, endTime)
        }
        return targetId
    }

    private suspend fun createNewExperience(
        startTime: Instant,
        firstItem: SubstanceGroupItem,
        endTime: Instant?,
    ): Int {
        val newId = (experienceRepo.getMaxExperienceIdFlow().firstOrNull() ?: 0) + 1
        val newExperience = Experience(
            id = newId,
            title = enteredTitle,
            text = "",
            creationDate = Instant.now(),
            sortDate = startTime,
            location = null,
        )
        val ingestion = buildIngestion(firstItem, newId, startTime, endTime)
        val companion = companionFor(firstItem.substanceName)
        experienceRepo.insertIngestionExperienceAndCompanion(ingestion, newExperience, companion)
        return newId
    }

    private suspend fun insertIngestion(
        item: SubstanceGroupItem,
        experienceId: Int,
        startTime: Instant,
        endTime: Instant?,
    ) {
        val ingestion = buildIngestion(item, experienceId, startTime, endTime)
        val companion = companionFor(item.substanceName)
        experienceRepo.insertIngestionAndCompanion(ingestion, companion)
    }

    private suspend fun companionFor(substanceName: String): SubstanceCompanion =
        experienceRepo.getSubstanceCompanion(substanceName)
            ?: SubstanceCompanion(
                substanceName = substanceName,
                color = AdaptiveColor.Custom(defaultSubstanceColors.colorOrRandom(substanceName)),
            )

    private fun buildIngestion(
        item: SubstanceGroupItem,
        experienceId: Int,
        startTime: Instant,
        endTime: Instant?,
    ): Ingestion =
        Ingestion(
            substanceName = item.substanceName,
            time = startTime,
            endTime = endTime,
            administrationRoute = item.administrationRoute,
            dose = item.dose,
            isDoseAnEstimate = item.isEstimate,
            estimatedDoseStandardDeviation = item.estimatedDoseStandardDeviation,
            units = item.units,
            experienceId = experienceId,
            notes = note.ifBlank { null },
            stomachFullness = null,
            consumerName = consumerName.ifBlank { null },
            customUnitId = item.customUnitId,
            category = ingestionCategory,
        )
}
