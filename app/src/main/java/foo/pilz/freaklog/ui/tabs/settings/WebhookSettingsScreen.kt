package foo.pilz.freaklog.ui.tabs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun WebhookSettingsScreen(
    navController: NavController,
    viewModel: WebhookSettingsScreenViewmodel = hiltViewModel()
) {
    WebhookSettingsContent(
        webhookURL = viewModel.webhookURL,
        onChangedWebhookURL = { viewModel.webhookURL = it },
        webhookName = viewModel.webhookName,
        onChangedWebhookName = { viewModel.webhookName = it },
        webhookTemplate = viewModel.webhookTemplate,
        onChangedWebhookTemplate = { viewModel.webhookTemplate = it },
        onDoneTap = {
            viewModel.onDoneTap(navController)
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhookSettingsContent(
    webhookURL: String,
    onChangedWebhookURL: (String) -> Unit,
    webhookName: String,
    onChangedWebhookName: (String) -> Unit,
    webhookTemplate: String,
    onChangedWebhookTemplate: (String) -> Unit,
    onDoneTap: () -> Unit)
{
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Webhook") },
                actions = {
                    IconButton(onClick = onDoneTap) {
                        Icon(
                            Icons.Filled.Done,
                            contentDescription = "Done icon"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(10.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val focusManager = LocalFocusManager.current
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Discord Webhook Integration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Paste your Discord Webhook URL below. The 'Webhook Name' will be used as the name for ingestion's.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            OutlinedTextField(
                value = webhookURL,
                onValueChange = onChangedWebhookURL,
                singleLine = true,
                label = { Text(text = "Webhook URL") },
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webhookName,
                onValueChange = onChangedWebhookName,
                singleLine = true,
                label = { Text(text = "Webhook Name") },
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webhookTemplate,
                onValueChange = onChangedWebhookTemplate,
                singleLine = true,
                label = { Text(text = "Webhook Template") },
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
fun EditWebhookSettingsScreenPreview() {
    WebhookSettingsContent(
        webhookURL = "https://psychonautwiki.org/",
        onChangedWebhookURL = { },
        webhookName = "psychonautwiki",
        onChangedWebhookName = { },
        webhookTemplate = "foobar",
        onChangedWebhookTemplate = { },
        onDoneTap = { }
    )
}