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
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.Experience
import foo.pilz.freaklog.data.room.experiences.entities.Location
import foo.pilz.freaklog.ui.main.navigation.graphs.EditExperienceRoute
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isEnteredTitleOk get() = enteredTitle.isNotEmpty()
    var enteredText by mutableStateOf("")
    var enteredLocation by mutableStateOf("")
    private var oldLongitude: Double? = null
    private var oldLatitude: Double? = null


    init {
        val editExperienceRoute = state.toRoute<EditExperienceRoute>()
        viewModelScope.launch {
            experience = repository.getExperience(id = editExperienceRoute.experienceId)!!
            enteredTitle = experience!!.title
            enteredText = experience!!.text
            enteredLocation = experience!!.location?.name ?: ""
            oldLongitude = experience!!.location?.longitude
            oldLatitude = experience!!.location?.latitude
        }
    }

    fun onDoneTap() {
        if (enteredTitle.isNotEmpty()) {
            viewModelScope.launch {
                experience!!.title = enteredTitle
                experience!!.text = enteredText
                val location = if (enteredLocation.isNotBlank()) {
                    Location(name = enteredLocation, longitude = oldLongitude, latitude = oldLatitude)
                } else {
                    null
                }
                experience!!.location = location
                repository.update(experience = experience!!)
            }
        }
    }

}