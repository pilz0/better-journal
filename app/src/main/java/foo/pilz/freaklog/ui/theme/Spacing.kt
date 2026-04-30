/*
 * Copyright (c) 2026. Freaklog contributors.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens for Freaklog.
 *
 * The upstream codebase scattered raw `.dp` literals across hundreds of
 * composables. New code should use these tokens (via [LocalSpacing] /
 * `MaterialTheme.spacing` extension below) so that paddings, gaps and
 * corner-to-corner gutters stay in lockstep across screens.
 *
 * The named scale follows the common 4-dp baseline grid:
 *   xs = 4, sm = 8, md = 12, lg = 16, xl = 24, xxl = 32.
 */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/**
 * Default [Spacing] instance. Hoisted to a top-level `val` so the Compose
 * theme doesn't allocate a new `Spacing` on every recomposition.
 */
val DefaultSpacing: Spacing = Spacing()

val LocalSpacing = compositionLocalOf { DefaultSpacing }

/**
 * Convenience accessor so callers can write `MaterialTheme.spacing.lg`
 * alongside the standard `MaterialTheme.colorScheme` / `.typography` /
 * `.shapes` accessors.
 */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
