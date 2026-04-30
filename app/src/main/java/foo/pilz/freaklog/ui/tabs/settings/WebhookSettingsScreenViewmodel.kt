package foo.pilz.freaklog.ui.tabs.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class WebhookSettingsScreenViewmodel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    var webhookURL by mutableStateOf("")
    var webhookName by mutableStateOf("")
    var webhookTemplate by mutableStateOf("")

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            webhookURL = userPreferences.readWebhookURL().first()
            webhookName = userPreferences.readWebhookName().first()
            webhookTemplate = userPreferences.readWebhookTemplate().first()
        }
    }

    // Change this function definition
    fun onDoneTap(navController: NavController) {
        viewModelScope.launch {
            // Save the current state variables to your repository
            userPreferences.writeWebhookURL(webhookURL)
            userPreferences.writeWebhookName(webhookName)
            userPreferences.writeWebhookTemplate(webhookTemplate)
            navController.popBackStack()
        }

    }
}