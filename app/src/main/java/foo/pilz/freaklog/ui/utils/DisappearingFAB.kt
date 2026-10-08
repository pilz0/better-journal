package foo.pilz.freaklog.ui.utils

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable

@Composable
fun DisappearingFAB(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible,
        enter = slideInVertically(initialOffsetY = { 80 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { 80 }) + fadeOut(),
    ) {
        content()
    }
}
