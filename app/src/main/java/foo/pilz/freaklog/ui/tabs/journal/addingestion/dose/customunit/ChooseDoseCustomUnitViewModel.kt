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

package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose.customunit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomUnit
import foo.pilz.freaklog.data.substances.classes.roa.DoseClass
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import foo.pilz.freaklog.ui.main.navigation.graphs.ChooseDoseCustomUnitRoute
import foo.pilz.freaklog.ui.tabs.journal.addingestion.search.suggestion.models.CustomUnitDose
import foo.pilz.freaklog.ui.utils.evaluateNumericExpression
import foo.pilz.freaklog.ui.utils.isValidDoseInput
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChooseDoseCustomUnitViewModel @Inject constructor(
    experienceRepo: ExperienceRepository,
    substanceRepo: SubstanceRepository,
    state: SavedStateHandle
) : ViewModel() {

    var customUnit: CustomUnit? by mutableStateOf(null)
    var doseRemark: String? by mutableStateOf(null)
    var roaDose: RoaDose? = null

    init {
        val chooseDoseCustomUnitRoute = runCatching { state.toRoute<ChooseDoseCustomUnitRoute>() }.getOrNull()
        val customUnitId = chooseDoseCustomUnitRoute?.customUnitId?.takeIf { it > 0 }
            ?: state.get<Int>("customUnitId")
            ?: -1
        if (customUnitId != -1) {
            viewModelScope.launch {
                val customUnit = experienceRepo.getCustomUnit(customUnitId)
                this@ChooseDoseCustomUnitViewModel.customUnit = customUnit
                if (customUnit != null) {
                    val substance = substanceRepo.getSubstance(customUnit.substanceName)
                    doseRemark = substance?.dosageRemark
                    roaDose = substance?.getRoa(customUnit.administrationRoute)?.roaDose
                }
            }
        }
    }

    // editable fields
    var doseText by mutableStateOf("")
    var isEstimate by mutableStateOf(false)
    var estimatedDoseDeviationText by mutableStateOf("")

    fun onDoseTextChange(newDoseText: String) {
        doseText = newDoseText
    }

    fun onEstimatedDoseDeviationChange(newEstimatedDeviationText: String) {
        estimatedDoseDeviationText = newEstimatedDeviationText
    }

    val dose: Double? get() = evaluateNumericExpression(doseText)
    val estimatedDoseDeviation: Double? get() = evaluateNumericExpression(estimatedDoseDeviationText)
    val isValidDose: Boolean get() = isValidDoseInput(dose, if (isEstimate) estimatedDoseDeviation else null)

    private val customUnitDose: CustomUnitDose?
        get() {
            return dose?.let { doseUnwrapped ->
                return@let customUnit?.let {
                    CustomUnitDose(
                        dose = doseUnwrapped,
                        isEstimate = isEstimate,
                        estimatedDoseStandardDeviation = estimatedDoseDeviation,
                        customUnit = it
                    )
                }
            }
        }
    val currentDoseClass: DoseClass? get() = roaDose?.getDoseClass(ingestionDose = customUnitDose?.calculatedDose)
    val customUnitCalculationText: String?
        get() {
            val cud = customUnitDose ?: return null
            val calculatedDesc = cud.calculatedDoseDescription ?: return null
            return "${cud.doseDescription} x ${cud.customUnit.getDoseOfOneUnitDescription()} = $calculatedDesc"
        }
}
