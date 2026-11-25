package foo.pilz.freaklog.ui.tabs.settings.customformulations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomFormulation
import foo.pilz.freaklog.ui.components.EmptyScreenDisclaimer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFormulationsScreen(
    navigateBack: () -> Unit,
    navigateToAdd: () -> Unit,
    viewModel: CustomFormulationsViewModel = hiltViewModel(),
) {
  val formulations by viewModel.formulations.collectAsStateWithLifecycle()

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Custom formulations") },
            navigationIcon = {
              IconButton(onClick = navigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            },
        )
      },
      floatingActionButton = {
        ExtendedFloatingActionButton(
            onClick = navigateToAdd,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Formulation") },
        )
      },
  ) { padding ->
    if (formulations.isEmpty()) {
      Box(modifier = Modifier.padding(padding)) {
        EmptyScreenDisclaimer(
            title = "No custom formulations yet",
            description = "Add a formulation, e.g. extended release, to pick it when logging",
            icon = Icons.Outlined.Medication,
        )
      }
    } else {
      LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        items(formulations, key = { it.id }) { formulation ->
          CustomFormulationItem(
              formulation = formulation,
              onDelete = { viewModel.delete(formulation) },
          )
        }
      }
    }
  }
}

@Composable
fun CustomFormulationItem(
    formulation: CustomFormulation,
    onDelete: () -> Unit,
) {
  ElevatedCard(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = formulation.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${formulation.substanceName} (${formulation.baseRoa.name})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onDelete) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.error,
        )
      }
    }
  }
}
