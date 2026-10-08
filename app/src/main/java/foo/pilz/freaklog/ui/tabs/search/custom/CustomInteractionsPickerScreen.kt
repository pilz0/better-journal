package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomInteractionsPickerScreen(
    navigateBack: () -> Unit,
    viewModel: CustomInteractionsPickerViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val sections = viewModel.sectionsFlow.collectAsStateWithLifecycle().value
    val query = viewModel.queryFlow.collectAsStateWithLifecycle().value

    val title = "Add ${viewModel.severity.displayLabel().lowercase()} interaction"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.queryFlow.value = it },
                    label = { Text("Search") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                )
            }

            if (sections.substances.isNotEmpty()) {
                item { SectionHeader("Substances") }
                item {
                    PickerCard(
                        items = sections.substances,
                        onPick = { name ->
                            viewModel.add(name, CustomInteractionTargetType.SUBSTANCE, navigateBack)
                        },
                    )
                }
            }

            if (sections.categories.isNotEmpty()) {
                item { SectionHeader("Categories") }
                item {
                    PickerCard(
                        items = sections.categories,
                        onPick = { name ->
                            viewModel.add(name, CustomInteractionTargetType.CATEGORY, navigateBack)
                        },
                    )
                }
            }

            if (sections.common.isNotEmpty()) {
                item { SectionHeader("Common") }
                item {
                    PickerCard(
                        items = sections.common,
                        onPick = { name ->
                            viewModel.add(name, CustomInteractionTargetType.COMMON, navigateBack)
                        },
                    )
                }
            }

            if (sections.substances.isEmpty() && sections.categories.isEmpty() && sections.common.isEmpty()) {
                item {
                    Text(
                        text = "No matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerCard(
    items: List<String>,
    onPick: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    ) {
        items.forEach { item ->
            ListItem(
                headlineContent = { Text(item) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPick(item) },
            )
        }
    }
}

private fun CustomInteractionSeverity.displayLabel(): String = when (this) {
    CustomInteractionSeverity.DANGEROUS -> "Dangerous"
    CustomInteractionSeverity.UNSAFE -> "Unsafe"
    CustomInteractionSeverity.UNCERTAIN -> "Uncertain"
}
