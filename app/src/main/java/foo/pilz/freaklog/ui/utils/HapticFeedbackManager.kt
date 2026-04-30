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
 * Supports Android 12+ (API 31+) for vibrator access and uses predefined VibrationEffects.
 * Falls back to Compose HapticFeedback for older Android versions.
 */
class HapticFeedbackManager(
    private val context: Context,
) {
    private val vibrator: Vibrator? by lazy {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.takeIf { it.hasVibrator() }
    }

    fun performHaptic(type: HapticType) {
        vibrator?.let {
            val effect = when (type) {
                HapticType.TICK, HapticType.SELECTION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticType.CLICK, HapticType.TOGGLE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticType.DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticType.HEAVY_CLICK, HapticType.LONG_PRESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticType.TEXTURE_TICK -> VibrationEffect.createOneShot(5, 50)
                HapticType.SUCCESS -> {
                    val timings = longArrayOf(0, 30, 50, 30)
                    val amplitudes = intArrayOf(0, 100, 0, 200)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                HapticType.ERROR -> {
                    val timings = longArrayOf(0, 50, 30, 50, 30, 50)
                    val amplitudes = intArrayOf(0, 150, 0, 150, 0, 150)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                HapticType.TIMELINE_SCRUB -> {
                    VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK)
                        .compose()
                }
            }
            it.vibrate(effect)
        }
    }
}
