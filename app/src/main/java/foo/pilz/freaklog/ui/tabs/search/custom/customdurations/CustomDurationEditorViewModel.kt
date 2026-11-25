package foo.pilz.freaklog.ui.tabs.search.custom.customdurations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.data.anodyne.model.AnodyneDosing
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoa
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.main.navigation.graphs.CustomDurationEditorRoute
import foo.pilz.freaklog.ui.tabs.journal.experience.TimelineDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.components.DataForOneEffectLine
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import foo.pilz.freaklog.ui.utils.getInstant
import foo.pilz.freaklog.ui.utils.stateInVm
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CustomDurationEditorViewModel(
    val experienceRepository: ExperienceRepository,
    private val anodyneRepository: AnodyneRepository,
    val state: SavedStateHandle,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

  @Inject
  constructor(
      experienceRepository: ExperienceRepository,
      anodyneRepository: AnodyneRepository,
      state: SavedStateHandle,
  ) : this(
      experienceRepository = experienceRepository,
      anodyneRepository = anodyneRepository,
      state = state,
      ioDispatcher = Dispatchers.IO,
      defaultDispatcher = Dispatchers.Default,
  )

  private val navRoute: CustomDurationEditorRoute? =
      runCatching { state.toRoute<CustomDurationEditorRoute>() }.getOrNull()
  val substanceId: Int = navRoute?.substanceId ?: state.get<Int>("substanceId") ?: 0
  val substanceName: String = navRoute?.substanceName ?: state.get<String>("substanceName") ?: ""
  val route: AdministrationRoute =
      navRoute?.route
          ?: state.get<AdministrationRoute>("route")
          ?: state.get<String>("route")?.let {
            runCatching { AdministrationRoute.valueOf(it) }.getOrNull()
          }
          ?: AdministrationRoute.ORAL
  var roaDurationFlow = MutableStateFlow<CustomRoaDuration?>(null)
  var roaDoseFlow = MutableStateFlow<CustomRoaDose?>(null)
  var roaFlow = MutableStateFlow<CustomRoa?>(null)
  var substanceUnitFlow = MutableStateFlow("")
  val ingestionPreviewTimeFlow = MutableStateFlow(LocalDateTime.now())

  /** The substance's other routes with saved dose or durations, to copy into this route. */
  val otherRoutesFlow = MutableStateFlow<List<RouteCopySource>>(emptyList())

  /** AnodyneWiki's dose tiers, one per route, to copy into this route. */
  val anodyneDosingFlow = MutableStateFlow<List<AnodyneDosing>>(emptyList())

  val timelineDisplayOptionFlow =
      combine(
              roaDurationFlow,
              ingestionPreviewTimeFlow,
          ) { roaDuration, ingestionTime ->
            val dataForEffectLine =
                DataForOneEffectLine(
                    substanceName = substanceName,
                    route = route,
                    roaDuration = roaDuration?.toRoaDuration(),
                    height = 1f,
                    horizontalWeight = 0.5f,
                    color = route.color,
                    startTime = ingestionTime.getInstant(),
                    endTime = null,
                )

            val model =
                AllTimelinesModel(
                    dataForLines = listOf(dataForEffectLine),
                    dataForRatings = emptyList(),
                    timedNotes = emptyList(),
                    areSubstanceHeightsIndependent = false,
                )
            return@combine TimelineDisplayOption.Shown(model)
          }
          .flowOn(defaultDispatcher)
          .stateInVm(viewModelScope, TimelineDisplayOption.Loading)

  init {
    viewModelScope.launch {
      val unit = experienceRepository.getCustomSubstance(substanceName)?.units ?: ""
      substanceUnitFlow.value = unit

      roaDurationFlow.value =
          experienceRepository.getCustomRoaDurations(substanceId).find { it.route == route }
              ?: CustomRoaDuration(
                  route = route,
                  customSubstanceId = substanceId,
                  onset = null,
                  comeup = null,
                  peak = null,
                  offset = null,
                  total = null,
              )

      roaDoseFlow.value =
          experienceRepository.getCustomRoaDose(substanceId, route)
              ?: CustomRoaDose(
                  customSubstanceId = substanceId,
                  route = route,
                  units = unit,
              )

      roaFlow.value =
          experienceRepository.getCustomRoa(substanceId, route)
              ?: CustomRoa(
                  customSubstanceId = substanceId,
                  route = route,
                  bioavailability = null,
              )
      loadOtherRoutes()
      loadAnodyneDosing()
    }
  }

  private suspend fun loadOtherRoutes() {
    val doses = experienceRepository.getCustomRoaDoses(substanceId).associateBy { it.route }
    val durations = experienceRepository.getCustomRoaDurations(substanceId).associateBy { it.route }
    otherRoutesFlow.value =
        AdministrationRoute.entries
            .filter { it != route }
            .mapNotNull { other ->
              val source = RouteCopySource(other, doses[other], durations[other])
              source.takeIf { it.hasData }
            }
  }

  /** Replaces this route's dose tiers and durations with those of another route. */
  fun copyRoute(source: RouteCopySource) {
    roaDoseFlow.value?.let { current ->
      val dose = source.dose
      roaDoseFlow.value =
          current.copy(
              units = dose?.units ?: current.units,
              lightMin = dose?.lightMin,
              commonMin = dose?.commonMin,
              strongMin = dose?.strongMin,
              heavyMin = dose?.heavyMin,
          )
    }
    roaDurationFlow.value?.let { current ->
      val duration = source.duration
      roaDurationFlow.value =
          current.copy(
              onset = duration?.onset,
              comeup = duration?.comeup,
              peak = duration?.peak,
              offset = duration?.offset,
              total = duration?.total,
          )
    }
  }

  // Cached AnodyneWiki data, or loaded now if the user lets it load automatically, as on iOS
  private suspend fun loadAnodyneDosing() {
    val substance =
        withContext(ioDispatcher) { anodyneRepository.cachedLookup(substanceName) }
            ?: if (anodyneRepository.isAutoLoadEnabled()) {
              anodyneRepository.lookup(substanceName).getOrNull()
            } else {
              null
            }
    anodyneDosingFlow.value =
        substance
            ?.dosing
            .orEmpty()
            .filter {
              it.route != null &&
                  listOfNotNull(it.light, it.common, it.strong, it.heavy).isNotEmpty()
            }
            .distinctBy { it.route }
  }

  /** Replaces this route's dose tiers (not its durations) with where AnodyneWiki's tiers start. */
  fun copyAnodyneDose(dosing: AnodyneDosing) {
    val current = roaDoseFlow.value ?: return
    roaDoseFlow.value =
        current.copy(
            units = dosing.unit ?: current.units,
            lightMin = dosing.light?.lower,
            commonMin = dosing.common?.lower,
            strongMin = dosing.strong?.lower,
            heavyMin = dosing.heavy?.lower,
        )
  }

  fun onDone(onComplete: () -> Unit = {}) {
    viewModelScope.launch {
      roaDurationFlow.value?.let { duration ->
        if (duration.hasPhases) {
          experienceRepository.insert(duration)
        } else {
          experienceRepository.delete(duration)
        }
      }
      roaDoseFlow.value?.let { dose ->
        if (dose.hasTiers) {
          experienceRepository.upsertCustomRoaDose(substanceId, dose)
        } else {
          experienceRepository.delete(dose)
        }
      }
      roaFlow.value?.let { roa ->
        if (roa.bioavailability != null) {
          experienceRepository.insert(roa)
        } else {
          experienceRepository.delete(roa)
        }
      }
      onComplete()
    }
  }

  fun onDelete(onComplete: () -> Unit = {}) {
    viewModelScope.launch {
      roaDurationFlow.value?.let { experienceRepository.delete(it) }
      roaDoseFlow.value?.let { experienceRepository.delete(it) }
      roaFlow.value?.let { experienceRepository.delete(it) }
      onComplete()
    }
  }
}

/** Another route's saved dose and durations. */
data class RouteCopySource(
    val route: AdministrationRoute,
    val dose: CustomRoaDose?,
    val duration: CustomRoaDuration?,
) {
  val hasData: Boolean
    get() = dose?.hasTiers == true || duration?.hasPhases == true
}

internal val CustomRoaDose.hasTiers: Boolean
  get() = listOfNotNull(lightMin, commonMin, strongMin, heavyMin).isNotEmpty()

internal val CustomRoaDuration.hasPhases: Boolean
  get() = listOfNotNull(onset, comeup, peak, offset, total).isNotEmpty()
