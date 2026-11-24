package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.data.room.experiences.CustomSubstanceRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LocalSubstanceMatch {
  data class Psychonaut(val name: String) : LocalSubstanceMatch

  data class Custom(val id: Int, val name: String) : LocalSubstanceMatch
}

sealed interface AnodyneSubstancePhase {
  data object Loading : AnodyneSubstancePhase

  data class Found(
      val substance: AnodyneSubstance,
      val userNotes: List<AnodyneUserNote> = emptyList(),
      val localMatch: LocalSubstanceMatch? = null,
  ) : AnodyneSubstancePhase

  data object NotFound : AnodyneSubstancePhase

  data class Failed(val error: String) : AnodyneSubstancePhase
}

@HiltViewModel
class AnodyneSubstanceViewModel(
    val substanceTitle: String,
    private val repository: AnodyneRepository,
    private val substanceRepo: SubstanceRepository? = null,
    private val customRepo: CustomSubstanceRepository? = null,
    private val experienceRepo: ExperienceRepository? = null,
) : ViewModel() {

  val title: String
    get() = substanceTitle

  @Inject
  constructor(
      repository: AnodyneRepository,
      substanceRepo: SubstanceRepository,
      customRepo: CustomSubstanceRepository,
      experienceRepo: ExperienceRepository,
      savedStateHandle: SavedStateHandle,
  ) : this(
      substanceTitle = savedStateHandle.get<String>("title") ?: "",
      repository = repository,
      substanceRepo = substanceRepo,
      customRepo = customRepo,
      experienceRepo = experienceRepo,
  )

  private val _phase = MutableStateFlow<AnodyneSubstancePhase>(AnodyneSubstancePhase.Loading)
  val phase: StateFlow<AnodyneSubstancePhase> = _phase.asStateFlow()

  val substance: StateFlow<AnodyneSubstance?> =
      _phase
          .map {
            (it as? AnodyneSubstancePhase.Found)?.substance
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val userNotes: StateFlow<List<AnodyneUserNote>> =
      _phase
          .map {
            (it as? AnodyneSubstancePhase.Found)?.userNotes ?: emptyList()
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val localMatch: StateFlow<LocalSubstanceMatch?> =
      _phase
          .map {
            (it as? AnodyneSubstancePhase.Found)?.localMatch
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  init {
    if (substanceTitle.isNotBlank()) {
      load(substanceTitle)
    } else {
      _phase.value = AnodyneSubstancePhase.NotFound
    }
  }

  fun load(nameToLoad: String = substanceTitle) {
    viewModelScope.launch {
      _phase.value = AnodyneSubstancePhase.Loading
      val match = findLocalMatch(nameToLoad)

      val result = repository.lookup(nameToLoad)
      result.fold(
          onSuccess = { sub ->
            if (sub != null) {
              val names = listOf(sub.title) + sub.abbreviations + sub.aliases
              val notesResult = repository.getUserNotes(names)
              val notes = notesResult.getOrNull().orEmpty()
              _phase.value =
                  AnodyneSubstancePhase.Found(
                      substance = sub,
                      userNotes = notes,
                      localMatch = match ?: findLocalMatchForSubstance(sub),
                  )
            } else {
              _phase.value = AnodyneSubstancePhase.NotFound
            }
          },
          onFailure = { err ->
            _phase.value = AnodyneSubstancePhase.Failed(err.message ?: "Failed to load substance")
          },
      )
    }
  }

  private suspend fun findLocalMatch(name: String): LocalSubstanceMatch? {
    val sRepo = substanceRepo ?: return null
    val cRepo = customRepo ?: return null

    sRepo.getSubstance(name)?.let {
      return LocalSubstanceMatch.Psychonaut(it.name)
    }
    val allPw = sRepo.getAllSubstances()
    allPw
        .firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?.let {
          return LocalSubstanceMatch.Psychonaut(it.name)
        }

    cRepo.getWithEverythingByName(name)?.let {
      return LocalSubstanceMatch.Custom(it.substance.id, it.substance.name)
    }

    return null
  }

  private suspend fun findLocalMatchForSubstance(sub: AnodyneSubstance): LocalSubstanceMatch? {
    val names = listOf(sub.title) + sub.abbreviations + sub.aliases
    for (name in names) {
      findLocalMatch(name)?.let {
        return it
      }
    }
    return null
  }

  /** [name] and [unit] are what the user confirmed in the sheet. */
  fun addAsCustomSubstance(name: String, unit: String, onCreated: (Int) -> Unit) {
    val currentPhase = _phase.value
    if (currentPhase !is AnodyneSubstancePhase.Found) return
    val sub = currentPhase.substance
    val eRepo = experienceRepo ?: return
    val cRepo = customRepo ?: return
    if (isAdding) return
    isAdding = true

    viewModelScope.launch {
      // Custom substance names are unique; open the existing one instead of a duplicate.
      cRepo.getWithEverythingByName(name)?.let { existing ->
        _phase.value =
            currentPhase.copy(
                localMatch =
                    LocalSubstanceMatch.Custom(existing.substance.id, existing.substance.name)
            )
        isAdding = false
        onCreated(existing.substance.id)
        return@launch
      }
      val defaultUnit = unit
      val description =
          listOfNotNull(
                  sub.healthEffects?.takeIf { it.isNotBlank() },
                  sub.drugWarnings?.takeIf { it.isNotBlank() },
                  sub.formula?.let { "Formula: $it" },
                  sub.molarMass?.let { "Molar mass: $it" },
              )
              .joinToString("\n\n")

      val customSubstance =
          CustomSubstance(
              name = name,
              units = defaultUnit,
              description = description,
          )
      val insertedId = eRepo.insert(customSubstance)

      for (dosing in sub.dosing) {
        val route = dosing.route ?: continue
        val roa = CustomRoa(customSubstanceId = insertedId, route = route)
        cRepo.upsertRoa(insertedId, roa)

        val roaDose =
            CustomRoaDose(
                customSubstanceId = insertedId,
                route = route,
                units = dosing.unit ?: defaultUnit,
                lightMin = dosing.light?.lower,
                commonMin = dosing.common?.lower,
                strongMin = dosing.strong?.lower,
                heavyMin = dosing.heavy?.lower,
            )
        cRepo.upsertDose(insertedId, roaDose)
      }

      substanceRepo?.let { sRepo ->
        val allCategories = sRepo.getAllCategories().map { it.name }
        val matchedCategories =
            sub.classes
                .filter { tag ->
                  allCategories.any { it.equals(tag, ignoreCase = true) }
                }
                .distinct()

        for (category in matchedCategories) {
          val canonicalCategory = allCategories.first { it.equals(category, ignoreCase = true) }
          cRepo.assignCategory(insertedId, canonicalCategory)
        }
      }

      _phase.value = currentPhase.copy(localMatch = LocalSubstanceMatch.Custom(insertedId, name))
      isAdding = false
      onCreated(insertedId)
    }
  }

  private var isAdding = false
}
