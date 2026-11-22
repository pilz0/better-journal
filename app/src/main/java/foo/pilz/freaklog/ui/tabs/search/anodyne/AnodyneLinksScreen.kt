package foo.pilz.freaklog.ui.tabs.search.anodyne

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.anodyne.model.AnodyneLink
import foo.pilz.freaklog.ui.theme.horizontalPadding
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.rememberHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnodyneLinksScreen(
    title: String,
    url: String = "",
    links: List<AnodyneLink> = emptyList(),
    navigateBack: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  val performHaptic = rememberHaptic()

  Scaffold(
      topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
              IconButton(
                  onClick = {
                    performHaptic(HapticType.CLICK)
                    navigateBack()
                  }
              ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
              }
            },
        )
      }
  ) { padding ->
    if (links.isEmpty() && url.isNotBlank()) {
      Box(
          modifier =
              Modifier.padding(padding)
                  .fillMaxSize()
                  .padding(horizontal = horizontalPadding, vertical = 16.dp),
          contentAlignment = Alignment.Center,
      ) {
        Button(onClick = { uriHandler.openUri(url) }) {
          Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Open $title")
        }
      }
    } else {
      LazyColumn(
          modifier =
              Modifier.padding(padding)
                  .fillMaxSize()
                  .padding(horizontal = horizontalPadding, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(links) { link ->
          ElevatedCard(
              modifier =
                  Modifier.fillMaxWidth().clickable {
                    performHaptic(HapticType.SELECTION)
                    uriHandler.openUri(link.url)
                  }
          ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                  text = link.title,
                  style = MaterialTheme.typography.bodyLarge,
                  modifier = Modifier.weight(1f),
              )
              Icon(
                  Icons.AutoMirrored.Outlined.OpenInNew,
                  contentDescription = "Open link",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
