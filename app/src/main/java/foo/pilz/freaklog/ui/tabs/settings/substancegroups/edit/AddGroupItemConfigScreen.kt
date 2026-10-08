package foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupItemConfigScreen(
    navigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddGroupItemConfigViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    var routeMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.substanceName) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add to group") },
                icon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                onClick = { viewModel.saveAndDismiss(onSaved) },
                containerColor = if (viewModel.canSave) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                OutlinedTextField(
                    value = viewModel.administrationRoute.displayText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Route") },
                    trailingIcon = {
                        if (routeMenuOpen) {
                            Icon(Icons.Default.ArrowDropUp, contentDescription = null)
                        } else {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = spacing.sm)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { routeMenuOpen = !routeMenuOpen },
                    color = Color.Transparent,
                ) {}
                DropdownMenu(
                    expanded = routeMenuOpen,
                    onDismissRequest = { routeMenuOpen = false },
                ) {
                    viewModel.availableRoutes.forEach { route ->
                        DropdownMenuItem(
                            text = { Text(route.displayText) },
                            onClick = {
                                viewModel.onRouteChange(route)
                                routeMenuOpen = false
                            },
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = viewModel.doseText,
                    onValueChange = viewModel::onDoseChange,
                    label = { Text("Dose") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = viewModel.unitsText,
                    onValueChange = viewModel::onUnitsChange,
                    label = { Text("Units") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Estimated dose", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = viewModel.isEstimate,
                    onCheckedChange = viewModel::onEstimateChange
                )
            }
            if (viewModel.isEstimate) {
                OutlinedTextField(
                    value = viewModel.estimatedStdDevText,
                    onValueChange = viewModel::onEstimatedStdDevChange,
                    label = { Text("Standard deviation (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
