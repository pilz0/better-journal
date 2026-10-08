package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomSubstanceScreen(
    navigateBack: () -> Unit,
    navigateToCustomDurationScreen: (substanceId: Int, substanceName: String) -> Unit,
    navigateToCategoriesPicker: (customSubstanceId: Int) -> Unit,
    navigateToInteractionsList: (customSubstanceId: Int) -> Unit,
    navigateToToleranceEditor: (customSubstanceId: Int) -> Unit,
    navigateToRisksEditor: (customSubstanceId: Int) -> Unit,
    viewModel: AddCustomSubstanceViewModel = hiltViewModel(),
) {
    var isShowingDiscardDialog by remember { mutableStateOf(false) }
    val substance = viewModel.substanceFlow.collectAsStateWithLifecycle().value
    val id = viewModel.idFlow.collectAsStateWithLifecycle().value

    BackHandler { isShowingDiscardDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add custom substance") },
                navigationIcon = {
                    IconButton(onClick = { isShowingDiscardDialog = true }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back")
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
            onNavigateToRoutes = id?.let {
                { navigateToCustomDurationScreen(it, viewModel.nameFlow.value) }
            },
            routesCount = substance?.roaInfos?.size ?: 0,
            onNavigateToCategories = id?.let { { navigateToCategoriesPicker(it) } },
            categoriesCount = substance?.categories?.size ?: 0,
            onNavigateToInteractions = id?.let { { navigateToInteractionsList(it) } },
            interactionsCount = substance?.interactions?.size ?: 0,
            onNavigateToTolerance = id?.let { { navigateToToleranceEditor(it) } },
            toleranceFilled = substance?.let { s ->
                s.substance.toleranceFull != null ||
                        s.substance.toleranceHalf != null ||
                        s.substance.toleranceZero != null ||
                        s.crossTolerances.isNotEmpty()
            } ?: false,
            onNavigateToRisks = id?.let { { navigateToRisksEditor(it) } },
            risksFilled = substance?.let { s ->
                s.substance.effectsText != null ||
                        s.substance.generalRisks != null ||
                        s.substance.longTermRisks != null
            } ?: false,
        )

        AnimatedVisibility(visible = isShowingDiscardDialog) {
            AlertDialog(
                onDismissRequest = { isShowingDiscardDialog = false },
                title = { Text("Discard substance?") },
                text = { Text("Anything you have entered will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isShowingDiscardDialog = false
                            viewModel.deleteDraft()
                            navigateBack()
                        },
                    ) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        isShowingDiscardDialog = false
                        navigateBack()
                    }) {
                        Text("Keep")
                    }
                },
            )
        }
    }
}
