/*
 * Copyright (c) 2023. Isaak Hanimann.
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

import foo.pilz.freaklog.data.substances.classes.SubstanceWithCategories
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(val substanceRepo: SubstanceRepository) :
    SearchRepositoryInterface {

  private class IndexedSubstance(
      val item: SubstanceWithCategories,
      val name: String,
      val normalizedPrimary: String,
      val normalizedAliases: List<String>,
      val categorySet: Set<String>,
      val isCommon: Boolean,
  )

  private class SearchIndex(
      val entries: List<IndexedSubstance>,
      val byName: Map<String, IndexedSubstance>,
  )

  private val searchIndex: SearchIndex by
      lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val entries =
            substanceRepo.getAllSubstancesWithCategories().map { item ->
              IndexedSubstance(
                  item = item,
                  name = item.substance.name,
                  normalizedPrimary =
                      item.substance.name.stripHyphensAndSpaces().lowercase(Locale.ROOT),
                  normalizedAliases =
                      item.substance.commonNames.map {
                        it.stripHyphensAndSpaces().lowercase(Locale.ROOT)
                      },
                  categorySet = item.substance.categories.toHashSet(),
                  isCommon = item.categories.any { cat -> cat.name == "common" },
              )
            }
        SearchIndex(
            entries = entries,
            byName = entries.associateBy { it.name },
        )
      }

  override fun getMatchingSubstances(
      searchText: String,
      filterCategories: List<String>,
      recentlyUsedSubstanceNamesSorted: List<String>,
  ): List<SubstanceWithCategories> {
    val index = searchIndex
    val hasCategoryFilter = filterCategories.isNotEmpty()
    val query = searchText.stripHyphensAndSpaces().lowercase(Locale.ROOT)

    val matchingEntries: List<IndexedSubstance>
    if (query.isEmpty()) {
      matchingEntries =
          if (!hasCategoryFilter) {
            index.entries
          } else {
            index.entries.filter { entry -> filterCategories.all { it in entry.categorySet } }
          }
    } else {
      val mainPrefixMatches = mutableListOf<IndexedSubstance>()
      val aliasPrefixMatches = mutableListOf<IndexedSubstance>()
      val substringMatches = mutableListOf<IndexedSubstance>()

      for (entry in index.entries) {
        if (hasCategoryFilter && !filterCategories.all { it in entry.categorySet }) continue
        when {
          entry.normalizedPrimary.startsWith(query) -> mainPrefixMatches.add(entry)
          entry.normalizedAliases.any { it.startsWith(query) } -> aliasPrefixMatches.add(entry)
          entry.normalizedPrimary.contains(query) ||
              entry.normalizedAliases.any { it.contains(query) } -> substringMatches.add(entry)
        }
      }

      matchingEntries =
          ArrayList<IndexedSubstance>(
                  mainPrefixMatches.size + aliasPrefixMatches.size + substringMatches.size
              )
              .apply {
                addAll(mainPrefixMatches)
                addAll(aliasPrefixMatches)
                addAll(substringMatches)
              }
    }

    return sortMatchingEntries(
        matchingEntries = matchingEntries,
        recentlyUsedSubstanceNamesSorted = recentlyUsedSubstanceNamesSorted,
    )
  }

  private fun sortMatchingEntries(
      matchingEntries: List<IndexedSubstance>,
      recentlyUsedSubstanceNamesSorted: List<String>,
  ): List<SubstanceWithCategories> {
    if (matchingEntries.isEmpty()) return emptyList()

    val result = ArrayList<SubstanceWithCategories>(matchingEntries.size)
    val emitted = HashSet<String>(matchingEntries.size * 2)

    if (recentlyUsedSubstanceNamesSorted.isNotEmpty()) {
      val matchingByName = matchingEntries.associateBy { it.name }
      for (recentName in recentlyUsedSubstanceNamesSorted) {
        val entry = matchingByName[recentName]
        if (entry != null && emitted.add(recentName)) {
          result.add(entry.item)
        }
      }
    }

    for (entry in matchingEntries) {
      if (entry.isCommon && emitted.add(entry.name)) {
        result.add(entry.item)
      }
    }

    for (entry in matchingEntries) {
      if (emitted.add(entry.name)) {
        result.add(entry.item)
      }
    }

    return result
  }

  private fun String.stripHyphensAndSpaces(): String = filter { it != '-' && it != ' ' }
}
