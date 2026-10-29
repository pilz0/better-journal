/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.theme


import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.ui.graph.style.LocalGraphStyle
import foo.pilz.freaklog.ui.graph.style.themedGraphStyle


// Default screen-edge padding. Bumped from the upstream 10.dp to the
// Material 3 standard 16.dp so list and card content no longer feels
// pinched against the edge of the screen. Prefer `MaterialTheme.spacing.lg`
// in new code; this constant remains for backwards compatibility.
val horizontalPadding = 16.dp
val verticalPaddingCards = 4.dp
val minimumTouchTargetHeight = 48.dp

@Composable
fun JournalTheme(
    content: @Composable () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    CompositionLocalProvider(LocalSpacing provides DefaultSpacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JournalTypography,
            shapes = JournalShapes,
        ) {
            SharedTransitionLayout {
                CompositionLocalProvider(
                    LocalGraphStyle provides themedGraphStyle(),
                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                    content = content,
                )
            }
        }
    }
}

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}
