/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package foo.pilz.freaklog.data.substances.repositories

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.SubstanceFile
import foo.pilz.freaklog.data.substances.classes.SubstanceWithCategories
import foo.pilz.freaklog.data.substances.parse.SubstanceParserInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubstanceRepository
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    private val substanceParser: SubstanceParserInterface,
) : SubstanceRepositoryInterface {

  private class LoadedData(
      val substanceFile: SubstanceFile,
      val categoriesMap: Map<String, Category>,
      val substancesWithCategoriesList: List<SubstanceWithCategories>,
      val substancesWithCategoriesMap: Map<String, SubstanceWithCategories>,
  )

  private val loaded: LoadedData by
      lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val fileContent = getAssetsSubstanceFileContent()
        val substanceFile = substanceParser.parseSubstanceFile(string = fileContent)
        val categoriesMap = substanceFile.categories.associateBy { it.name }
        val substancesWithCategoriesList =
            substanceFile.substances.map { substance ->
              SubstanceWithCategories(
                  substance = substance,
                  categories = substance.categories.mapNotNull { categoriesMap[it] },
              )
            }
        val substancesWithCategoriesMap = substancesWithCategoriesList.associateBy {
          it.substance.name
        }
        LoadedData(
            substanceFile = substanceFile,
            categoriesMap = categoriesMap,
            substancesWithCategoriesList = substancesWithCategoriesList,
            substancesWithCategoriesMap = substancesWithCategoriesMap,
        )
      }

  /** Pre-warms the parsed substance cache; safe to call from a background dispatcher. */
  fun preload() {
    loaded
  }

  private fun getAssetsSubstanceFileContent(): String {
    return appContext.assets.open("Substances.json").bufferedReader().use { it.readText() }
  }

  override fun getAllSubstances(): List<Substance> {
    return loaded.substanceFile.substances
  }

  override fun getAllSubstancesWithCategories(): List<SubstanceWithCategories> {
    return loaded.substancesWithCategoriesList
  }

  override fun getAllCategories(): List<Category> {
    return loaded.substanceFile.categories
  }

  override fun getSubstance(substanceName: String): Substance? {
    return loaded.substanceFile.substancesMap.getOrDefault(key = substanceName, defaultValue = null)
  }

  override fun getCategory(categoryName: String): Category? {
    return loaded.categoriesMap[categoryName]
  }

  override fun getSubstanceWithCategories(substanceName: String): SubstanceWithCategories? {
    return loaded.substancesWithCategoriesMap[substanceName]
  }
}
