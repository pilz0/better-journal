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
import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.SubstanceFile
import foo.pilz.freaklog.data.substances.classes.SubstanceWithCategories
import foo.pilz.freaklog.data.substances.parse.SubstanceParserInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubstanceRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    substanceParser: SubstanceParserInterface,
) : SubstanceRepositoryInterface {

    private val substanceFile: SubstanceFile

    init {
        val fileContent = getAssetsSubstanceFileContent()
        substanceFile = substanceParser.parseSubstanceFile(string = fileContent)
    }

    private val categoriesMap: Map<String, Category> = substanceFile.categories.associateBy { it.name }
    private val substancesWithCategoriesList: List<SubstanceWithCategories> = substanceFile.substances.map { substance ->
        SubstanceWithCategories(
            substance = substance,
            categories = substanceFile.categories.filter { it.name in substance.categories }
        )
    }
    private val substancesWithCategoriesMap: Map<String, SubstanceWithCategories> =
        substancesWithCategoriesList.associateBy { it.substance.name }

    private fun getAssetsSubstanceFileContent(): String {
        return appContext.assets.open("Substances.json").bufferedReader().use { it.readText() }
    }

    override fun getAllSubstances(): List<Substance> {
        return substanceFile.substances
    }

    override fun getAllSubstancesWithCategories(): List<SubstanceWithCategories> {
        return substancesWithCategoriesList
    }

    override fun getAllCategories(): List<Category> {
        return substanceFile.categories
    }

    override fun getSubstance(substanceName: String): Substance? {
        return substanceFile.substancesMap.getOrDefault(key = substanceName, defaultValue = null)
    }

    override fun getCategory(categoryName: String): Category? {
        return categoriesMap[categoryName]
    }

    override fun getSubstanceWithCategories(substanceName: String): SubstanceWithCategories? {
        return substancesWithCategoriesMap[substanceName]
    }
}
