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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AchievementViewModel
@Inject
constructor(
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    private val customRecipeRepository: CustomRecipeRepository,
    private val achievementPreferences: AchievementPreferences,
    interactionChecker: InteractionChecker,
    @ApplicationContext context: Context,
) : ViewModel() {

  private val defs = loadAchievements(context)

  /**
   * Combines all the journal data the evaluator needs into a single flow, and uses
   * [distinctUntilChanged] to skip re-running the whole evaluator when the underlying entity lists
   * are structurally equal between emissions. Room's `Flow` queries already emit only on real
   * changes, so this is mostly a guard against rapid duplicate emissions; using the default
   * structural-equality check is safe because achievement predicates inspect many non-ID fields
   * (notes, administrationRoute, time, location, isFavorite, ...) that a cheap ID-only hash would
   * miss.
   */
  /** Null until the first evaluation finished. */
  val achievementsFlow: StateFlow<List<UnlockedAchievement>?> =
      combine(
              experienceRepo.getAllExperiencesWithIngestionsTimedNotesAndRatingsFlow(),
              experienceRepo.getAllCustomUnitsFlow(),
              customRecipeRepository.getActiveRecipesFlow(),
          ) { experiences, customUnits, recipes ->
            Triple(experiences, customUnits, recipes)
          }
          .distinctUntilChanged()
          .map { (experiences, customUnits, customRecipes) ->
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
