package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import foo.pilz.freaklog.data.room.experiences.entities.SubstanceGroupItem
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.theme.LocalSpacing

@Composable
fun SubstanceGroupEditorContent(
    padding: PaddingValues,
    name: String,
    onNameChange: (String) -> Unit,
    items: List<SubstanceGroupItem>,
    onAddItem: (() -> Unit)?,
    onRemoveItem: (SubstanceGroupItem) -> Unit,
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
            }
        }

        SectionHeader("Substances")
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal),
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SubstanceGroupItemRow(
                        item = item,
                        onRemove = { onRemoveItem(item) },
                    )
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = spacing.lg),
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.lg))
                }
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Add substance",
                            color = if (onAddItem != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = if (onAddItem != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(enabled = onAddItem != null) {
                        onAddItem?.invoke()
                    },
                )
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
                    text = "Delete group",
                    modifier = Modifier.padding(start = spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun SubstanceGroupItemRow(
    item: SubstanceGroupItem,
    onRemove: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.substanceName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatItemDose(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove substance",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatItemDose(item: SubstanceGroupItem): String {
    val route = item.administrationRoute.displayText
    val dose = item.dose
    val unit = item.units ?: ""
    val dosePart = when {
        dose == null -> "unknown dose"
        item.isEstimate && item.estimatedDoseStandardDeviation != null ->
            "$dose ± ${item.estimatedDoseStandardDeviation} $unit"

        item.isEstimate -> "~$dose $unit"
        else -> "$dose $unit"
    }.trim()
    return "$route · $dosePart"
}
