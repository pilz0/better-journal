/*
 * Copyright (c) 2024. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.safer.spray

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import foo.pilz.freaklog.data.room.SprayRepository
import foo.pilz.freaklog.data.room.experiences.entities.Spray
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SprayCalculatorViewModel @Inject constructor(
    private val sprayRepository: SprayRepository
) : ViewModel() {

    val spraysFlow: StateFlow<List<Spray>> = sprayRepository.getAllSpraysFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedSprayId = MutableStateFlow<Int?>(null)
    val selectedSprayId: StateFlow<Int?> = _selectedSprayId.asStateFlow()

    private val _weightUnit = MutableStateFlow(WeightUnit.MG)
    val weightUnit: StateFlow<WeightUnit> = _weightUnit.asStateFlow()

    private val _weightPerSpray = MutableStateFlow("")
    val weightPerSpray: StateFlow<String> = _weightPerSpray.asStateFlow()

    private val _liquidAmountInMl = MutableStateFlow("")
    val liquidAmountInMl: StateFlow<String> = _liquidAmountInMl.asStateFlow()

    private val _totalWeight = MutableStateFlow("")
    val totalWeight: StateFlow<String> = _totalWeight.asStateFlow()

    private val _purityInPercent = MutableStateFlow("")
    val purityInPercent: StateFlow<String> = _purityInPercent.asStateFlow()

    private enum class CalculatedOutput { TOTAL_WEIGHT, LIQUID_VOLUME }

    /** The field whose current value was calculated rather than typed by the user. */
    private var calculatedOutput: CalculatedOutput? = null

    init {
        viewModelScope.launch {
            val preferredSpray = sprayRepository.getPreferredSpray()
            if (preferredSpray != null) {
                _selectedSprayId.value = preferredSpray.id
            }
        }
    }

    fun setWeightUnit(unit: WeightUnit) {
        _weightUnit.value = unit
    }

    fun setWeightPerSpray(value: String) {
        _weightPerSpray.value = value
        recalculate()
    }

    fun setLiquidAmountInMl(value: String) {
        _liquidAmountInMl.value = value
        if (calculatedOutput == CalculatedOutput.LIQUID_VOLUME) calculatedOutput = null
        updateOutput(CalculatedOutput.TOTAL_WEIGHT)
    }

    fun setTotalWeight(value: String) {
        _totalWeight.value = value
        if (calculatedOutput == CalculatedOutput.TOTAL_WEIGHT) calculatedOutput = null
        updateOutput(CalculatedOutput.LIQUID_VOLUME)
    }

    fun setPurityInPercent(value: String) {
        _purityInPercent.value = value
    }

    fun selectSpray(sprayId: Int) {
        _selectedSprayId.value = sprayId
        recalculate()
        viewModelScope.launch {
            sprayRepository.setPreferred(sprayId)
        }
    }

    fun deleteSpray(spray: Spray) {
        viewModelScope.launch {
            sprayRepository.delete(spray)
            if (_selectedSprayId.value == spray.id) {
                val newSelectedId = sprayRepository.getAllSprays().firstOrNull { it.id != spray.id }?.id
                _selectedSprayId.value = newSelectedId
                recalculate()
                if (newSelectedId != null) {
                    sprayRepository.setPreferred(newSelectedId)
                }
            }
        }
    }

    fun addSpray(name: String, contentInMl: Double, numSprays: Double) {
        val trimmedName = name.trim()
        val isValid = trimmedName.isNotEmpty() &&
            contentInMl.isFinite() && contentInMl > 0.0 &&
            numSprays.isFinite() && numSprays > 0.0
        if (!isValid) return
        viewModelScope.launch {
            val spray = Spray(
                name = trimmedName,
                contentInMl = contentInMl,
                numSprays = numSprays,
                creationDate = Instant.now(),
                isPreferred = true
            )
            val id = sprayRepository.insert(spray).toInt()
            _selectedSprayId.value = id
        }
    }

    private fun parseDecimal(value: String): Double? =
        value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

    private fun selectedValidSpray(): Spray? {
        val selectedSpray = _selectedSprayId.value?.let { id -> spraysFlow.value.find { it.id == id } } ?: return null
        val isSprayValid = selectedSpray.contentInMl > 0.0 &&
            selectedSpray.numSprays > 0.0 &&
            selectedSpray.contentInMl.isFinite() &&
            selectedSpray.numSprays.isFinite()
        return selectedSpray.takeIf { isSprayValid }
    }

    /** Recalculates the field holding the last calculated value, or whichever field can be calculated. */
    private fun recalculate() {
        val output = calculatedOutput
        if (output != null) {
            updateOutput(output)
        } else if (!updateOutput(CalculatedOutput.TOTAL_WEIGHT)) {
            updateOutput(CalculatedOutput.LIQUID_VOLUME)
        }
    }

    /**
     * Writes the calculated value into [output]. If it can't be calculated (an input was cleared or
     * is zero) and [output] holds a previously calculated value, that stale value is cleared.
     */
    private fun updateOutput(output: CalculatedOutput): Boolean {
        val result = when (output) {
            CalculatedOutput.TOTAL_WEIGHT -> calculateTotalWeight()
            CalculatedOutput.LIQUID_VOLUME -> calculateLiquidVolume()
        }
        val field = when (output) {
            CalculatedOutput.TOTAL_WEIGHT -> _totalWeight
            CalculatedOutput.LIQUID_VOLUME -> _liquidAmountInMl
        }
        if (result != null) {
            field.value = result.toReadableString()
            calculatedOutput = output
            return true
        }
        if (calculatedOutput == output) {
            field.value = ""
            calculatedOutput = null
        }
        return false
    }

    private fun calculateTotalWeight(): Double? {
        val liquidMl = parseDecimal(_liquidAmountInMl.value) ?: return null
        val weightPerSprayValue = parseDecimal(_weightPerSpray.value) ?: return null
        val selectedSpray = selectedValidSpray() ?: return null
        val numSprays = liquidMl * selectedSpray.numSprays / selectedSpray.contentInMl
        val result = numSprays * weightPerSprayValue
        return result.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun calculateLiquidVolume(): Double? {
        val totalWeightValue = parseDecimal(_totalWeight.value) ?: return null
        val weightPerSprayValue = parseDecimal(_weightPerSpray.value)?.takeIf { it > 0.0 } ?: return null
        val selectedSpray = selectedValidSpray() ?: return null
        val numSprays = totalWeightValue / weightPerSprayValue
        val result = numSprays * selectedSpray.contentInMl / selectedSpray.numSprays
        return result.takeIf { it.isFinite() && it > 0.0 }
    }

    fun getDoseAdjustedToPurity(): Double? {
        val totalWeightValue = parseDecimal(_totalWeight.value) ?: return null
        val purityValue = parseDecimal(_purityInPercent.value) ?: return null
        if (purityValue <= 0.0 || purityValue > 100.0 || totalWeightValue <= 0.0) return null
        val result = totalWeightValue * 100.0 / purityValue
        return result.takeIf { it.isFinite() && it > 0.0 }
    }

    fun getConcentrationPerMl(): Double? {
        val totalWeightValue = parseDecimal(_totalWeight.value) ?: return null
        val liquidMl = parseDecimal(_liquidAmountInMl.value) ?: return null
        if (liquidMl <= 0.0 || totalWeightValue <= 0.0) return null
        val result = totalWeightValue / liquidMl
        return result.takeIf { it.isFinite() && it > 0.0 }
    }

    fun getNumberOfSprays(): Double? {
        val liquidMl = parseDecimal(_liquidAmountInMl.value) ?: return null
        if (liquidMl <= 0.0) return null
        val selectedSpray = selectedValidSpray() ?: return null
        val result = liquidMl * selectedSpray.numSprays / selectedSpray.contentInMl
        return result.takeIf { it.isFinite() && it > 0.0 }
    }
}
