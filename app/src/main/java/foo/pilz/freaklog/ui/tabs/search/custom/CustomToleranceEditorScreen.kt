package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomToleranceEditorScreen(
    navigateBack: () -> Unit,
    navigateToCrossTolerancePicker: (customSubstanceId: Int) -> Unit,
    viewModel: CustomToleranceEditorViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val full = viewModel.fullFlow.collectAsStateWithLifecycle().value
    val half = viewModel.halfFlow.collectAsStateWithLifecycle().value
    val zero = viewModel.zeroFlow.collectAsStateWithLifecycle().value
    val cross = viewModel.crossToleranceFlow.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tolerance") },
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
            item { SectionHeader("Tolerance build-up") }
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        ToleranceField(
                            label = "Full",
                            value = full,
                            onChange = viewModel::onFullChange,
                        )
                        ToleranceField(
                            label = "Half",
                            value = half,
                            onChange = viewModel::onHalfChange,
                        )
                        ToleranceField(
                            label = "Zero",
                            value = zero,
                            onChange = viewModel::onZeroChange,
                        )
                        Text(
                            text = "Zero is the time until tolerance is back to baseline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionHeader("Cross-tolerance") }
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal),
                ) {
                    ListItem(
                        headlineContent = { Text("Categories") },
                        supportingContent = {
                            Text(
                                text = if (cross.isEmpty()) "None"
                                else cross.joinToString(", "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            navigateToCrossTolerancePicker(viewModel.customSubstanceId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToleranceField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
