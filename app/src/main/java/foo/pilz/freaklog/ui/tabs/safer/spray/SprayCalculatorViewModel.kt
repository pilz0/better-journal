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
        maybeUpdateTotalWeight()
    }

    fun setLiquidAmountInMl(value: String) {
        _liquidAmountInMl.value = value
        maybeUpdateTotalWeight()
    }

    fun setTotalWeight(value: String) {
        _totalWeight.value = value
        maybeUpdateLiquidVolume()
    }

    fun setPurityInPercent(value: String) {
        _purityInPercent.value = value
    }

    fun selectSpray(sprayId: Int) {
        _selectedSprayId.value = sprayId
    }

    fun saveSelection() {
        viewModelScope.launch {
            val selectedId = _selectedSprayId.value
            if (selectedId != null) {
                sprayRepository.setPreferred(selectedId)
            }
        }
    }

    fun deleteSpray(spray: Spray) {
        viewModelScope.launch {
            sprayRepository.delete(spray)
            if (_selectedSprayId.value == spray.id) {
                val remaining = sprayRepository.getAllSprays()
                _selectedSprayId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun addSpray(name: String, contentInMl: Double, numSprays: Double) {
        viewModelScope.launch {
            val spray = Spray(
                name = name,
                contentInMl = contentInMl,
                numSprays = numSprays,
                creationDate = Instant.now(),
                isPreferred = true
            )
            val id = sprayRepository.insert(spray).toInt()
            _selectedSprayId.value = id
        }
    }

    private fun maybeUpdateTotalWeight() {
        val liquidMl = _liquidAmountInMl.value.toDoubleOrNull() ?: return
        val weightPerSprayValue = _weightPerSpray.value.toDoubleOrNull() ?: return
        val sprays = spraysFlow.value
        val selectedId = _selectedSprayId.value ?: return
        val selectedSpray = sprays.find { it.id == selectedId } ?: return

        val numSprays = liquidMl * selectedSpray.numSprays / selectedSpray.contentInMl
        val result = numSprays * weightPerSprayValue
        val resultText = result.toReadableString()
        if (resultText != _totalWeight.value) {
            _totalWeight.value = resultText
        }
    }

    private fun maybeUpdateLiquidVolume() {
        val totalWeightValue = _totalWeight.value.toDoubleOrNull() ?: return
        val weightPerSprayValue = _weightPerSpray.value.toDoubleOrNull() ?: return
        val sprays = spraysFlow.value
        val selectedId = _selectedSprayId.value ?: return
        val selectedSpray = sprays.find { it.id == selectedId } ?: return

        val numSprays = totalWeightValue / weightPerSprayValue
        val result = numSprays * selectedSpray.contentInMl / selectedSpray.numSprays
        val resultText = result.toReadableString()
        if (resultText != _liquidAmountInMl.value) {
            _liquidAmountInMl.value = resultText
        }
    }

    fun getDoseAdjustedToPurity(): Double? {
        val totalWeightValue = _totalWeight.value.toDoubleOrNull() ?: return null
        val purityValue = _purityInPercent.value.toDoubleOrNull() ?: return null
        if (purityValue <= 0) return null
        return totalWeightValue * 100 / purityValue
    }

    fun getConcentrationPerMl(): Double? {
        val totalWeightValue = _totalWeight.value.toDoubleOrNull() ?: return null
        val liquidMl = _liquidAmountInMl.value.toDoubleOrNull() ?: return null
        if (liquidMl <= 0) return null
        return totalWeightValue / liquidMl
    }

    fun getNumberOfSprays(): Double? {
        val liquidMl = _liquidAmountInMl.value.toDoubleOrNull() ?: return null
        val sprays = spraysFlow.value
        val selectedId = _selectedSprayId.value ?: return null
        val selectedSpray = sprays.find { it.id == selectedId } ?: return null
        if (selectedSpray.contentInMl <= 0) return null
        return liquidMl * selectedSpray.numSprays / selectedSpray.contentInMl
    }
}
