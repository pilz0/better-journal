package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.rememberHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnodyneUserNoteScreen(
    user: String,
    substanceName: String,
    navigateBack: () -> Unit,
    viewModel: AnodyneUserNotesViewModel = hiltViewModel(),
) {
  val performHaptic = rememberHaptic()
  val notes by viewModel.notes.collectAsState()
  val note: AnodyneUserNote? = notes.firstOrNull {
    it.user.equals(user, ignoreCase = true) &&
        (substanceName.isBlank() || it.substanceName.equals(substanceName, ignoreCase = true))
  }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("$user's Note") },
            navigationIcon = {
              IconButton(
                  onClick = {
                    performHaptic(HapticType.CLICK)
                    navigateBack()
                  }
              ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
              }
            },
        )
      },
  ) { padding ->
    if (note == null) {
      Box(
          modifier = Modifier.padding(padding).fillMaxSize(),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = "Note for '$user' not found.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      NoteDetailContent(padding = padding, note = note)
    }
  }
}

@Composable
private fun NoteDetailContent(
    padding: PaddingValues,
    note: AnodyneUserNote,
) {
  Column(
      modifier =
          Modifier.padding(padding)
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = horizontalPadding, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    NoteHeaderCard(note)
    if (note.routes.isNotEmpty()) {
      NoteRoutesCard(note)
    }
    if (note.effects.isNotEmpty()) {
      NoteEffectsCard(note.effects)
    }
    if (note.notes.isNotEmpty()) {
      NoteNotesCard(note.notes)
    }
  }
}

@Composable
private fun NoteHeaderCard(note: AnodyneUserNote) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = note.substanceName,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = "Logged by: ${note.user}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (note.sources.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Source: ${note.sources.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun NoteRoutesCard(note: AnodyneUserNote) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = "Routes & Timings",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(8.dp))
      note.routes.forEachIndexed { index, route ->
        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = route.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        route.dosage?.let { KeyValueRow("Dose", it) }
        route.onset?.let { KeyValueRow("Onset", it) }
        route.comeup?.let { KeyValueRow("Comeup", it) }
        route.duration?.let { KeyValueRow("Duration", it) }
      }
    }
  }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
private fun NoteEffectsCard(effects: List<String>) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = "Reported Effects",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(4.dp))
      Text(
          text = effects.joinToString(", "),
          style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun NoteNotesCard(notes: List<String>) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = "Experience Notes",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.height(8.dp))
      notes.forEachIndexed { i, n ->
        if (i > 0) Spacer(Modifier.height(8.dp))
        Text(
            text = n,
            style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}
