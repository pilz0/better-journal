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

package foo.pilz.freaklog.ui.tabs.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomSubstance
import foo.pilz.freaklog.ui.tabs.search.substancerow.SubstanceRow
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.rememberHaptic

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = hiltViewModel(),
    onSubstanceTap: (substanceModel: SubstanceModel) -> Unit,
    onCustomSubstanceTap: (customSubstanceId: Int) -> Unit,
    navigateToAddCustomSubstanceScreen: () -> Unit,
    navigateToAnodyneSearch: (query: String) -> Unit = {},
) {
  val focusRequester = remember { FocusRequester() }
  var isFocused by remember { mutableStateOf(false) }
  val performHaptic = rememberHaptic()

  Scaffold(
      floatingActionButton = {
        if (!isFocused) {
          FloatingActionButton(
              onClick = {
                performHaptic(HapticType.CLICK)
                focusRequester.requestFocus()
              },
          ) {
            Icon(Icons.Default.Keyboard, contentDescription = "Keyboard")
          }
        }
      },
  ) { padding ->
    SearchBodyContent(
        modifier = Modifier.padding(padding),
        searchViewModel = searchViewModel,
        focusRequester = focusRequester,
        onFocusChanged = { isFocused = it },
        onSubstanceTap = onSubstanceTap,
        onCustomSubstanceTap = onCustomSubstanceTap,
        navigateToAddCustomSubstanceScreen = navigateToAddCustomSubstanceScreen,
        navigateToAnodyneSearch = navigateToAnodyneSearch,
    )
  }
}

@Suppress("LongParameterList")
@Composable
private fun SearchBodyContent(
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSubstanceTap: (SubstanceModel) -> Unit,
    onCustomSubstanceTap: (Int) -> Unit,
    navigateToAddCustomSubstanceScreen: () -> Unit,
    navigateToAnodyneSearch: (String) -> Unit,
) {
  val performHaptic = rememberHaptic()
  Column(modifier = modifier) {
    val searchText = searchViewModel.searchTextFlow.collectAsStateWithLifecycle().value
    val chipCategories = searchViewModel.chipCategoriesFlow.collectAsStateWithLifecycle().value
    SearchField(
        modifier =
            Modifier.fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState -> onFocusChanged(focusState.isFocused) }
                .clearAndSetSemantics {},
        searchText = searchText,
        onChange = { searchViewModel.filterSubstances(searchText = it) },
        categories = chipCategories,
        onFilterTapped = {
          performHaptic(HapticType.SELECTION)
          searchViewModel.onFilterTapped(it)
        },
        isShowingFilter = true,
    )
    val activeFilters = remember(chipCategories) { chipCategories.filter { it.isActive } }
    ActiveFiltersRow(
        activeFilters = activeFilters,
        onFilterTapped = { chipName ->
          performHaptic(HapticType.SELECTION)
          searchViewModel.onFilterTapped(chipName)
        },
    )
    val filteredSubstances =
        searchViewModel.filteredSubstancesFlow.collectAsStateWithLifecycle().value
    val filteredCustomSubstances =
        searchViewModel.filteredCustomSubstancesFlow.collectAsStateWithLifecycle().value
    if (filteredSubstances.isEmpty() && filteredCustomSubstances.isEmpty()) {
      val activeCategoryNames = remember(activeFilters) { activeFilters.map { it.chipName } }
      SearchEmptyResults(
          activeCategoryNames = activeCategoryNames,
          searchText = searchText,
          onAddCustomSubstance = {
            performHaptic(HapticType.CLICK)
            navigateToAddCustomSubstanceScreen()
          },
          onSearchAnodyne = {
            performHaptic(HapticType.CLICK)
            navigateToAnodyneSearch(it)
          },
      )
    } else {
      SearchResultsList(
          filteredSubstances = filteredSubstances,
          filteredCustomSubstances = filteredCustomSubstances,
          searchText = searchText,
          onSubstanceTap = {
            performHaptic(HapticType.SELECTION)
            onSubstanceTap(it)
          },
          onCustomSubstanceTap = {
            performHaptic(HapticType.SELECTION)
            onCustomSubstanceTap(it)
          },
          onAddCustomSubstance = {
            performHaptic(HapticType.CLICK)
            navigateToAddCustomSubstanceScreen()
          },
          onSearchAnodyne = {
            performHaptic(HapticType.CLICK)
            navigateToAnodyneSearch(it)
          },
      )
    }
  }
}

@Composable
private fun ActiveFiltersRow(
    activeFilters: List<CategoryChipModel>,
    onFilterTapped: (String) -> Unit,
) {
  if (activeFilters.isEmpty()) return
  LazyRow(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      modifier = Modifier.padding(vertical = 6.dp),
  ) {
    item(key = "start_spacer") {
      Spacer(modifier = Modifier.width(4.dp))
    }
    items(activeFilters, key = { it.chipName }) { categoryChipModel ->
      CategoryChipDelete(categoryChipModel = categoryChipModel) {
        onFilterTapped(categoryChipModel.chipName)
      }
    }
    item(key = "end_spacer") {
      Spacer(modifier = Modifier.width(4.dp))
    }
  }
}

@Composable
private fun SearchEmptyResults(
    activeCategoryNames: List<String>,
    searchText: String,
    onAddCustomSubstance: () -> Unit,
    onSearchAnodyne: (String) -> Unit,
) {
  Column {
    if (activeCategoryNames.isEmpty()) {
      Text("No matching substance found", modifier = Modifier.padding(10.dp))
    } else if (activeCategoryNames.size == 1) {
      Text(
          "No matching substance with the tag '${activeCategoryNames[0]}' found",
          modifier = Modifier.padding(10.dp),
      )
    } else {
      val names = activeCategoryNames.joinToString(separator = "', '")
      Text(
          "No matching substance with tags '$names' found",
          modifier = Modifier.padding(10.dp),
      )
    }
    TextButton(
        onClick = onAddCustomSubstance,
        modifier = Modifier.padding(horizontal = horizontalPadding),
    ) {
      Icon(Icons.Outlined.Add, contentDescription = "Add")
      Spacer(Modifier.size(ButtonDefaults.IconSpacing))
      Text(text = "Add custom substance")
    }
    TextButton(
        onClick = { onSearchAnodyne(searchText) },
        modifier = Modifier.padding(horizontal = horizontalPadding),
    ) {
      Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "AnodyneWiki")
      Spacer(Modifier.size(ButtonDefaults.IconSpacing))
      Text(text = "Search AnodyneWiki")
    }
  }
}

@Composable
private fun SearchResultsList(
    filteredSubstances: List<SubstanceModel>,
    filteredCustomSubstances: List<CustomSubstance>,
    searchText: String,
    onSubstanceTap: (SubstanceModel) -> Unit,
    onCustomSubstanceTap: (Int) -> Unit,
    onAddCustomSubstance: () -> Unit,
    onSearchAnodyne: (String) -> Unit,
) {
  LazyColumn {
    items(filteredCustomSubstances, key = { "custom_${it.id}" }) { customSubstance ->
      val substanceModel =
          remember(customSubstance.id, customSubstance.name) {
            SubstanceModel(
                name = customSubstance.name,
                commonNames = emptyList(),
                categories =
                    listOf(
                        CategoryModel(
                            name = "custom",
                            color = customColor,
                        ),
                    ),
                hasSaferUse = false,
                hasInteractions = false,
            )
          }
      SubstanceRow(
          substanceModel = substanceModel,
          onTap = { onCustomSubstanceTap(customSubstance.id) },
      )
      HorizontalDivider()
    }
    items(filteredSubstances, key = { it.name }) { substance ->
      SubstanceRow(
          substanceModel = substance,
          onTap = { onSubstanceTap(substance) },
      )
      HorizontalDivider()
    }

    item(key = "add_custom_substance") {
      val addCustomSubstanceText = "Add custom substance"
      TextButton(
          onClick = onAddCustomSubstance,
          modifier =
              Modifier.padding(horizontal = horizontalPadding).semantics {
                contentDescription = addCustomSubstanceText
              },
      ) {
        Icon(Icons.Outlined.Add, contentDescription = "Add")
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = addCustomSubstanceText)
      }
      TextButton(
          onClick = { onSearchAnodyne(searchText) },
          modifier = Modifier.padding(horizontal = horizontalPadding),
      ) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "AnodyneWiki")
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text = "Search AnodyneWiki")
      }
    }
  }
}
