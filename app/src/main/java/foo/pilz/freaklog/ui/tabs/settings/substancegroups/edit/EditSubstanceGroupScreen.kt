package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

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
fun EditSubstanceGroupScreen(
    navigateBack: () -> Unit,
    navigateToAddItem: (groupId: Int) -> Unit,
    viewModel: EditSubstanceGroupViewModel = hiltViewModel(),
) {
    var isShowingDeleteDialog by remember { mutableStateOf(false) }
    val name by viewModel.nameFlow.collectAsStateWithLifecycle()
    val items by viewModel.itemsFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit substance group") },
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
        SubstanceGroupEditorContent(
            padding = padding,
            name = name,
            onNameChange = viewModel::onNameChange,
            items = items,
            onAddItem = { navigateToAddItem(viewModel.id) },
            onRemoveItem = viewModel::removeItem,
            onDelete = { isShowingDeleteDialog = true },
        )

        AnimatedVisibility(visible = isShowingDeleteDialog) {
            AlertDialog(
                onDismissRequest = { isShowingDeleteDialog = false },
                title = { Text("Delete group?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            isShowingDeleteDialog = false
                            viewModel.delete()
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
