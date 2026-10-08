package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import foo.pilz.freaklog.ui.components.EmptyScreenDisclaimer
import foo.pilz.freaklog.ui.theme.LocalSpacing
import java.time.Instant

@Composable
fun IntakeLimitsScreen(
    viewModel: IntakeLimitsViewModel = hiltViewModel(),
    navigateToAddIntakeLimit: () -> Unit,
    navigateToEditIntakeLimit: (limitId: Int) -> Unit,
) {
    IntakeLimitsScreenContent(
        items = viewModel.itemsFlow.collectAsStateWithLifecycle().value,
        navigateToAddIntakeLimit = navigateToAddIntakeLimit,
        navigateToEditIntakeLimit = navigateToEditIntakeLimit,
    )
}

@Preview
@Composable
fun IntakeLimitsScreenPreview() {
    val now = Instant.now()
    IntakeLimitsScreenContent(
        items = listOf(
            IntakeLimit.caffeineSample.copy(id = 1),
            IntakeLimit.nicotineSample.copy(id = 2),
        ).map { limit ->
            val pastIngestions = when (limit.limitType) {
                IntakeLimitType.DOSE -> listOf(
                    LimitIngestion(250.0, "mg", now.minusSeconds(3_600)),
                    LimitIngestion(250.0, "mg", now.minusSeconds(7_200)),
                )
                IntakeLimitType.COUNT -> listOf(
                    LimitIngestion(1.0, null, now.minusSeconds(86_400)),
                    LimitIngestion(1.0, null, now.minusSeconds(2 * 86_400)),
                )
            }
            IntakeLimitListItem(limit, evaluateIntakeLimit(limit, now, pastIngestions, pending = null))
        },
        navigateToAddIntakeLimit = {},
        navigateToEditIntakeLimit = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeLimitsScreenContent(
    items: List<IntakeLimitListItem>,
    navigateToAddIntakeLimit: () -> Unit,
    navigateToEditIntakeLimit: (limitId: Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("Intake limits") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.imePadding(),
                onClick = navigateToAddIntakeLimit,
                icon = { Icon(Icons.Default.Add, contentDescription = "Add intake limit") },
                text = { Text(text = "Limit") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (items.isEmpty()) {
                EmptyScreenDisclaimer(
                    title = "No intake limits yet",
                    description = "Add a limit to get warned when you approach it",
                    icon = Icons.Outlined.Speed
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    items(items, key = { it.limit.id }) { item ->
                        IntakeLimitCard(
                            item = item,
                            onClick = { navigateToEditIntakeLimit(item.limit.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntakeLimitCard(
    item: IntakeLimitListItem,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Text(
                text = item.limit.substanceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.limit.summaryDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.limit.isEnabled) {
                Text(
                    text = item.status.listUsageLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = item.status.severityColor()
                )
                item.status.resetInfo()?.let { reset ->
                    Text(
                        text = reset,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
