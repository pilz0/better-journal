/*
 * Copyright (c) 2023. Isaak Hanimann.
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
import foo.pilz.freaklog.ui.main.navigation.SettingsTopLevelRoute
import foo.pilz.freaklog.ui.tabs.settings.FAQScreen
import foo.pilz.freaklog.ui.tabs.settings.SettingsScreen
import foo.pilz.freaklog.ui.tabs.settings.colors.SubstanceColorsScreen
import foo.pilz.freaklog.ui.tabs.settings.combinations.CombinationSettingsScreen
import foo.pilz.freaklog.ui.tabs.settings.customunits.CustomUnitsScreen
import foo.pilz.freaklog.ui.tabs.settings.customunits.archive.CustomUnitArchiveScreen
import foo.pilz.freaklog.ui.tabs.settings.customunits.edit.EditCustomUnitScreen
import foo.pilz.freaklog.ui.tabs.settings.webhooks.EditWebhookPresetScreen
import foo.pilz.freaklog.ui.tabs.settings.webhooks.WebhookArchiveScreen
import foo.pilz.freaklog.ui.tabs.settings.webhooks.WebhooksScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    navigation<SettingsTopLevelRoute>(
        startDestination = SettingsScreenRoute,
    ) {
        composableWithTransitions<SettingsScreenRoute> {
            SettingsScreen(
                navigateToFAQ = {
                    navController.navigate(FAQRoute)
                },
                navigateToComboSettings = {
                    navController.navigate(CombinationSettingsRoute)
                },
                navigateToSubstanceColors = {
                    navController.navigate(SubstanceColorsRoute)
                },
                navigateToCustomUnits = {
                    navController.navigate(CustomUnitsRoute)
                },
                navigateToWebhook = {
                  navController.navigate(WebhooksScreenRoute)
                },
            )
        }
        composableWithTransitions<FAQRoute> { FAQScreen() }
        composableWithTransitions<CombinationSettingsRoute> { CombinationSettingsScreen() }
        composableWithTransitions<SubstanceColorsRoute> { SubstanceColorsScreen() }
        composableWithTransitions<WebhooksScreenRoute> {
            WebhooksScreen(
                navigateToEditPreset = { presetId ->
                    navController.navigate(EditWebhookPresetRoute(presetId))
                },
                navigateToArchive = {
                    navController.navigate(WebhookArchiveRoute)
                }
            )
        }
        composableWithTransitions<EditWebhookPresetRoute> { backStackEntry ->
            val presetId = backStackEntry.arguments?.getInt("presetId") ?: return@composableWithTransitions
            EditWebhookPresetScreen(
                presetId = presetId,
                navigateBack = navController::popBackStack
            )
        }
        composableWithTransitions<WebhookArchiveRoute> {
            WebhookArchiveScreen()
        }
        composableWithTransitions<CustomUnitArchiveRoute> {
            CustomUnitArchiveScreen(navigateToEditCustomUnit = { customUnitId ->
                navController.navigate(EditCustomUnitRoute(customUnitId))
            })
        }
        addCustomUnitGraph(navController)
        composableWithTransitions<CustomUnitsRoute> {
            CustomUnitsScreen(
                navigateToAddCustomUnit = {
                    navController.navigate(AddCustomUnitsParentRoute)
                },
                navigateToEditCustomUnit = { customUnitId ->
                    navController.navigate(EditCustomUnitRoute(customUnitId))
                },
                navigateToCustomUnitArchive = {
                    navController.navigate(CustomUnitArchiveRoute)
                }
            )
        }
        composableWithTransitions<EditCustomUnitRoute> {
            EditCustomUnitScreen(navigateBack = navController::popBackStack)
        }
    }
}

@Serializable
object SettingsScreenRoute

@Serializable
object FAQRoute

@Serializable
object CombinationSettingsRoute

@Serializable
object SubstanceColorsRoute

@Serializable
object CustomUnitArchiveRoute

@Serializable
object CustomUnitsRoute

@Serializable
object WebhooksScreenRoute

@Serializable
data class EditWebhookPresetRoute(val presetId: Int)

@Serializable
object WebhookArchiveRoute

@Serializable
data class EditCustomUnitRoute(val customUnitId: Int)
