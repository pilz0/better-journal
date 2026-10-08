package foo.pilz.freaklog.ui.tabs.search.custom

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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRisksEditorScreen(
    navigateBack: () -> Unit,
    viewModel: CustomRisksEditorViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val effects = viewModel.effectsFlow.collectAsStateWithLifecycle().value
    val general = viewModel.generalRisksFlow.collectAsStateWithLifecycle().value
    val longTerm = viewModel.longTermRisksFlow.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Risks & effects") },
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
            item { SectionHeader("Effects") }
            item {
                LongTextCard(
                    value = effects,
                    onChange = viewModel::onEffectsChange,
                    label = "Effects",
                )
            }

            item { SectionHeader("Risks") }
            item {
                LongTextCard(
                    value = general,
                    onChange = viewModel::onGeneralRisksChange,
                    label = "General risks",
                )
            }

            item { SectionHeader("Long-term risks") }
            item {
                LongTextCard(
                    value = longTerm,
                    onChange = viewModel::onLongTermRisksChange,
                    label = "Long-term risks",
                )
            }
        }
    }
}

@Composable
private fun LongTextCard(
    value: String,
    onChange: (String) -> Unit,
    label: String,
) {
    val spacing = LocalSpacing.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
