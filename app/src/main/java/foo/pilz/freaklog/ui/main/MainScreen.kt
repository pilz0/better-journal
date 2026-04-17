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

package foo.pilz.freaklog.ui.main

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import foo.pilz.freaklog.ui.main.navigation.graphs.ExperienceRoute
import foo.pilz.freaklog.ui.main.navigation.graphs.inventoryGraph
import foo.pilz.freaklog.ui.main.navigation.graphs.journalGraph
import foo.pilz.freaklog.ui.main.navigation.graphs.saferGraph
import foo.pilz.freaklog.ui.main.navigation.graphs.searchGraph
import foo.pilz.freaklog.ui.main.navigation.graphs.settingsGraph
import foo.pilz.freaklog.ui.main.navigation.graphs.statsGraph
import foo.pilz.freaklog.ui.main.navigation.graphs.AddIngestionRoute
import foo.pilz.freaklog.ui.main.navigation.JournalTopLevelRoute
import foo.pilz.freaklog.ui.main.navigation.topLevelRoutes
import foo.pilz.freaklog.ui.utils.HapticFeedbackProvider
import foo.pilz.freaklog.ui.utils.HapticType
import foo.pilz.freaklog.ui.utils.rememberHaptic

@Composable
fun MainScreen(
    viewModel: MainScreenViewModel = hiltViewModel(),
    shouldNavigateToAddIngestion: Boolean = false,
    onAddIngestionNavigated: () -> Unit = {},
    shouldNavigateToJournalScreen: Boolean = false,
    onJournalScreenNavigated: () -> Unit = {},
    shouldNavigateToExperienceId: Int? = null,
    onExperienceNavigated: () -> Unit = {}
) {
    if (viewModel.isAcceptedFlow.collectAsState().value) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val isHapticEnabled = viewModel.isHapticFeedbackEnabledFlow.collectAsState().value

        // Handle navigation to Add Ingestion when triggered from widget
        // The guard condition prevents re-execution when state is reset to false
        LaunchedEffect(shouldNavigateToAddIngestion) {
            if (shouldNavigateToAddIngestion) {
                navController.navigate(AddIngestionRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
                onAddIngestionNavigated()
            }
        }

        LaunchedEffect(shouldNavigateToJournalScreen) {
            if (shouldNavigateToJournalScreen) {
                navController.navigate(JournalTopLevelRoute) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
                onJournalScreenNavigated()
            }
        }

        LaunchedEffect(shouldNavigateToExperienceId) {
            if (shouldNavigateToExperienceId != null) {
                navController.navigate(ExperienceRoute(shouldNavigateToExperienceId)) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
                onExperienceNavigated()
            }
        }

        val hideSafer = viewModel.activateSaferFlow.collectAsState().value
        val hideStats = viewModel.isStatsHiddenFlow.collectAsState().value
        val hideDrugs = viewModel.isDrugsHiddenFlow.collectAsState().value
        val showInventory = viewModel.isInventoryEnabledFlow.collectAsState().value

        HapticFeedbackProvider(isEnabled = isHapticEnabled) {
            val performHaptic = rememberHaptic()

            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    val currentDestination = navBackStackEntry?.destination
                    topLevelRoutes(hideSafer, hideStats, hideDrugs, showInventory).forEach { topLevelRoute ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.hasRoute(topLevelRoute.route::class) } == true
                        item(
                            icon = {
                                Icon(
                                    if (selected) topLevelRoute.filledIcon else topLevelRoute.outlinedIcon,
                                    contentDescription = topLevelRoute.name
                                )
                            },
                            label = { Text(topLevelRoute.name) },
                            selected = selected,
                            onClick = {
                                performHaptic(HapticType.CLICK)
                                if (selected) {
                                    val isAlreadyOnTopOfTab =
                                        topLevelRoutes(hideSafer, hideStats, hideDrugs, showInventory).any { it.route == currentDestination?.route }
                                    if (!isAlreadyOnTopOfTab) {
                                        navController.popBackStack()
                                    }
                                } else {
                                    navController.navigate(topLevelRoute.route) {
                                        // Pop up to the start destination of the graph to
                                        // avoid building up a large stack of destinations
                                        // on the back stack as users select items
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        // Avoid multiple copies of the same destination when
                                        // reselecting the same item
                                        launchSingleTop = true
                                        // Restore state when reselecting a previously selected item
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            ) {
                NavHost(
                    navController,
                    startDestination = JournalTopLevelRoute
                ) {
                    journalGraph(navController)
                    statsGraph(navController)
                    searchGraph(navController)
                    saferGraph(navController)
                    if (showInventory) {
                        inventoryGraph(navController)
                    }
                    settingsGraph(navController)
                }
            }
        }
    } else {
        AcceptConditionsScreen(onTapAccept = viewModel::accept)
    }
}
