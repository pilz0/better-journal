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

package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomSubstanceAndContinueScreen(
    navigateToChooseRoa: (customSubstanceName: String) -> Unit,
    navigateBack: () -> Unit,
    initialName: String = "",
    viewModel: AddCustomSubstanceViewModel = hiltViewModel()
) {
    val name = viewModel.nameFlow.collectAsStateWithLifecycle().value
    val units = viewModel.unitsFlow.collectAsStateWithLifecycle().value
    // The view model saves edits into a draft row, so the prefill has to wait until that row exists.
    val isDraftReady = viewModel.substanceFlow.collectAsStateWithLifecycle().value != null
    LaunchedEffect(isDraftReady) {
        if (isDraftReady && name.isBlank() && initialName.isNotBlank()) {
            viewModel.onNameChange(initialName)
        }
    }
    BackHandler {
        viewModel.deleteDraft()
        navigateBack()
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Add custom substance") })
        },
        floatingActionButton = {
            if (isDraftReady && name.isNotBlank() && units.isNotBlank()) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.imePadding(),
                    onClick = {
                        navigateToChooseRoa(name)
                    },
                    icon = {
                        Icon(
                            Icons.Filled.Done,
                            contentDescription = "Done"
                        )
                    },
                    text = { Text("Done") },
                )
            }
        }
    ) { padding ->
        AddOrEditCustomSubstanceContent(
            padding = padding,
            name = name,
            units = units,
            onNameChange = viewModel::onNameChange,
            onUnitsChange = viewModel::onUnitsChange,
            category = viewModel.categoryFlow.collectAsStateWithLifecycle().value,
            onCategoryChange = viewModel::onCategoryChange,
        )
    }
}