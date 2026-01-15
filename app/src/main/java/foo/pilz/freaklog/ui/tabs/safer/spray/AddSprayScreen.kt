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

package foo.pilz.freaklog.ui.tabs.safer.spray

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.tabs.journal.experience.components.CardWithTitle
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun AddSprayScreen(
    viewModel: SprayCalculatorViewModel = hiltViewModel(),
    navigateBack: () -> Unit
) {
    AddSprayScreenContent(
        onSave = { name, contentInMl, numSprays ->
            viewModel.addSpray(name, contentInMl, numSprays)
            navigateBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSprayScreenContent(
    onSave: (name: String, contentInMl: Double, numSprays: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sizeInMl by remember { mutableStateOf("") }
    var numSprays by remember { mutableStateOf("") }

    val sizeInMlValue = sizeInMl.toDoubleOrNull()
    val numSpraysValue = numSprays.toDoubleOrNull()
    val canSave = name.isNotBlank() && 
                  sizeInMlValue != null && sizeInMlValue > 0 && 
                  numSpraysValue != null && numSpraysValue > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Spray") },
                actions = {
                    if (canSave) {
                        Button(
                            onClick = { onSave(name, sizeInMlValue!!, numSpraysValue!!) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .verticalScroll(rememberScrollState())
        ) {
            CardWithTitle(title = "Name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Spray Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CardWithTitle(title = "Size") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sizeInMl,
                        onValueChange = { sizeInMl = it },
                        label = { Text("Volume") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ml", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = numSprays,
                    onValueChange = { numSprays = it },
                    label = { Text("Number of sprays") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Note: fill it into the spray bottle and count the number of sprays. To make sure the last couple of sprays still work properly use a small spray bottle (5ml) and fill it completely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview
@Composable
fun AddSprayScreenPreview() {
    AddSprayScreenContent(onSave = { _, _, _ -> })
}
