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

package foo.pilz.freaklog.ui.main.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigation
import foo.pilz.freaklog.ui.main.navigation.composableWithTransitions
import foo.pilz.freaklog.ui.main.navigation.StatsTopLevelRoute
import foo.pilz.freaklog.ui.tabs.stats.StatsScreen
import foo.pilz.freaklog.ui.tabs.stats.charts.MoreChartsScreen
import foo.pilz.freaklog.ui.tabs.stats.substancecompanion.SubstanceCompanionScreen
import foo.pilz.freaklog.ui.tabs.stats.tolerance.ToleranceChartScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.statsGraph(navController: NavHostController) {
    navigation<StatsTopLevelRoute>(
        startDestination = StatsScreenRoute,
    ) {
        composableWithTransitions<StatsScreenRoute> {
            StatsScreen(
                navigateToSubstanceCompanion = { substanceName, consumerName ->
                    navController.navigate(
                        SubstanceCompanionRoute(
                            substanceName = substanceName,
                            consumerName = consumerName
                        )
                    )
                },
                navigateToToleranceChart = {
                    navController.navigate(ToleranceChartRoute)
                },
                navigateToMoreCharts = {
                    navController.navigate(MoreChartsRoute)
                }
            )
        }
        composableWithTransitions<SubstanceCompanionRoute> {
            SubstanceCompanionScreen()
        }
        composableWithTransitions<ToleranceChartRoute> {
            ToleranceChartScreen()
        }
        composableWithTransitions<MoreChartsRoute> {
            MoreChartsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Serializable
object StatsScreenRoute

@Serializable
data class SubstanceCompanionRoute(val substanceName: String, val consumerName: String?)

@Serializable
object ToleranceChartRoute

@Serializable
object MoreChartsRoute