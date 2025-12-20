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

import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.classes.SubstanceWithCategories

interface SubstanceRepositoryInterface {
    fun getAllSubstances(): List<Substance>
    fun getAllSubstancesWithCategories(): List<SubstanceWithCategories>
    fun getAllCategories(): List<Category>
    fun getSubstance(substanceName: String): Substance?
    fun getCategory(categoryName: String): Category?
    fun getSubstanceWithCategories(substanceName: String): SubstanceWithCategories?
}