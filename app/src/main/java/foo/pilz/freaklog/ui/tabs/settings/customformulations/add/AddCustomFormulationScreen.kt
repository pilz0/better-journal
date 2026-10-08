package foo.pilz.freaklog.ui.tabs.settings.customformulations.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.substances.AdministrationRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomFormulationScreen(
    navigateBack: () -> Unit,
    viewModel: AddCustomFormulationViewModel = hiltViewModel()
) {
    var substanceName by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var baseRoa by remember { mutableStateOf(AdministrationRoute.ORAL) }
    var expanded by remember { mutableStateOf(false) }

    var onsetMin by remember { mutableStateOf("") }
    var onsetMax by remember { mutableStateOf("") }
    var comeupMin by remember { mutableStateOf("") }
    var comeupMax by remember { mutableStateOf("") }
    var peakMin by remember { mutableStateOf("") }
    var peakMax by remember { mutableStateOf("") }
    var offsetMin by remember { mutableStateOf("") }
    var offsetMax by remember { mutableStateOf("") }
    var totalMin by remember { mutableStateOf("") }
    var totalMax by remember { mutableStateOf("") }
    var afterglowMin by remember { mutableStateOf("") }
    var afterglowMax by remember { mutableStateOf("") }

    val canSave = substanceName.isNotBlank() && name.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Formulation") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = substanceName,
                onValueChange = { substanceName = it },
                label = { Text("Substance Name") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = baseRoa.name,
                    onValueChange = { },
                    label = { Text("Base Route of Administration") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    AdministrationRoute.entries.forEach { route ->
                        DropdownMenuItem(
                            text = { Text(route.name) },
                            onClick = {
                                baseRoa = route
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Formulation Name (e.g. Extended Release)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Duration Overrides (Minutes, Optional)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

            DurationRow("Onset", onsetMin, onsetMax, { onsetMin = it }, { onsetMax = it })
            DurationRow("Comeup", comeupMin, comeupMax, { comeupMin = it }, { comeupMax = it })
            DurationRow("Peak", peakMin, peakMax, { peakMin = it }, { peakMax = it })
            DurationRow("Offset", offsetMin, offsetMax, { offsetMin = it }, { offsetMax = it })
            DurationRow("Total", totalMin, totalMax, { totalMin = it }, { totalMax = it })
            DurationRow("Afterglow", afterglowMin, afterglowMax, { afterglowMin = it }, { afterglowMax = it })

            Button(
                onClick = {
                    viewModel.saveFormulation(
                        substanceName = substanceName.trim(),
                        baseRoa = baseRoa,
                        name = name.trim(),
                        onsetMin = onsetMin.toIntOrNull(),
                        onsetMax = onsetMax.toIntOrNull(),
                        comeupMin = comeupMin.toIntOrNull(),
                        comeupMax = comeupMax.toIntOrNull(),
                        peakMin = peakMin.toIntOrNull(),
                        peakMax = peakMax.toIntOrNull(),
                        offsetMin = offsetMin.toIntOrNull(),
                        offsetMax = offsetMax.toIntOrNull(),
                        totalMin = totalMin.toIntOrNull(),
                        totalMax = totalMax.toIntOrNull(),
                        afterglowMin = afterglowMin.toIntOrNull(),
                        afterglowMax = afterglowMax.toIntOrNull()
                    )
                    navigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                enabled = canSave
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun DurationRow(
    label: String,
    minVal: String,
    maxVal: String,
    onMinChange: (String) -> Unit,
    onMaxChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = minVal,
            onValueChange = onMinChange,
            label = { Text("$label Min") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = maxVal,
            onValueChange = onMaxChange,
            label = { Text("$label Max") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}
