package foo.pilz.freaklog.ui.tabs.safer.tolerance

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.theme.horizontalPadding
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToleranceScreen(
    navigateBack: () -> Unit,
    viewModel: ToleranceViewModel = hiltViewModel()
) {
    val recentTolerances = viewModel.recentTolerances.collectAsState().value
    val searchedTolerance = viewModel.searchedTolerance.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tolerance") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SearchSection(
                    searchText = viewModel.searchText,
                    onSearchTextChange = viewModel::onSearchTextChange,
                    suggestions = viewModel.searchSuggestions,
                    onSelectSubstance = viewModel::onSelectSubstance
                )
            }

            if (searchedTolerance != null) {
                item {
                    ToleranceDetailCard(
                        estimate = searchedTolerance,
                        modifier = Modifier.padding(horizontal = horizontalPadding)
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (recentTolerances.isNotEmpty() && searchedTolerance == null) {
                item {
                    Text(
                        text = "Recently used substances",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            .padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                items(recentTolerances) { estimate ->
                    ToleranceSummaryCard(
                        estimate = estimate,
                        modifier = Modifier.padding(horizontal = horizontalPadding)
                    )
                }
            } else if (searchedTolerance == null) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "No recent ingestions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "Use the search above to check tolerance for any substance.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Estimate based on PsychonautWiki data. Individual tolerance varies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchSection(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    suggestions: List<String>,
    onSelectSubstance: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var expanded by remember(suggestions) { mutableStateOf(suggestions.isNotEmpty()) }

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 3.dp
        ) {
            TextField(
                value = searchText,
                onValueChange = {
                    onSearchTextChange(it)
                    expanded = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { onSearchTextChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Search substance") },
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions.Default.copy(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                    capitalization = KeyboardCapitalization.Words,
                ),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
        }

        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelectSubstance(name)
                    }
                )
            }
        }
    }
}

@Composable
private fun ToleranceSummaryCard(
    estimate: ToleranceEstimate,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = estimate.substanceName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${estimate.percentage}% — ${estimate.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { estimate.toleranceLevel },
                modifier = Modifier.fillMaxWidth()
            )
            if (estimate.lastIngestionTime != null) {
                Text(
                    text = "Last use: ${formatTimeSince(estimate.lastIngestionTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToleranceDetailCard(
    estimate: ToleranceEstimate,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = estimate.substanceName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${estimate.percentage}% — ${estimate.label}",
                style = MaterialTheme.typography.bodyLarge
            )
            LinearProgressIndicator(
                progress = { estimate.toleranceLevel },
                modifier = Modifier.fillMaxWidth()
            )
            if (estimate.lastIngestionTime != null) {
                Text(
                    text = "Last use: ${formatTimeSince(estimate.lastIngestionTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (estimate.crossToleranceContributors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Cross-tolerance from: ${estimate.crossToleranceContributors.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimeSince(instant: Instant): String {
    val duration = Duration.between(instant, Instant.now())
    val days = duration.toDays()
    val hours = duration.toHours()
    return when {
        days > 0 -> "$days day${if (days != 1L) "s" else ""} ago"
        hours > 0 -> "$hours hour${if (hours != 1L) "s" else ""} ago"
        else -> "just now"
    }
}
