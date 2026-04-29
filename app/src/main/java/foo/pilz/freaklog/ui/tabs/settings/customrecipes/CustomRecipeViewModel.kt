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

package foo.pilz.freaklog.ui.tabs.settings.customrecipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.data.room.experiences.CustomRecipeRepository
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.relations.CustomRecipeWithComponents
import foo.pilz.freaklog.data.substances.AdministrationRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class CustomRecipeViewModel @Inject constructor(
    private val customRecipeRepository: CustomRecipeRepository
) : ViewModel() {

    val activeRecipesFlow: StateFlow<List<CustomRecipeWithComponents>> =
        customRecipeRepository.getActiveRecipesWithComponentsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val archivedRecipesFlow: StateFlow<List<CustomRecipeWithComponents>> =
        customRecipeRepository.getArchivedRecipesWithComponentsFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _selectedRecipe = MutableStateFlow<CustomRecipeWithComponents?>(null)
    val selectedRecipe = _selectedRecipe.asStateFlow()

    private val _isEditDialogOpen = MutableStateFlow(false)
    val isEditDialogOpen = _isEditDialogOpen.asStateFlow()

    private val _recipeName = MutableStateFlow("")
    val recipeName = _recipeName.asStateFlow()

    private val _recipeNote = MutableStateFlow("")
    val recipeNote = _recipeNote.asStateFlow()

    private val _components = MutableStateFlow<List<RecipeComponentState>>(emptyList())
    val components = _components.asStateFlow()

    fun openCreateDialog() {
        _selectedRecipe.value = null
        _recipeName.value = ""
        _recipeNote.value = ""
        _components.value = emptyList()
        _isEditDialogOpen.value = true
    }

    fun openEditDialog(recipe: CustomRecipeWithComponents) {
        _selectedRecipe.value = recipe
        _recipeName.value = recipe.recipe.name
        _recipeNote.value = recipe.recipe.note
        _components.value = recipe.components.sortedBy { it.componentOrder }.map {
            RecipeComponentState(
                substanceName = it.substanceName,
                dose = it.dose,
                isDoseAnEstimate = it.isDoseAnEstimate,
                estimatedDoseStandardDeviation = it.estimatedDoseStandardDeviation,
                units = it.units ?: "",
                administrationRoute = it.administrationRoute,
                customUnitId = it.customUnitId
            )
        }
        _isEditDialogOpen.value = true
    }

    fun closeDialog() {
        _isEditDialogOpen.value = false
        _selectedRecipe.value = null
    }

    fun updateRecipeName(name: String) {
        _recipeName.value = name
    }

    fun updateRecipeNote(note: String) {
        _recipeNote.value = note
    }

    fun addComponent() {
        _components.value = _components.value + RecipeComponentState()
    }

    fun updateComponent(index: Int, component: RecipeComponentState) {
        _components.value = _components.value.toMutableList().apply {
            set(index, component)
        }
    }

    fun removeComponent(index: Int) {
        _components.value = _components.value.toMutableList().apply {
            removeAt(index)
        }
    }

    fun saveRecipe() {
        viewModelScope.launch {
            val name = _recipeName.value.trim()
            if (name.isEmpty() || _components.value.isEmpty()) return@launch

            val existing = _selectedRecipe.value
            
            if (existing != null) {
                // Update existing recipe
                val updatedRecipe = existing.recipe.copy(
                    name = name,
                    note = _recipeNote.value.trim()
                )
                val updatedComponents = _components.value.mapIndexed { index, comp ->
                    CustomRecipeComponent(
                        customRecipeId = existing.recipe.id,
                        substanceName = comp.substanceName,
                        dose = comp.dose,
                        isDoseAnEstimate = comp.isDoseAnEstimate,
                        estimatedDoseStandardDeviation = comp.estimatedDoseStandardDeviation,
                        units = comp.units.takeIf { it.isNotBlank() },
                        administrationRoute = comp.administrationRoute,
                        customUnitId = comp.customUnitId,
                        componentOrder = index
                    )
                }
                customRecipeRepository.updateRecipeWithComponents(updatedRecipe, updatedComponents)
            } else {
                // Create new recipe
                val newRecipe = CustomRecipe(
                    name = name,
                    note = _recipeNote.value.trim(),
                    creationDate = Instant.now()
                )
                val newComponents = _components.value.mapIndexed { index, comp ->
                    CustomRecipeComponent(
                        customRecipeId = 0, // Will be set by repository
                        substanceName = comp.substanceName,
                        dose = comp.dose,
                        isDoseAnEstimate = comp.isDoseAnEstimate,
                        estimatedDoseStandardDeviation = comp.estimatedDoseStandardDeviation,
                        units = comp.units.takeIf { it.isNotBlank() },
                        administrationRoute = comp.administrationRoute,
                        customUnitId = comp.customUnitId,
                        componentOrder = index
                    )
                }
                customRecipeRepository.insertRecipeWithComponents(newRecipe, newComponents)
            }
            closeDialog()
        }
    }

    fun deleteRecipe(recipeId: Int) {
        viewModelScope.launch {
            customRecipeRepository.deleteRecipeWithComponents(recipeId)
        }
    }

    fun toggleArchived(recipeId: Int, isArchived: Boolean) {
        viewModelScope.launch {
            customRecipeRepository.setArchived(recipeId, !isArchived)
        }
    }

    fun useRecipe(recipeId: Int) {
        viewModelScope.launch {
            customRecipeRepository.updateLastUsedDate(recipeId, Instant.now())
        }
    }
}

data class RecipeComponentState(
    val substanceName: String = "",
    val dose: Double? = null,
    val isDoseAnEstimate: Boolean = false,
    val estimatedDoseStandardDeviation: Double? = null,
    val units: String = "",
    val administrationRoute: AdministrationRoute = AdministrationRoute.ORAL,
    val customUnitId: Int? = null
)
