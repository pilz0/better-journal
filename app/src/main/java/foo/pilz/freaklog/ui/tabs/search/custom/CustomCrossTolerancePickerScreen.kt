package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCrossTolerancePickerScreen(
    navigateBack: () -> Unit,
    viewModel: CustomCrossTolerancePickerViewModel = hiltViewModel(),
) {
    val selected = viewModel.selectedNamesFlow.collectAsStateWithLifecycle().value
    CustomCategoriesPickerScreen(
        categories = viewModel.allCategories,
        selectedNames = selected,
        navigateBack = navigateBack,
        onToggle = viewModel::toggle,
    )
}
