package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomSubstanceFromAnodyneSheet(
    substance: AnodyneSubstance,
    sheetState: SheetState =
        rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String) -> Unit,
) {
  var name by remember { mutableStateOf(substance.commonName) }
  var unit by remember {
    mutableStateOf(substance.dosing.firstOrNull()?.unit ?: "mg")
  }

  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = sheetState,
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
    ) {
      Text(
          text = "Add as custom substance",
          style = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.height(8.dp))
      Text(
          text =
              "Pre-fill routes, dose tiers, and pharmacological classes from " +
                  "AnodyneWiki into your personal custom substances.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(16.dp))

      OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Substance name") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
      )
      Spacer(Modifier.height(12.dp))

      OutlinedTextField(
          value = unit,
          onValueChange = { unit = it },
          label = { Text("Default unit") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
      )
      Spacer(Modifier.height(20.dp))

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        ) {
          Text("Cancel")
        }
        Button(
            onClick = { onConfirm(name.trim(), unit.trim()) },
            modifier = Modifier.weight(1f),
            enabled = name.isNotBlank() && unit.isNotBlank(),
        ) {
          Text("Add substance")
        }
      }
    }
  }
}
