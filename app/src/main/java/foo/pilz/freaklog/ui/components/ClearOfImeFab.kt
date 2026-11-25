package foo.pilz.freaklog.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Height of an extended FAB (56dp) plus its 16dp margin and a little air. */
private val ImeFabClearance = 80.dp

/**
 * For the scroll container of a screen whose FAB floats above the keyboard (`Modifier.imePadding()`
 * on the FAB): while the keyboard is open the container ends above the FAB, so a focused field is
 * scrolled into view above it instead of underneath it.
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.clearOfImeFab(): Modifier = composed {
  imePadding().padding(bottom = if (WindowInsets.isImeVisible) ImeFabClearance else 0.dp)
}

/**
 * A focused text field only scrolls its cursor line into view, so its bottom edge can end up hidden
 * when the keyboard opens. Once the keyboard settles, bring the whole field into view.
 */
fun Modifier.wholeFieldAboveIme(): Modifier = composed {
  val requester = remember { BringIntoViewRequester() }
  var isFocused by remember { mutableStateOf(false) }
  // Content above the field can still change (e.g. a hint animating in): follow where it ends up.
  var bottomInRoot by remember { mutableFloatStateOf(0f) }
  val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
  // Restarted on every change while things animate; only the last one gets to run.
  LaunchedEffect(isFocused, imeBottom, bottomInRoot) {
    if (isFocused && imeBottom > 0) {
      delay(IME_SETTLE_MS)
      requester.bringIntoView()
    }
  }
  bringIntoViewRequester(requester)
      .onFocusChanged { isFocused = it.isFocused }
      .onGloballyPositioned { bottomInRoot = it.boundsInRoot().bottom }
}

// ponytail: fixed wait for the field's own cursor scroll to finish (shorter ones get overridden);
// replace with a scroll-idle signal if it ever proves flaky.
private const val IME_SETTLE_MS = 300L
