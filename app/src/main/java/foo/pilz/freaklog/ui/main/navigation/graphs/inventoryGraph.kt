/*
 * Copyright (c) 2026. FreakLog contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package foo.pilz.freaklog.ui.main.navigation.graphs

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation
import foo.pilz.freaklog.ui.main.navigation.InventoryTopLevelRoute
import foo.pilz.freaklog.ui.main.navigation.composableWithTransitions
import foo.pilz.freaklog.ui.tabs.inventory.InventoryScreen
import kotlinx.serialization.Serializable

@Serializable
object InventoryScreenRoute

fun NavGraphBuilder.inventoryGraph(navController: NavController) {
    navigation<InventoryTopLevelRoute>(
        startDestination = InventoryScreenRoute,
    ) {
        composableWithTransitions<InventoryScreenRoute> {
            InventoryScreen(
                navigateToAddIngestionForSubstance = { substanceName, isCustom ->
                    if (isCustom) {
                        navController.navigate(CustomSubstanceChooseRouteRoute(substanceName))
                    } else {
                        navController.navigate(CheckInteractionsRoute(substanceName))
                    }
                }
            )
        }
    }
}
