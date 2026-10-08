package foo.pilz.freaklog.ui.tabs.settings.substancegroups

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.ui.components.EmptyScreenDisclaimer
import foo.pilz.freaklog.ui.theme.LocalSpacing
import foo.pilz.freaklog.ui.theme.SearchBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstanceGroupsScreen(
    navigateToAddSubstanceGroup: () -> Unit,
    navigateToEditSubstanceGroup: (groupId: Int) -> Unit,
    viewModel: SubstanceGroupsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val groups by viewModel.filteredGroupsFlow.collectAsStateWithLifecycle()
    val searchText by viewModel.searchTextFlow.collectAsStateWithLifecycle()
    val collision by viewModel.pendingCollision.collectAsStateWithLifecycle()
    val snackbarText by viewModel.snackbar.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarText) {
        snackbarText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromUri(context, it) }
    }

    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Substance groups") },
                actions = {
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Import group")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Substance group") },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add substance group") },
                onClick = navigateToAddSubstanceGroup,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            SearchBar(
                searchText = searchText,
                onChangeSearchText = viewModel::onSearch,
                placeholder = "Search substance groups",
            )
            if (groups.isEmpty()) {
                if (searchText.isEmpty()) {
                    EmptyScreenDisclaimer(
                        title = "No substance groups yet",
                        description = "Create a group to add multiple ingestions at once",
                        icon = Icons.Outlined.Medication,
                    )
                } else {
                    EmptyScreenDisclaimer(
                        title = "No substance groups found",
                        description = "No substance group matches your search",
                        icon = Icons.Outlined.Medication,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        vertical = spacing.sm,
                        horizontal = spacing.screenHorizontal
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(groups, key = { it.group.id }) { group ->
                        SubstanceGroupCard(
                            group = group,
                            onClick = { navigateToEditSubstanceGroup(group.group.id) },
                        )
                    }
                }
            }
        }
    }

    collision?.let { payload ->
        AlertDialog(
            onDismissRequest = viewModel::resolveCancel,
            title = { Text("Group already exists") },
            text = { Text("A substance group named \"${payload.name}\" is already saved. What do you want to do?") },
            confirmButton = {
                TextButton(onClick = viewModel::resolveReplace) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::resolveKeepBoth) { Text("Keep both") }
            },
        )
    }
}
