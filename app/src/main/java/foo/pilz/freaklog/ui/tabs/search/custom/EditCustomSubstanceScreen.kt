package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomSubstanceScreen(
    navigateBack: () -> Unit,
    navigateToCustomDurationScreen: (substanceId: Int, substanceName: String) -> Unit,
    navigateToCategoriesPicker: (customSubstanceId: Int) -> Unit,
    navigateToInteractionsList: (customSubstanceId: Int) -> Unit,
    navigateToToleranceEditor: (customSubstanceId: Int) -> Unit,
    navigateToRisksEditor: (customSubstanceId: Int) -> Unit,
    viewModel: EditCustomSubstanceViewModel = hiltViewModel(),
) {
    var isShowingDeleteDialog by remember { mutableStateOf(false) }
    val substance = viewModel.substanceFlow.collectAsStateWithLifecycle().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit custom substance") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = { viewModel.share(context) }) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "Share")
                    }
                },
            )
        },
    ) { padding ->
        CustomSubstanceEditorContent(
            padding = padding,
            name = viewModel.nameFlow.collectAsStateWithLifecycle().value,
            onNameChange = viewModel::onNameChange,
            units = viewModel.unitsFlow.collectAsStateWithLifecycle().value,
            onUnitsChange = viewModel::onUnitsChange,
            summary = viewModel.summaryFlow.collectAsStateWithLifecycle().value,
            onSummaryChange = viewModel::onSummaryChange,
            category = viewModel.categoryFlow.collectAsStateWithLifecycle().value,
            onCategoryChange = viewModel::onCategoryChange,
            onNavigateToRoutes = {
                navigateToCustomDurationScreen(viewModel.id, viewModel.nameFlow.value)
            },
            routesCount = substance?.roaInfos?.size ?: 0,
            onNavigateToCategories = { navigateToCategoriesPicker(viewModel.id) },
            categoriesCount = substance?.categories?.size ?: 0,
            onNavigateToInteractions = { navigateToInteractionsList(viewModel.id) },
            interactionsCount = substance?.interactions?.size ?: 0,
            onNavigateToTolerance = { navigateToToleranceEditor(viewModel.id) },
            toleranceFilled = substance?.let { s ->
                s.substance.toleranceFull != null ||
                        s.substance.toleranceHalf != null ||
                        s.substance.toleranceZero != null ||
                        s.crossTolerances.isNotEmpty()
            } ?: false,
            onNavigateToRisks = { navigateToRisksEditor(viewModel.id) },
            risksFilled = substance?.let { s ->
                s.substance.effectsText != null ||
                        s.substance.generalRisks != null ||
                        s.substance.longTermRisks != null
            } ?: false,
            onDelete = { isShowingDeleteDialog = true },
        )

        AnimatedVisibility(visible = isShowingDeleteDialog) {
            AlertDialog(
                onDismissRequest = { isShowingDeleteDialog = false },
                title = { Text("Delete substance?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isShowingDeleteDialog = false
                            viewModel.deleteCustomSubstance()
                            navigateBack()
                        },
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { isShowingDeleteDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
