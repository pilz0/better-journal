package foo.pilz.freaklog.ui.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * Top bar back arrow. It goes through the back dispatcher, so it behaves exactly like the system
 * back gesture, including a screen's own BackHandler (e.g. "discard changes?").
 */
@Composable
fun NavigateBackButton() {
  val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  IconButton(onClick = { dispatcher?.onBackPressed() }) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
  }
}
