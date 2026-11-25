package foo.pilz.freaklog.ui.tabs.settings.anodyne

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.anodyne.AnodyneRepository
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.theme.verticalPaddingCards

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnodyneSettingsScreen(
    navigateBack: () -> Unit,
    navigateToBrowse: () -> Unit,
    viewModel: AnodyneSettingsViewModel = hiltViewModel(),
) {
  val isAutoLoad by viewModel.isAutoLoadEnabled.collectAsStateWithLifecycle()
  val configuredUrl by viewModel.configuredBaseUrl.collectAsStateWithLifecycle()
  val draftUrl by viewModel.draftUrl.collectAsStateWithLifecycle()
  val isValid by viewModel.isDraftValid.collectAsStateWithLifecycle()
  val cacheSize by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  val formattedSize = Formatter.formatFileSize(context, cacheSize)

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text("AnodyneWiki") },
            navigationIcon = {
              IconButton(onClick = navigateBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
              }
            },
        )
      },
  ) { padding ->
    Column(
        modifier =
            Modifier.padding(padding)
                .padding(horizontal = horizontalPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
      AutoLoadCard(
          isAutoLoad = isAutoLoad,
          onAutoLoadChanged = viewModel::onAutoLoadChanged,
      )

      InstanceUrlCard(
          draftUrl = draftUrl,
          configuredUrl = configuredUrl,
          isValid = isValid,
          onDraftUrlChange = {
            viewModel.onDraftUrlChange(it)
            viewModel.applyDraftUrl()
          },
          onResetToDefault = viewModel::resetToDefaultUrl,
      )

      DownloadedDataCard(
          formattedSize = formattedSize,
          cacheSize = cacheSize,
          onClearCache = viewModel::clearCache,
      )

      NavigationActionsCard(
          navigateToBrowse = navigateToBrowse,
          onOpenWebsite = { uriHandler.openUri(configuredUrl) },
      )

      Spacer(Modifier.height(8.dp))
      Text(
          text =
              "AnodyneWiki is a meta-wiki that aggregates substance data from many sources. " +
                  "Its content is in the public domain unless noted otherwise. " +
                  "Dose tiers are statistically derived from logged doses (DBI-IGS) and are not recommendations.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
      )
    }
  }
}

@Composable
private fun AutoLoadCard(
    isAutoLoad: Boolean,
    onAutoLoadChanged: (Boolean) -> Unit,
) {
  ElevatedCard(
      modifier = Modifier.fillMaxWidth().padding(vertical = verticalPaddingCards),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Load automatically", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(4.dp))
          Text(
              "Substance pages load AnodyneWiki data as they open. " +
                  "Otherwise it is only loaded when tapping More from AnodyneWiki.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = isAutoLoad,
            onCheckedChange = onAutoLoadChanged,
        )
      }
    }
  }
}

@Composable
private fun InstanceUrlCard(
    draftUrl: String,
    configuredUrl: String,
    isValid: Boolean,
    onDraftUrlChange: (String) -> Unit,
    onResetToDefault: () -> Unit,
) {
  ElevatedCard(
      modifier = Modifier.fillMaxWidth().padding(vertical = verticalPaddingCards),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Instance", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(4.dp))
      Text(
          "The AnodyneWiki to load data from and link to. " +
              "Changing it deletes data downloaded from the previous one.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(12.dp))

      OutlinedTextField(
          value = draftUrl,
          onValueChange = onDraftUrlChange,
          label = { Text("Instance URL") },
          placeholder = { Text(AnodyneRepository.DEFAULT_INSTANCE_URL) },
          isError = !isValid,
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
      )
      if (!isValid) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Enter an HTTPS address, e.g. wiki.example.org (plain http only for localhost)",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
      }

      if (configuredUrl != AnodyneRepository.DEFAULT_INSTANCE_URL) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onResetToDefault) {
          Text("Use anodyne.wiki")
        }
      }
    }
  }
}

@Composable
private fun DownloadedDataCard(
    formattedSize: String,
    cacheSize: Long,
    onClearCache: () -> Unit,
) {
  ElevatedCard(
      modifier = Modifier.fillMaxWidth().padding(vertical = verticalPaddingCards),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text("Downloaded data", style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(4.dp))
      Text(
          "$formattedSize downloaded. Substances and index entries are cached locally for faster offline access.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(12.dp))

      OutlinedButton(
          onClick = onClearCache,
          enabled = cacheSize > 0,
          colors =
              ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Delete downloaded data")
      }
    }
  }
}

@Composable
private fun NavigationActionsCard(
    navigateToBrowse: () -> Unit,
    onOpenWebsite: () -> Unit,
) {
  ElevatedCard(
      modifier = Modifier.fillMaxWidth().padding(vertical = verticalPaddingCards),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      TextButton(
          onClick = navigateToBrowse,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Browse AnodyneWiki", modifier = Modifier.weight(1f))
      }

      TextButton(
          onClick = onOpenWebsite,
          modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(
            Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text("Open wiki website", modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}
