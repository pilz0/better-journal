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

package foo.pilz.freaklog.ui.main.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val WITHIN_TAB_TRANSITION_TIME_MS = 300
private const val TAB_SWITCH_TIME_MS = 200
private const val SLIDE_OFFSET_PX = 300

inline fun <reified T : Any> NavGraphBuilder.composableWithTransitions(
    noinline content: @Composable (AnimatedVisibilityScope.(NavBackStackEntry) -> Unit)
) {
  composable<T>(
      exitTransition = { navExitTransition() },
      popEnterTransition = { navPopEnterTransition() },
      enterTransition = { navEnterTransition() },
      popExitTransition = { navPopExitTransition() },
      content = content,
  )
}

/** Enter transition for forward navigation within or across tabs. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.navEnterTransition(): EnterTransition =
    if (isChangingTab()) {
      fadeIn(animationSpec = tween(TAB_SWITCH_TIME_MS))
    } else {
      slideInHorizontally(
          initialOffsetX = { SLIDE_OFFSET_PX },
          animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS),
      ) + fadeIn(animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS))
    }

/** Exit transition for forward navigation within or across tabs. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.navExitTransition(): ExitTransition =
    if (isChangingTab()) {
      fadeOut(animationSpec = tween(TAB_SWITCH_TIME_MS))
    } else {
      slideOutHorizontally(
          targetOffsetX = { -SLIDE_OFFSET_PX },
          animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS),
      ) + fadeOut(animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS))
    }

/** Enter transition when popping a destination (button or gesture back). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopEnterTransition(): EnterTransition =
    if (isChangingTab()) {
      fadeIn(animationSpec = tween(TAB_SWITCH_TIME_MS))
    } else {
      slideInHorizontally(
          initialOffsetX = { -SLIDE_OFFSET_PX },
          animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS),
      ) + fadeIn(animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS))
    }

/** Exit transition when popping a destination (button or gesture back). */
fun AnimatedContentTransitionScope<NavBackStackEntry>.navPopExitTransition(): ExitTransition =
    if (isChangingTab()) {
      fadeOut(animationSpec = tween(TAB_SWITCH_TIME_MS))
    } else {
      slideOutHorizontally(
          targetOffsetX = { SLIDE_OFFSET_PX },
          animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS),
      ) + fadeOut(animationSpec = tween(WITHIN_TAB_TRANSITION_TIME_MS))
    }

fun AnimatedContentTransitionScope<NavBackStackEntry>.isChangingTab(): Boolean {
  val initialTopLevel =
      initialState.destination.hierarchy.firstOrNull { navDestination ->
        topLevelRoutes().any { navDestination.hasRoute(it.route::class) }
      }
  val targetTopLevel =
      targetState.destination.hierarchy.firstOrNull { navDestination ->
        topLevelRoutes().any { navDestination.hasRoute(it.route::class) }
      }
  return initialTopLevel != targetTopLevel
}
