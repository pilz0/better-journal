package foo.pilz.freaklog.ui.tabs.journal.addingestion.dose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.theme.horizontalPadding

@Composable
fun AlcoholCalculator(
    onApplyDose: (String) -> Unit
) {
    var volumeText by remember { mutableStateOf("") }
    var percentageText by remember { mutableStateOf("") }
    val density = 0.789

    val calculatedGrams by remember {
        derivedStateOf {
            val volume = volumeText.toDoubleOrNull()
            val percentage = percentageText.toDoubleOrNull()
            if (volume != null && percentage != null) {
                volume * (percentage / 100.0) * density
            } else {
                null
            }
        }
    }

    ElevatedCard(
        modifier = Modifier.padding(
            horizontal = horizontalPadding,
            vertical = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = horizontalPadding,
                vertical = 10.dp
            )
        ) {
            Text(
                text = "Alcohol Calculator",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val focusManager = LocalFocusManager.current

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = volumeText,
                    onValueChange = { volumeText = it.replace(',', '.') },
                    label = { Text("Volume") },
                    trailingIcon = { Text("mL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onNext = { /* Handle next focus if needed */ }),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                OutlinedTextField(
                    value = percentageText,
                    onValueChange = { percentageText = it.replace(',', '.') },
                    label = { Text("Strength") },
                    trailingIcon = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (calculatedGrams != null) {
                Text(
                    text = "= ${calculatedGrams!!.toReadableString()} g Ethanol",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Button(
                    onClick = {
                        calculatedGrams?.let {
                            onApplyDose(it.toReadableString())
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Use this dose")
                }
            }
        }
    }
}
