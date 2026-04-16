package foo.pilz.freaklog.ui.tabs.settings.funny

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions.InteractionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val experienceRepo: ExperienceRepository,
    private val substanceRepo: SubstanceRepository,
    interactionChecker: InteractionChecker,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val defs = loadAchievements(context)
    private var previouslyUnlocked: Set<String> = emptySet()

    private val _newlyUnlocked = MutableSharedFlow<AchievementDef>()
    val newlyUnlocked = _newlyUnlocked.asSharedFlow()

    val achievementsFlow: StateFlow<List<UnlockedAchievement>> =
        experienceRepo.getSortedExperiencesWithIngestionsFlow()
            .map { experiences ->
                val allIngestions = experiences.flatMap { it.ingestions }

                val results = defs.map { def ->
                    UnlockedAchievement(
                        def = def,
                        unlocked = evaluateAchievement(
                            def.condition,
                            experiences,
                            allIngestions,
                            substanceRepo,
                            interactionChecker
                        )
                    )
                }

                val currentlyUnlocked = results.filter { it.unlocked }.map { it.def.id }.toSet()
                val newOnes = currentlyUnlocked - previouslyUnlocked
                if (previouslyUnlocked.isNotEmpty()) {
                    newOnes.forEach { id ->
                        val def = defs.first { it.id == id }
                        viewModelScope.launch { _newlyUnlocked.emit(def) }
                    }
                }
                previouslyUnlocked = currentlyUnlocked

                results
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = defs.map { UnlockedAchievement(it, false) }
            )
}
