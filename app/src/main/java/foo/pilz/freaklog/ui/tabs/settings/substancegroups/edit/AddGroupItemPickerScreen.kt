package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.tabs.search.SubstanceModel
import foo.pilz.freaklog.ui.tabs.search.substancerow.SubstanceRow
import foo.pilz.freaklog.ui.theme.LocalSpacing
import foo.pilz.freaklog.ui.theme.SearchBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupItemPickerScreen(
    navigateBack: () -> Unit,
    navigateToConfig: (substanceName: String, isCustomSubstance: Boolean) -> Unit,
    viewModel: AddGroupItemPickerViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val searchText by viewModel.searchTextFlow.collectAsStateWithLifecycle()
    val substances by viewModel.filteredSubstancesFlow.collectAsStateWithLifecycle()
    val customSubstances by viewModel.filteredCustomSubstancesFlow.collectAsStateWithLifecycle()
    val isSearching = searchText.isNotBlank()
    val anyResults = customSubstances.isNotEmpty() || (isSearching && substances.isNotEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add substance") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SearchBar(
                searchText = searchText,
                onChangeSearchText = viewModel::onSearch,
                placeholder = "Search substances",
            )
            if (!anyResults) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isSearching) "No matching substance found" else "Search for a substance to add",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            LazyColumn {
                if (customSubstances.isNotEmpty()) {
                    item { SectionHeader(text = "Custom substances") }
                    items(customSubstances, key = { "cs_${it.id}" }) { customSubstance ->
                        SubstanceRow(
                            substanceModel = SubstanceModel(
                                name = customSubstance.name,
                                commonNames = emptyList(),
                                categories = emptyList(),
                                hasSaferUse = false,
                                hasInteractions = false,
                            ),
                            onTap = { navigateToConfig(customSubstance.name, true) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
                if (isSearching && substances.isNotEmpty()) {
                    item { SectionHeader(text = "Substances") }
                    items(substances, key = { it.name }) { substance ->
                        SubstanceRow(
                            substanceModel = substance,
                            onTap = { navigateToConfig(substance.name, false) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}
