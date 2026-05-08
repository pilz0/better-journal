package foo.pilz.freaklog.ui.main.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigation
import foo.pilz.freaklog.ui.main.navigation.StatsTopLevelRoute
import foo.pilz.freaklog.ui.main.navigation.composableWithTransitions
import foo.pilz.freaklog.ui.tabs.stats.StatsScreen
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
                }
            )
        }
        composableWithTransitions<SubstanceCompanionRoute> {
            SubstanceCompanionScreen()
        }
        composableWithTransitions<ToleranceChartRoute> {
            ToleranceChartScreen()
        }
    }
}

@Serializable
object StatsScreenRoute

@Serializable
data class SubstanceCompanionRoute(val substanceName: String, val consumerName: String?)

@Serializable
object ToleranceChartRoute