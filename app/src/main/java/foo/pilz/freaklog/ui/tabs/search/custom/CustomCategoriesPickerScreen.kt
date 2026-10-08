package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.substances.classes.Category
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCategoriesPickerScreen(
    navigateBack: () -> Unit,
    viewModel: CustomCategoriesPickerViewModel = hiltViewModel(),
) {
    val selected = viewModel.selectedNamesFlow.collectAsStateWithLifecycle().value
    CustomCategoriesPickerScreen(
        categories = viewModel.allCategories,
        selectedNames = selected,
        navigateBack = navigateBack,
        onToggle = viewModel::toggle,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomCategoriesPickerScreen(
    categories: List<Category>,
    selectedNames: Set<String>,
    navigateBack: () -> Unit,
    onToggle: (String) -> Unit,
) {
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
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
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    Text(
                        text = "Pick the categories this substance belongs to. " +
                                "Categories influence cross-tolerance and interaction " +
                                "checks against other substances.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.lg),
                    )
                }
            }

            val selectedList = categories.filter { selectedNames.contains(it.name) }
            val unselectedList = categories.filter { !selectedNames.contains(it.name) }

            if (selectedList.isNotEmpty()) {
                item { SectionHeader("Selected") }
                item {
                    CategoryChipsCard(
                        categories = selectedList,
                        selectedNames = selectedNames,
                        onToggle = onToggle,
                    )
                }
            }

            item { SectionHeader("Available") }
            item {
                CategoryChipsCard(
                    categories = unselectedList,
                    selectedNames = selectedNames,
                    onToggle = onToggle,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChipsCard(
    categories: List<Category>,
    selectedNames: Set<String>,
    onToggle: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            categories.forEach { category ->
                CategoryFilterChip(
                    category = category,
                    selected = selectedNames.contains(category.name),
                    onClick = { onToggle(category.name) },
                )
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = category.color.copy(alpha = 0.08f),
            selectedContainerColor = category.color.copy(alpha = 0.22f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = category.color.copy(alpha = 0.4f),
            selectedBorderColor = category.color.copy(alpha = 0.7f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        ),
    )
}

private class CategoryListProvider : PreviewParameterProvider<List<Category>> {
    override val values = sequenceOf(
        listOf(
            Category("psychedelic", "", null, androidx.compose.ui.graphics.Color(0xFF8E44AD)),
            Category("stimulant", "", null, androidx.compose.ui.graphics.Color(0xFFE67E22)),
            Category("dissociative", "", null, androidx.compose.ui.graphics.Color(0xFF3498DB)),
            Category("opioid", "", null, androidx.compose.ui.graphics.Color(0xFFC0392B)),
        ),
    )
}

@Preview
@Composable
private fun CustomCategoriesPickerPreview(
    @PreviewParameter(CategoryListProvider::class) categories: List<Category>,
) {
    CustomCategoriesPickerScreen(
        categories = categories,
        selectedNames = setOf("psychedelic"),
        navigateBack = {},
        onToggle = {},
    )
}
