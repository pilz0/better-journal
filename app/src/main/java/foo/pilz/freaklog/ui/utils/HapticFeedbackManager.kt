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

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Enum representing different types of haptic feedback effects.
 * These are designed to provide satisfying tactile feedback throughout the app.
 */
enum class HapticType {
    /** Light tick for subtle interactions like scrolling */
    TICK,
    /** Standard click for button presses */
    CLICK,
    /** Double click for confirmations */
    DOUBLE_CLICK,
    /** Heavy click for important actions */
    HEAVY_CLICK,
    /** Smooth texture effect for graph/timeline dragging */
    TEXTURE_TICK,
    /** Success feedback for completed actions */
    SUCCESS,
    /** Error feedback for failed actions */
    ERROR,
    /** Selection change feedback */
    SELECTION,
    /** Toggle switch feedback */
    TOGGLE,
    /** Long press feedback */
    LONG_PRESS,
    /** Timeline scrubbing - especially satisfying for graph interaction */
    TIMELINE_SCRUB
}

/**
 * Manager class for handling haptic feedback using Android's Vibrator APIs.
 * Supports Android 12+ (API 31+) with enhanced effects for newer Android versions.
 */
class HapticFeedbackManager(
    private val context: Context,
    private val composeHapticFeedback: HapticFeedback
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val hasVibrator: Boolean = vibrator?.hasVibrator() == true

    /**
     * Performs haptic feedback of the specified type.
     * Uses Android's VibrationEffect API for satisfying tactile feedback.
     */
    fun performHaptic(type: HapticType) {
        if (!hasVibrator) return

        when (type) {
            HapticType.TICK -> performTick()
            HapticType.CLICK -> performClick()
            HapticType.DOUBLE_CLICK -> performDoubleClick()
            HapticType.HEAVY_CLICK -> performHeavyClick()
            HapticType.TEXTURE_TICK -> performTextureTick()
            HapticType.SUCCESS -> performSuccess()
            HapticType.ERROR -> performError()
            HapticType.SELECTION -> performSelection()
            HapticType.TOGGLE -> performToggle()
            HapticType.LONG_PRESS -> performLongPress()
            HapticType.TIMELINE_SCRUB -> performTimelineScrub()
        }
    }

    private fun performTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    private fun performClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun performDoubleClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun performHeavyClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun performTextureTick() {
        // Create a subtle, satisfying texture effect for scrubbing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(5, 50)
            vibrator?.vibrate(effect)
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    private fun performSuccess() {
        // Create a satisfying success pattern
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 50, 30)
            val amplitudes = intArrayOf(0, 100, 0, 200)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun performError() {
        // Create a distinct error pattern
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 50, 30, 50, 30, 50)
            val amplitudes = intArrayOf(0, 150, 0, 150, 0, 150)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            vibrator?.vibrate(effect)
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun performSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    private fun performToggle() {
        // Satisfying toggle effect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun performLongPress() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    private fun performTimelineScrub() {
        // Create an especially satisfying scrubbing effect for the timeline graph
        // This uses a short, crisp vibration that feels great when dragging across the graph
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(8, 80)
            vibrator?.vibrate(effect)
        } else {
            composeHapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

/**
 * Composable function to remember and create a HapticFeedbackManager.
 */
@Composable
fun rememberHapticFeedbackManager(): HapticFeedbackManager {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    return remember(context, hapticFeedback) {
        HapticFeedbackManager(context, hapticFeedback)
    }
}
