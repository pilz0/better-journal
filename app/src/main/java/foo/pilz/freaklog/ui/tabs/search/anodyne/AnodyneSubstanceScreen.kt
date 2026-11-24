package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.rememberHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnodyneSubstanceScreen(
    navigateBack: () -> Unit,
    navigateToPsychonautSubstance: (String) -> Unit,
    navigateToCustomSubstance: (Int) -> Unit,
    navigateToUserNotes: (String) -> Unit,
    viewModel: AnodyneSubstanceViewModel = hiltViewModel(),
) {
  val phase by viewModel.phase.collectAsState()
  val performHaptic = rememberHaptic()
  var showAddSheet by remember { mutableStateOf(false) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(viewModel.title.ifBlank { "AnodyneWiki" }) },
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
              val currentPhase = phase
              if (currentPhase is AnodyneSubstancePhase.Found && currentPhase.localMatch == null) {
                IconButton(
                    onClick = {
                      performHaptic(HapticType.CLICK)
                      showAddSheet = true
                    }
                ) {
                  Icon(Icons.Outlined.Add, contentDescription = "Add as custom substance")
                }
              }
              IconButton(
                  onClick = {
                    performHaptic(HapticType.CLICK)
                    viewModel.load()
                  }
              ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reload")
              }
            },
        )
      },
  ) { padding ->
    when (val p = phase) {
      is AnodyneSubstancePhase.Loading -> AnodyneSubstanceLoadingContent(padding)
      is AnodyneSubstancePhase.Failed ->
          AnodyneSubstanceFailedContent(padding, p.error, viewModel::load)
      is AnodyneSubstancePhase.NotFound -> AnodyneSubstanceNotFoundContent(padding)
      is AnodyneSubstancePhase.Found -> {
        AnodyneSubstanceFoundContent(
            padding = padding,
            sub = p.substance,
            localMatch = p.localMatch,
            userNotes = p.userNotes,
            navigateToPsychonautSubstance = navigateToPsychonautSubstance,
            navigateToCustomSubstance = navigateToCustomSubstance,
            navigateToUserNotes = navigateToUserNotes,
        )
        if (showAddSheet) {
          AddCustomSubstanceFromAnodyneSheet(
              substance = p.substance,
              onDismiss = { showAddSheet = false },
              onConfirm = { name, unit ->
                showAddSheet = false
                viewModel.addAsCustomSubstance(name, unit) { newId ->
                  performHaptic(HapticType.SUCCESS)
                  navigateToCustomSubstance(newId)
                }
              },
          )
        }
      }
    }
  }
}

@Composable
private fun AnodyneSubstanceLoadingContent(padding: PaddingValues) {
  Box(
      modifier = Modifier.padding(padding).fillMaxSize(),
      contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator()
  }
}

@Composable
private fun AnodyneSubstanceFailedContent(
    padding: PaddingValues,
    error: String,
    onRetry: () -> Unit,
) {
  Box(
      modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
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

@Composable
private fun AnodyneSubstanceNotFoundContent(padding: PaddingValues) {
  Box(
      modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = "Substance not found on AnodyneWiki.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun AnodyneSubstanceFoundContent(
    padding: PaddingValues,
    sub: AnodyneSubstance,
    localMatch: LocalSubstanceMatch?,
    userNotes: List<AnodyneUserNote>,
    navigateToPsychonautSubstance: (String) -> Unit,
    navigateToCustomSubstance: (Int) -> Unit,
    navigateToUserNotes: (String) -> Unit,
) {
  Column(
      modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
  ) {
    localMatch?.let { match ->
      FreakLogMatchCard(
          match = match,
          navigateToPsychonautSubstance = navigateToPsychonautSubstance,
          navigateToCustomSubstance = navigateToCustomSubstance,
      )
    }

    AnodyneOverviewSection(sub)
    AnodyneEffectsSection(sub.effectGroups)

    if (sub.dosing.isNotEmpty()) {
      AnodyneCard("Logged Doses") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          sub.dosing.forEach { dosing ->
            AnodyneDoseLadder(dosing)
          }
        }
      }
    }

    AnodyneDurationSection(sub)

    if (
        userNotes.isNotEmpty() || sub.erowidReports.isNotEmpty() || sub.redditReports.isNotEmpty()
    ) {
      ExperienceReportsSection(
          sub = sub,
          userNotes = userNotes,
          navigateToUserNotes = navigateToUserNotes,
      )
    }

    AnodyneInteractionsSection(sub.interactions)
    AnodyneToxicitySection(sub.reportedFatalDose, sub.treatment)
    AnodyneHealthWarningsSection(sub.healthEffects, sub.drugWarnings)
    AnodyneReagentsSection(sub.reagents)
    AnodyneLegalSection(sub.scheduling)
    AnodynePharmacologySection(sub.mechanismOfAction, sub.metabolism)
    AnodyneSourcesSection(sub.title, sub.references)
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun FreakLogMatchCard(
    match: LocalSubstanceMatch,
    navigateToPsychonautSubstance: (String) -> Unit,
    navigateToCustomSubstance: (Int) -> Unit,
) {
  val performHaptic = rememberHaptic()
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = horizontalPadding, vertical = 8.dp)
              .clickable {
                performHaptic(HapticType.CLICK)
                when (match) {
                  is LocalSubstanceMatch.Psychonaut -> navigateToPsychonautSubstance(match.name)
                  is LocalSubstanceMatch.Custom -> navigateToCustomSubstance(match.id)
                }
              },
      colors =
          CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
          ),
      shape = RoundedCornerShape(12.dp),
  ) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.Outlined.Check,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = "Present in FreakLog",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Tap to view with your personal logs and timings.",
            style = MaterialTheme.typography.bodySmall,
        )
      }
      Icon(
          Icons.AutoMirrored.Outlined.ArrowForward,
          contentDescription = "Open",
          tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun ExperienceReportsSection(
    sub: AnodyneSubstance,
    userNotes: List<AnodyneUserNote>,
    navigateToUserNotes: (String) -> Unit,
) {
  val performHaptic = rememberHaptic()
  AnodyneCard("Experience Reports") {
    if (userNotes.isNotEmpty()) {
      Card(
          modifier =
              Modifier.fillMaxWidth().clickable {
                performHaptic(HapticType.CLICK)
                navigateToUserNotes(sub.title)
              },
          colors =
              CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceVariant,
              ),
          shape = RoundedCornerShape(8.dp),
      ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
              text = "${userNotes.size} Wiki User Notes",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
          )
          Icon(
              Icons.AutoMirrored.Outlined.ArrowForward,
              contentDescription = "View notes",
          )
        }
      }
      Spacer(Modifier.height(8.dp))
    }

    if (sub.erowidReports.isNotEmpty()) {
      Text(
          text = "${sub.erowidReports.size} Erowid Experience Reports",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (sub.redditReports.isNotEmpty()) {
      Text(
          text = "${sub.redditReports.size} Reddit Experience Reports",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
