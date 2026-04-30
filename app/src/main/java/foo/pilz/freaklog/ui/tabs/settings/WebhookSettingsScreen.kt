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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
        useFreakQuery = viewModel.useFreakQuery,
        onChangedUseFreakQuery = { viewModel.useFreakQuery = it },
        freakQuerySeparator = viewModel.freakQuerySeparator,
        onChangedFreakQuerySeparator = { viewModel.freakQuerySeparator = it },
        hyperlinkSubstances = viewModel.hyperlinkSubstances,
        onChangedHyperlinkSubstances = { viewModel.hyperlinkSubstances = it },
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
    useFreakQuery: Boolean,
    onChangedUseFreakQuery: (Boolean) -> Unit,
    freakQuerySeparator: String,
    onChangedFreakQuerySeparator: (String) -> Unit,
    hyperlinkSubstances: Boolean,
    onChangedHyperlinkSubstances: (Boolean) -> Unit,
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
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
                    Text(
                        text = "Template variables: {user}, {substance}, {dose}, {units}, {route}, {site}, {note}. Use [optional text] to hide blocks if variables are empty.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "FreakQuery tags: {{today|substance=A-PVP|sum=dose}}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
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
                singleLine = false,
                minLines = 3,
                label = { Text(text = "Webhook Template") },
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FreakQuery Configuration",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Enable FreakQuery tags", modifier = Modifier.weight(1f))
                        Switch(checked = useFreakQuery, onCheckedChange = onChangedUseFreakQuery)
                    }

                    if (useFreakQuery) {
                        OutlinedTextField(
                            value = freakQuerySeparator,
                            onValueChange = onChangedFreakQuerySeparator,
                            singleLine = true,
                            label = { Text(text = "List Separator") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(text = "Hyperlink substances", modifier = Modifier.weight(1f))
                        Switch(checked = hyperlinkSubstances, onCheckedChange = onChangedHyperlinkSubstances)
                    }
                }
            }
            Spacer(modifier = Modifier.padding(20.dp))
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
        useFreakQuery = true,
        onChangedUseFreakQuery = { },
        freakQuerySeparator = ", ",
        onChangedFreakQuerySeparator = { },
        hyperlinkSubstances = true,
        onChangedHyperlinkSubstances = { },
        onDoneTap = { }
    )
}