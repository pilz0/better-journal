package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.IngestionCategoryPicker
import foo.pilz.freaklog.ui.theme.LocalSpacing

@Composable
fun CustomSubstanceEditorContent(
    padding: PaddingValues,
    name: String,
    onNameChange: (String) -> Unit,
    units: String,
    onUnitsChange: (String) -> Unit,
    summary: String,
    onSummaryChange: (String) -> Unit,
    category: IngestionCategory?,
    onCategoryChange: (IngestionCategory?) -> Unit,
    onNavigateToRoutes: (() -> Unit)? = null,
    routesCount: Int = 0,
    onNavigateToCategories: (() -> Unit)? = null,
    categoriesCount: Int = 0,
    onNavigateToInteractions: (() -> Unit)? = null,
    interactionsCount: Int = 0,
    onNavigateToTolerance: (() -> Unit)? = null,
    toleranceFilled: Boolean = false,
    onNavigateToRisks: (() -> Unit)? = null,
    risksFilled: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .padding(padding)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        SectionHeader("Basics")
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal),
        ) {
            Column(
                modifier = Modifier.padding(spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = units,
                    onValueChange = onUnitsChange,
                    label = { Text("Units") },
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = { onUnitsChange("µg") }) { Text("µg") }
                    OutlinedButton(onClick = { onUnitsChange("mg") }) { Text("mg") }
                    OutlinedButton(onClick = { onUnitsChange("g") }) { Text("g") }
                    OutlinedButton(onClick = { onUnitsChange("mL") }) { Text("mL") }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = onSummaryChange,
                    label = { Text("Summary") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionHeader("Default ingestion category")
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal),
        ) {
            Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) {
                Text(
                    text = "Ingestions of this substance use this category by default unless overridden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = spacing.xs),
                )
                IngestionCategoryPicker(
                    category,
                    onCategoryChange,
                    inheritedCategory = null,
                )
            }
        }

        val sectionRows = buildList {
            if (onNavigateToCategories != null) {
                add(
                    SectionRowSpec(
                        title = "Categories",
                        valueText = if (categoriesCount == 0) "None" else "$categoriesCount selected",
                        onClick = onNavigateToCategories,
                    )
                )
            }
            if (onNavigateToRoutes != null) {
                add(
                    SectionRowSpec(
                        title = "Routes",
                        valueText = if (routesCount == 0) "None" else "$routesCount",
                        onClick = onNavigateToRoutes,
                    )
                )
            }
            if (onNavigateToInteractions != null) {
                add(
                    SectionRowSpec(
                        title = "Interactions",
                        valueText = if (interactionsCount == 0) "None" else "$interactionsCount",
                        onClick = onNavigateToInteractions,
                    )
                )
            }
            if (onNavigateToTolerance != null) {
                add(
                    SectionRowSpec(
                        title = "Tolerance",
                        valueText = if (toleranceFilled) "Set" else "None",
                        onClick = onNavigateToTolerance,
                    )
                )
            }
            if (onNavigateToRisks != null) {
                add(
                    SectionRowSpec(
                        title = "Risks & effects",
                        valueText = if (risksFilled) "Set" else "None",
                        onClick = onNavigateToRisks,
                    )
                )
            }
        }

        if (sectionRows.isNotEmpty()) {
            SectionHeader("Details")
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal),
            ) {
                Column {
                    sectionRows.forEachIndexed { index, row ->
                        ListItem(
                            headlineContent = { Text(row.title) },
                            supportingContent = {
                                Text(
                                    text = row.valueText,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(onClick = row.onClick),
                        )
                        if (index < sectionRows.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = spacing.lg),
                            )
                        }
                    }
                }
            }
        }

        if (onDelete != null) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(
                    text = "Delete substance",
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        }
    }
}

private data class SectionRowSpec(
    val title: String,
    val valueText: String,
    val onClick: () -> Unit,
)
