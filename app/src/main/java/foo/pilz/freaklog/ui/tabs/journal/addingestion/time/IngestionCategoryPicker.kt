package foo.pilz.freaklog.ui.tabs.journal.addingestion.time

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.substances.classes.IngestionCategory

@Composable
fun IngestionCategoryPicker(
    ingestionCategory: IngestionCategory?,
    onIngestionCategoryChange: (IngestionCategory?) -> Unit,
    inheritedCategory: IngestionCategory?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow {
            val categories = IngestionCategory.entries

            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = 0,
                    count = categories.size + 1
                ),
                onClick = { onIngestionCategoryChange(null) },
                selected = ingestionCategory == null,
                label = { Text("Default") }
            )
            categories.forEachIndexed { index, category ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index + 1,
                        count = categories.size + 1
                    ),
                    onClick = { onIngestionCategoryChange(category) },
                    selected = ingestionCategory == category,
                    label = { Text(category.displayText) }
                )
            }
        }

        AnimatedVisibility(ingestionCategory == null) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Inherited as ${
                        (inheritedCategory ?: IngestionCategory.DEFAULT_INGESTION_CATEGORY)
                            .displayText
                    }",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
