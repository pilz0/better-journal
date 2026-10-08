package foo.pilz.freaklog.ui.tabs.settings.funny

import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.tabs.journal.experience.notification.PhaseCalculator
import foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin.SkinEligibilityEntry
import foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin.hasActivePsychedelic
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UltraFunSkinEligibility @Inject constructor(
    experienceRepo: ExperienceRepository,
    substanceRepo: SubstanceRepository,
    userPreferences: UserPreferences,
) {
    private val currentTimeFlow: Flow<Instant> = flow {
        while (true) {
            emit(Instant.now())
            delay(60_000)
        }
    }

    private val isPsychedelicActiveFlow: Flow<Boolean> =
        experienceRepo.getSortedIngestions(limit = 50)
            .combine(currentTimeFlow) { ingestions, _ ->
                hasActivePsychedelic(
                    ingestions.map { ingestion ->
                        val substance = substanceRepo.getSubstance(ingestion.substanceName)
                        SkinEligibilityEntry(
                            isHallucinogen = substance?.isHallucinogen == true,
                            phase = PhaseCalculator.currentPhase(
                                ingestion.time,
                                substance?.roas?.find { it.route == ingestion.administrationRoute }?.roaDuration,
                                ingestion.endTime,
                            ).phase,
                        )
                    }
                )
            }
            .flowOn(Dispatchers.Default)

    val eligibleFlow: Flow<Boolean> = userPreferences.funnyConfigFlow
        .combine(isPsychedelicActiveFlow) { fc, active ->
            fc.enableFunny && fc.teamsSkin && active
        }
        .distinctUntilChanged()
}
