/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * CompositionLocal to track whether haptic feedback is enabled.
 * Defaults to true (enabled).
 */
val LocalHapticEnabled = compositionLocalOf { true }

/**
 * CompositionLocal to provide access to HapticFeedbackManager throughout the app.
 */
val LocalHapticFeedbackManager = compositionLocalOf<HapticFeedbackManager?> { null }

/**
 * Provides haptic feedback context to the content.
 * This should be placed at the root of the app's composition.
 *
 * @param isEnabled Whether haptic feedback is enabled
 * @param content The composable content
 */
@Composable
fun HapticFeedbackProvider(
    isEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val hapticManager = HapticFeedbackManager(context, hapticFeedback)

    CompositionLocalProvider(
        LocalHapticEnabled provides isEnabled,
        LocalHapticFeedbackManager provides hapticManager
    ) {
        content()
    }
}

/**
 * Composable function to perform haptic feedback if enabled.
 * Returns a function that can be called to trigger haptic feedback.
 */
@Composable
fun rememberHaptic(): (HapticType) -> Unit {
    val isEnabled = LocalHapticEnabled.current
    val manager = LocalHapticFeedbackManager.current

    return { type: HapticType ->
        if (isEnabled) {
            manager?.performHaptic(type)
        }
    }
}
