package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteraction
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomInteractionsListScreen(
    navigateBack: () -> Unit,
    navigateToPicker: (customSubstanceId: Int, severity: CustomInteractionSeverity) -> Unit,
    viewModel: CustomInteractionsListViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val grouped = viewModel.groupedFlow.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interactions") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CustomInteractionSeverity.entries.forEach { severity ->
                item { SectionHeader(severity.displayLabel()) }
                item {
                    SeverityCard(
                        severity = severity,
                        entries = grouped[severity].orEmpty(),
                        onAdd = { navigateToPicker(viewModel.customSubstanceId, severity) },
                        onRemove = viewModel::remove,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityCard(
    severity: CustomInteractionSeverity,
    entries: List<CustomInteraction>,
    onAdd: () -> Unit,
    onRemove: (CustomInteraction) -> Unit,
) {
    val spacing = LocalSpacing.current
    val accent = severity.toInteractionType().color

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "No ${severity.displayLabel().lowercase()} interactions yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(spacing.lg),
            )
        } else {
            entries.forEach { entry ->
                ListItem(
                    leadingContent = { AccentBar(color = accent) },
                    headlineContent = { Text(entry.targetName) },
                    supportingContent = { Text(entry.targetType.displayLabel()) },
                    trailingContent = {
                        IconButton(onClick = { onRemove(entry) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                text = "Add ${severity.displayLabel().lowercase()} interaction",
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
    }
}

@Composable
private fun AccentBar(color: Color) {
    Surface(
        color = color,
        modifier = Modifier
            .size(width = 4.dp, height = 32.dp)
            .clip(RoundedCornerShape(2.dp)),
    ) {}
}

private fun CustomInteractionSeverity.displayLabel(): String = when (this) {
    CustomInteractionSeverity.DANGEROUS -> "Dangerous"
    CustomInteractionSeverity.UNSAFE -> "Unsafe"
    CustomInteractionSeverity.UNCERTAIN -> "Uncertain"
}

private fun CustomInteractionSeverity.toInteractionType(): InteractionType = when (this) {
    CustomInteractionSeverity.DANGEROUS -> InteractionType.DANGEROUS
    CustomInteractionSeverity.UNSAFE -> InteractionType.UNSAFE
    CustomInteractionSeverity.UNCERTAIN -> InteractionType.UNCERTAIN
}

private fun foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType.displayLabel(): String =
    when (this) {
        foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType.SUBSTANCE -> "Substance"
        foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType.CATEGORY -> "Category"
        foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType.COMMON -> "Common"
    }
