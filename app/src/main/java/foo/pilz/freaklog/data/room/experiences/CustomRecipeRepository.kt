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

package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.relations.CustomRecipeWithComponents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomRecipeRepository @Inject constructor(
    private val customRecipeDao: CustomRecipeDao
) {
    
    fun getActiveRecipesFlow(): Flow<List<CustomRecipe>> = 
        customRecipeDao.getActiveRecipesFlow()
    
    fun getArchivedRecipesFlow(): Flow<List<CustomRecipe>> = 
        customRecipeDao.getArchivedRecipesFlow()
    
    fun getActiveRecipesWithComponentsFlow(): Flow<List<CustomRecipeWithComponents>> =
        customRecipeDao.getActiveRecipesWithComponentsFlow()
    
    fun getArchivedRecipesWithComponentsFlow(): Flow<List<CustomRecipeWithComponents>> =
        customRecipeDao.getArchivedRecipesWithComponentsFlow()
    
    suspend fun getRecipeWithComponents(recipeId: Int): CustomRecipeWithComponents? =
        withContext(Dispatchers.IO) {
            customRecipeDao.getRecipeWithComponents(recipeId)
        }
    
    suspend fun insertRecipeWithComponents(recipe: CustomRecipe, components: List<CustomRecipeComponent>): Long =
        withContext(Dispatchers.IO) {
            customRecipeDao.insertRecipeWithComponents(recipe, components)
        }
    
    suspend fun updateRecipeWithComponents(recipe: CustomRecipe, components: List<CustomRecipeComponent>) =
        withContext(Dispatchers.IO) {
            customRecipeDao.updateRecipeWithComponents(recipe, components)
        }
    
    suspend fun deleteRecipeWithComponents(recipeId: Int) =
        withContext(Dispatchers.IO) {
            customRecipeDao.deleteRecipeWithComponents(recipeId)
        }
    
    suspend fun updateLastUsedDate(recipeId: Int, timestamp: Instant = Instant.now()) =
        withContext(Dispatchers.IO) {
            customRecipeDao.updateLastUsedDate(recipeId, timestamp)
        }
    
    suspend fun setArchived(recipeId: Int, isArchived: Boolean) =
        withContext(Dispatchers.IO) {
            customRecipeDao.setArchived(recipeId, isArchived)
        }
    
    suspend fun deleteAllRecipes() =
        withContext(Dispatchers.IO) {
            customRecipeDao.deleteAllRecipes()
            customRecipeDao.deleteAllComponents()
        }
}
