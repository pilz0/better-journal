package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import foo.pilz.freaklog.data.anodyne.AnodyneTextCleaner
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.rememberHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnodyneUserNotesScreen(
    viewModel: AnodyneUserNotesViewModel = hiltViewModel(),
    navigateBack: () -> Unit,
    navigateToNoteDetail: (user: String, substanceName: String) -> Unit,
) {
  val notes by viewModel.notes.collectAsState()
  val isLoading by viewModel.isLoading.collectAsState()
  val error by viewModel.error.collectAsState()
  val performHaptic = rememberHaptic()

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("Notes: ${viewModel.substanceName}") },
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
            actions = {
              IconButton(
                  onClick = {
                    performHaptic(HapticType.CLICK)
                    viewModel.loadNotes()
                  }
              ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
              }
            },
        )
      },
  ) { padding ->
    UserNotesContent(
        modifier = Modifier.padding(padding).fillMaxSize(),
        isLoading = isLoading,
        error = error,
        notes = notes,
        substanceName = viewModel.substanceName,
        onRetry = viewModel::loadNotes,
        onNoteClick = { note ->
          performHaptic(HapticType.SELECTION)
          navigateToNoteDetail(note.user, note.substanceName)
        },
    )
  }
}

@Composable
private fun UserNotesContent(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    error: String?,
    notes: List<AnodyneUserNote>,
    substanceName: String,
    onRetry: () -> Unit,
    onNoteClick: (AnodyneUserNote) -> Unit,
) {
  Box(modifier = modifier) {
    when {
      isLoading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      error != null -> {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
              Text("Retry")
            }
          }
        }
      }
      notes.isEmpty() -> {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = "No user experience notes found for '$substanceName'.",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      else -> {
        LazyColumn(
            modifier =
                Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(notes, key = { "${it.user}_${it.substanceName}" }) { note ->
            UserNoteCard(
                note = note,
                onClick = { onNoteClick(note) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun UserNoteCard(
    note: AnodyneUserNote,
    onClick: () -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
      shape = RoundedCornerShape(12.dp),
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = "User: ${note.user}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
      )

      if (note.routes.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        note.routes.forEach { route ->
          val parts =
              listOfNotNull(
                  route.dosage?.takeIf { it.isNotBlank() },
                  route.onset?.let { "onset: $it" },
                  route.duration?.let { "duration: $it" },
              )
          Text(
              text = "${route.name}: ${parts.joinToString(", ")}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (note.notes.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = note.notes.joinToString("\n\n", transform = AnodyneTextCleaner::clean),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
        )
      }
    }
  }
}
