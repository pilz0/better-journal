package foo.pilz.freaklog.ui.main.navigation.graphs

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation
import foo.pilz.freaklog.ui.main.navigation.composableWithTransitions
import foo.pilz.freaklog.ui.tabs.settings.customunits.add.AddIngestionSearchScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.addIntakeLimitGraph(navController: NavController) {
    navigation<AddIntakeLimitParentRoute>(
        startDestination = AddIntakeLimitChooseSubstanceRoute,
    ) {
        composableWithTransitions<AddIntakeLimitChooseSubstanceRoute> {
            AddIngestionSearchScreen(
                navigateToChooseRoute = { substanceName ->
                    navController.navigate(EditIntakeLimitRoute(substanceName = substanceName))
                },
                navigateToCustomSubstanceChooseRoute = { customSubstanceName ->
                    navController.navigate(EditIntakeLimitRoute(substanceName = customSubstanceName))
                }
            )
        }
    }
}

@Serializable
object AddIntakeLimitParentRoute

@Serializable
object AddIntakeLimitChooseSubstanceRoute
