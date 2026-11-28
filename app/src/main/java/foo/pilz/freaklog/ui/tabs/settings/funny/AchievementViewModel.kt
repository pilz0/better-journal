package foo.pilz.freaklog.ui.tabs.settings.funny

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.room.experiences.CustomRecipeRepository
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AchievementViewModel
@Inject
constructor(
    experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    customRecipeRepository: CustomRecipeRepository,
    private val achievementPreferences: AchievementPreferences,
    interactionChecker: InteractionChecker,
    @ApplicationContext context: Context,
) : ViewModel() {

  private val defsFlow = flow { emit(loadAchievements(context)) }.flowOn(Dispatchers.IO)

  private val journalInputsFlow =
      combine(
              experienceRepo.getAllExperiencesWithIngestionsTimedNotesAndRatingsFlow(),
              experienceRepo.getAllCustomUnitsFlow(),
              customRecipeRepository.getActiveRecipesFlow(),
          ) { experiences, customUnits, recipes ->
            Triple(experiences, customUnits, recipes)
          }
          .distinctUntilChanged()

  /** Null until the first evaluation finished. */
  val achievementsFlow: StateFlow<List<UnlockedAchievement>?> =
      combine(defsFlow, journalInputsFlow) { defs, (experiences, customUnits, customRecipes) ->
            val allIngestions = experiences.flatMap { it.ingestions }
            val allTimedNotes = experiences.flatMap { it.timedNotes }
            val allRatings = experiences.flatMap { it.ratings }

            val evaluationContext = currentCoroutineContext()
            val ctx =
                AchievementContext(
                    experiences = experiences,
                    allIngestions = allIngestions,
                    allTimedNotes = allTimedNotes,
                    allRatings = allRatings,
                    customUnits = customUnits,
                    customRecipes = customRecipes,
                    substanceRepo = substanceRepo,
                    interactionChecker = interactionChecker,
                    checkActive = { evaluationContext.ensureActive() },
                )

            defs.map { def ->
              currentCoroutineContext().ensureActive()
              UnlockedAchievement(
                  def = def,
                  unlocked =
                      runCatching { evaluateAchievement(def.condition, ctx) }.getOrDefault(false),
              )
            }
          }
          .flowOn(Dispatchers.Default)
          .stateIn(
              scope = viewModelScope,
              started = SharingStarted.Eagerly,
              initialValue = null,
          )

  val seenIdsFlow: StateFlow<Set<String>> =
      achievementPreferences.seenIdsFlow.stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000),
          initialValue = emptySet(),
      )

  fun markAllUnlockedAsSeen() {
    viewModelScope.launch {
      val unlocked =
          achievementsFlow.value.orEmpty().filter { it.unlocked }.map { it.def.id }.toSet()
      achievementPreferences.markSeen(unlocked)
    }
  }
}
