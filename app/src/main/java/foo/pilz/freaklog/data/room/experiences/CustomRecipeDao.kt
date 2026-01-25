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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipe
import foo.pilz.freaklog.data.room.experiences.entities.CustomRecipeComponent
import foo.pilz.freaklog.data.room.experiences.relations.CustomRecipeWithComponents
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface CustomRecipeDao {
    
    @Query("SELECT * FROM CustomRecipe WHERE isArchived = 0 ORDER BY CASE WHEN lastUsedDate IS NULL THEN 1 ELSE 0 END, lastUsedDate DESC, creationDate DESC")
    fun getActiveRecipesFlow(): Flow<List<CustomRecipe>>
    
    @Query("SELECT * FROM CustomRecipe WHERE isArchived = 1 ORDER BY creationDate DESC")
    fun getArchivedRecipesFlow(): Flow<List<CustomRecipe>>
    
    @Transaction
    @Query("SELECT * FROM CustomRecipe WHERE id = :recipeId")
    suspend fun getRecipeWithComponents(recipeId: Int): CustomRecipeWithComponents?
    
    @Transaction
    @Query("SELECT * FROM CustomRecipe WHERE isArchived = 0 ORDER BY CASE WHEN lastUsedDate IS NULL THEN 1 ELSE 0 END, lastUsedDate DESC, creationDate DESC")
    fun getActiveRecipesWithComponentsFlow(): Flow<List<CustomRecipeWithComponents>>
    
    @Transaction
    @Query("SELECT * FROM CustomRecipe WHERE isArchived = 1 ORDER BY creationDate DESC")
    fun getArchivedRecipesWithComponentsFlow(): Flow<List<CustomRecipeWithComponents>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: CustomRecipe): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponent(component: CustomRecipeComponent): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponents(components: List<CustomRecipeComponent>)
    
    @Update
    suspend fun updateRecipe(recipe: CustomRecipe)
    
    @Update
    suspend fun updateComponent(component: CustomRecipeComponent)
    
    @Delete
    suspend fun deleteRecipe(recipe: CustomRecipe)
    
    @Delete
    suspend fun deleteComponent(component: CustomRecipeComponent)
    
    @Query("DELETE FROM CustomRecipeComponent WHERE customRecipeId = :recipeId")
    suspend fun deleteComponentsForRecipe(recipeId: Int)
    
    @Query("UPDATE CustomRecipe SET lastUsedDate = :timestamp WHERE id = :recipeId")
    suspend fun updateLastUsedDate(recipeId: Int, timestamp: Instant)
    
    @Query("UPDATE CustomRecipe SET isArchived = :isArchived WHERE id = :recipeId")
    suspend fun setArchived(recipeId: Int, isArchived: Boolean)
    
    @Transaction
    suspend fun insertRecipeWithComponents(recipe: CustomRecipe, components: List<CustomRecipeComponent>): Long {
        val recipeId = insertRecipe(recipe)
        val componentsWithRecipeId = components.map { it.copy(customRecipeId = recipeId.toInt()) }
        insertComponents(componentsWithRecipeId)
        return recipeId
    }
    
    @Transaction
    suspend fun updateRecipeWithComponents(recipe: CustomRecipe, components: List<CustomRecipeComponent>) {
        updateRecipe(recipe)
        deleteComponentsForRecipe(recipe.id)
        insertComponents(components)
    }
    
    @Transaction
    suspend fun deleteRecipeWithComponents(recipeId: Int) {
        deleteComponentsForRecipe(recipeId)
        val recipe = getRecipeWithComponents(recipeId)?.recipe
        if (recipe != null) {
            deleteRecipe(recipe)
        }
    }
    
    @Query("DELETE FROM CustomRecipe")
    suspend fun deleteAllRecipes()
    
    @Query("DELETE FROM CustomRecipeComponent")
    suspend fun deleteAllComponents()
}
