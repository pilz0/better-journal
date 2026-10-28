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

package foo.pilz.freaklog.ui.tabs.journal.experience.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Location
import foo.pilz.freaklog.ui.main.navigation.graphs.EditExperienceRoute
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class EditExperienceViewModel @Inject constructor(
    private val repository: ExperienceRepository,
    state: SavedStateHandle
) :
    ViewModel() {

    var experience: Experience? = null
    var enteredTitle by mutableStateOf("")
    val isEnteredTitleOk get() = enteredTitle.isNotBlank()
    var enteredText by mutableStateOf("")
    var enteredLocation by mutableStateOf("")
    private var oldLongitude: Double? = null
    private var oldLatitude: Double? = null


    init {
        val editExperienceRoute = state.toRoute<EditExperienceRoute>()
        viewModelScope.launch {
            val exp = repository.getExperience(id = editExperienceRoute.experienceId)
            experience = exp
            if (exp != null) {
                enteredTitle = exp.title
                enteredText = exp.text
                enteredLocation = exp.location?.name ?: ""
                oldLongitude = exp.location?.longitude
                oldLatitude = exp.location?.latitude
            }
        }
    }

    fun onDoneTap() {
        if (isEnteredTitleOk) {
            val currentExperience = experience ?: return
            viewModelScope.launch {
                currentExperience.title = enteredTitle.trim()
                currentExperience.text = enteredText
                val trimmedLocation = enteredLocation.trim()
                val location = if (trimmedLocation.isNotBlank()) {
                    val isLocationSame = trimmedLocation.equals(
                        currentExperience.location?.name?.trim(),
                        ignoreCase = true
                    )
                    Location(
                        name = trimmedLocation,
                        longitude = if (isLocationSame) oldLongitude else null,
                        latitude = if (isLocationSame) oldLatitude else null
                    )
                } else {
                    null
                }
                currentExperience.location = location
                repository.update(experience = currentExperience)
            }
        }
    }

}
