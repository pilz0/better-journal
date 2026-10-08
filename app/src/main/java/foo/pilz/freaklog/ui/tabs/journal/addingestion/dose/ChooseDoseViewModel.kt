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

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.ChooseDoseRoute
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.utils.evaluateNumericExpression
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class ChooseDoseViewModel @Inject constructor(
    repository: SubstanceRepository,
    private val experienceRepo: foo.pilz.freaklog.data.room.experiences.ExperienceRepository,
    state: SavedStateHandle,
) : ViewModel() {
    private val chooseDoseRoute = state.toRoute<ChooseDoseRoute>()
    val substance: Substance = repository.getSubstance(chooseDoseRoute.substanceName)!!
    val administrationRoute: AdministrationRoute = chooseDoseRoute.administrationRoute
    val roaDose: RoaDose? = substance.getRoa(administrationRoute)?.roaDose
    val roaDuration: foo.pilz.freaklog.data.substances.classes.roa.RoaDuration? =
        substance.getRoa(administrationRoute)?.roaDuration
    var isEstimate by mutableStateOf(false)
    var doseText by mutableStateOf("")
    var estimatedDoseStandardDeviationText by mutableStateOf("")
    var purityText by mutableStateOf("100")
    var units by mutableStateOf("")
    private val purity: Double?
        get() {
            val p = evaluateNumericExpression(purityText)
            return if (p != null && p > 0 && p <= 100) {
                p
            } else {
                null
            }
        }
    val isPurityValid: Boolean get() = purity != null
    val impureDoseWithUnit: String?
        get() {
            dose.let {
                if (it == null) return null
                purity.let { safePurity ->
                    if (safePurity == null) return null
                    val result = it.div(safePurity).times(100)
                    return result.toReadableString() + " impure ${roaDose?.units ?: ""}"
                }
            }
        }
    val dose: Double? get() = evaluateNumericExpression(doseText)
    val estimatedDoseStandardDeviation: Double? get() = evaluateNumericExpression(estimatedDoseStandardDeviationText)
    val isValidDose: Boolean get() = dose != null
    val currentDoseClass: DoseClass? get() = roaDose?.getDoseClass(ingestionDose = dose)

    fun onDoseTextChange(newDoseText: String) {
        doseText = newDoseText
    }

    fun onEstimatedDoseStandardDeviationChange(newEstimatedStandardDeviationText: String) {
        estimatedDoseStandardDeviationText = newEstimatedStandardDeviationText
    }

    private var loadedIntakeLimits by mutableStateOf<List<foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit>>(emptyList())
    private var pastLimitIngestions by mutableStateOf<List<foo.pilz.freaklog.ui.tabs.settings.intakelimits.LimitIngestion>>(emptyList())

    /** Live status of every enabled intake limit for this substance, including the dose being typed. */
    val intakeLimitStatuses: List<foo.pilz.freaklog.ui.tabs.settings.intakelimits.IntakeLimitStatus>
        get() {
            if (loadedIntakeLimits.isEmpty()) return emptyList()
            val now = java.time.Instant.now()
            val pending = foo.pilz.freaklog.ui.tabs.settings.intakelimits.LimitIngestion(dose, units.ifBlank { null }, now)
            return loadedIntakeLimits.map {
                foo.pilz.freaklog.ui.tabs.settings.intakelimits.evaluateIntakeLimit(it, now, pastLimitIngestions, pending)
            }
        }

    init {
        units = roaDose?.units ?: ""
        viewModelScope.launch { loadIntakeLimits() }
    }

    private suspend fun loadIntakeLimits() {
        val limits = experienceRepo.getIntakeLimitsForSubstance(substance.name).filter { it.isEnabled }
        if (limits.isEmpty()) return
        val since = java.time.Instant.now().minusSeconds(limits.maxOf { it.windowSeconds })
        pastLimitIngestions = experienceRepo
            .getIngestionsWithCustomUnitsForSubstanceSince(substance.name, since)
            .map { foo.pilz.freaklog.ui.tabs.settings.intakelimits.LimitIngestion(it.pureDose, it.originalUnit, it.ingestion.time) }
        loadedIntakeLimits = limits
    }

}
