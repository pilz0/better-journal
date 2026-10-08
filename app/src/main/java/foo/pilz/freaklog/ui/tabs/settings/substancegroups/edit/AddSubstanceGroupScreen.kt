package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

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
fun AddSubstanceGroupScreen(
    navigateBack: () -> Unit,
    navigateToAddItem: (groupId: Int) -> Unit,
    viewModel: AddSubstanceGroupViewModel = hiltViewModel(),
) {
    var isShowingDiscardDialog by remember { mutableStateOf(false) }
    val name by viewModel.nameFlow.collectAsStateWithLifecycle()
    val id by viewModel.idFlow.collectAsStateWithLifecycle()
    val items by viewModel.itemsFlow.collectAsStateWithLifecycle()

    BackHandler { isShowingDiscardDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add substance group") },
                navigationIcon = {
                    IconButton(onClick = { isShowingDiscardDialog = true }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        SubstanceGroupEditorContent(
            padding = padding,
            name = name,
            onNameChange = viewModel::onNameChange,
            items = items,
            onAddItem = id?.let { groupId -> { navigateToAddItem(groupId) } },
            onRemoveItem = viewModel::removeItem,
        )

        AnimatedVisibility(visible = isShowingDiscardDialog) {
            AlertDialog(
                onDismissRequest = { isShowingDiscardDialog = false },
                title = { Text("Discard group?") },
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
