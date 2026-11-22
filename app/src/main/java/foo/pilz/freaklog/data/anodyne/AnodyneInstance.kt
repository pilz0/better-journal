package foo.pilz.freaklog.data.anodyne

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The AnodyneWiki instance the user picked, for code that can't suspend on the setting (Compose
 * links, webhook hyperlinks). `JournalApplication` keeps it in sync with
 * `UserPreferences.anodyneBaseUrlFlow`; Compose readers recompose when it changes.
 */
object AnodyneInstance {
  var baseUrl: String by mutableStateOf(AnodyneRepository.DEFAULT_INSTANCE_URL)

  fun substancePageUrl(title: String): String = AnodyneRepository.substancePageUrl(baseUrl, title)

  /** Points a bundled `https://anodyne.wiki/...` link at the configured instance. */
  fun rebase(url: String): String =
      if (url.startsWith(AnodyneRepository.DEFAULT_INSTANCE_URL + "/")) {
        baseUrl + url.removePrefix(AnodyneRepository.DEFAULT_INSTANCE_URL)
      } else {
        url
      }
}
