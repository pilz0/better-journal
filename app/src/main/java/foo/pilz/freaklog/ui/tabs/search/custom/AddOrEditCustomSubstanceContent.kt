package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SsidChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.substances.classes.IngestionCategory
import foo.pilz.freaklog.ui.tabs.journal.addingestion.time.IngestionCategoryPicker
import foo.pilz.freaklog.ui.theme.LocalSpacing

@Preview
@Composable
fun AddOrEditCustomSubstanceContentPreview() {
    AddOrEditCustomSubstanceContent(
        name = "Medication",
        units = "mg",
        onNameChange = {},
        onUnitsChange = {},
        padding = PaddingValues(0.dp),
        category = IngestionCategory.DEFAULT_INGESTION_CATEGORY,
        onCategoryChange = {},
        navigateToCustomDurationScreen = { },
    )
}

@Composable
fun AddOrEditCustomSubstanceContent(
    padding: PaddingValues,
    name: String,
    onNameChange: (String) -> Unit,
    units: String,
    onUnitsChange: (String) -> Unit,
    category: IngestionCategory?,
    onCategoryChange: (IngestionCategory?) -> Unit,
    navigateToCustomDurationScreen: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(horizontal = spacing.screenHorizontal)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(spacing.sm))
        val focusManager = LocalFocusManager.current
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = units,
            onValueChange = onUnitsChange,
            label = { Text("Units") },
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = { onUnitsChange("µg") }) {
                Text(text = "µg")
            }
            OutlinedButton(onClick = { onUnitsChange("mg") }) {
                Text(text = "mg")
            }
            OutlinedButton(onClick = { onUnitsChange("g") }) {
                Text(text = "g")
            }
            OutlinedButton(onClick = { onUnitsChange("mL") }) {
                Text(text = "mL")
            }
        }

        Spacer(modifier = Modifier.height(spacing.sm))

        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = spacing.lg, vertical = spacing.sm)
            ) {
                Text(
                    text = "Default category for ingestions",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Ingestions with this substance will use this category by default "
                            + "unless overridden by a custom unit or ingestion.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = spacing.xs)
                )

                IngestionCategoryPicker(
                    category,
                    onCategoryChange,
                    inheritedCategory = null,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.lg))

        if (navigateToCustomDurationScreen != null) {
            TextButton(
                onClick = navigateToCustomDurationScreen
            ) {
                Icon(
                    imageVector = Icons.Outlined.SsidChart,
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Edit custom durations")
            }
        }

    }
}