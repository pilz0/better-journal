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

import foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit.EditSubstanceGroupScreen
import foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit.AddSubstanceGroupScreen
import foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit.AddGroupItemPickerScreen
import foo.pilz.freaklog.ui.tabs.settings.substancegroups.edit.AddGroupItemConfigScreen
import foo.pilz.freaklog.ui.tabs.settings.substancegroups.SubstanceGroupsScreen
import foo.pilz.freaklog.ui.tabs.settings.customsubstances.CustomSubstancesScreen
import foo.pilz.freaklog.ui.tabs.search.custom.AddCustomSubstanceScreen
import androidx.navigation.toRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigation
import foo.pilz.freaklog.ui.main.navigation.SettingsTopLevelRoute
import foo.pilz.freaklog.ui.main.navigation.composableWithTransitions
import foo.pilz.freaklog.ui.tabs.settings.AiAssistantSettingsScreen
import foo.pilz.freaklog.ui.tabs.settings.FAQScreen
import foo.pilz.freaklog.ui.tabs.settings.SettingsScreen
import foo.pilz.freaklog.ui.tabs.settings.colors.SubstanceColorsScreen
import foo.pilz.freaklog.ui.tabs.settings.combinations.CombinationSettingsScreen
import foo.pilz.freaklog.ui.tabs.settings.customunits.CustomUnitsScreen
import foo.pilz.freaklog.ui.tabs.settings.customunits.archive.CustomUnitArchiveScreen
import foo.pilz.freaklog.ui.tabs.settings.customunits.edit.EditCustomUnitScreen
import foo.pilz.freaklog.ui.tabs.settings.freakquery.FreakQueryShellScreen
import foo.pilz.freaklog.ui.tabs.settings.funny.AchievementsScreen
import foo.pilz.freaklog.ui.tabs.settings.reminders.EditReminderScreen
import foo.pilz.freaklog.ui.tabs.settings.reminders.RemindersScreen
import foo.pilz.freaklog.ui.tabs.settings.customformulations.CustomFormulationsScreen
import foo.pilz.freaklog.ui.tabs.settings.customformulations.add.AddCustomFormulationScreen
import foo.pilz.freaklog.ui.tabs.settings.webhooks.WebhookEditorScreen
import foo.pilz.freaklog.ui.tabs.settings.webhooks.WebhooksListScreen
import kotlinx.serialization.Serializable

@Suppress("LongMethod")
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
                navigateToCustomSubstances = { navController.navigate(CustomSubstancesRoute) },
                navigateToSubstanceGroups = { navController.navigate(SubstanceGroupsRoute) },
                navigateToCustomFormulations = {
                    navController.navigate(CustomFormulationsRoute)
                },
                navigateToWebhook = {
                  navController.navigate(WebhooksListRoute)
                },
                navigateToReminders = {
                    navController.navigate(RemindersScreenRoute)
                },
                navigateToAchievements = {
                    navController.navigate(AchievementsRoute)
                },
                navigateToIntakeLimits = {
                    navController.navigate(IntakeLimitsRoute)
                },
                navigateToExportBackup = {
                    navController.navigate(ExportBackupRoute)
                },
                navigateToFreakQueryShell = {
                    navController.navigate(FreakQueryShellRoute)
                },
                navigateToAiAssistantSettings = {
                    navController.navigate(AiAssistantSettingsRoute)
                }
            )
        }
        composableWithTransitions<CustomSubstancesRoute> {
            CustomSubstancesScreen(
                navigateToAddCustomSubstance = { navController.navigate(AddCustomSubstanceRouteOnSettingsGraph) },
                navigateToEditCustomSubstance = { id -> navController.navigate(EditCustomSubstanceRoute(id)) },
            )
        }
        composableWithTransitions<AddCustomSubstanceRouteOnSettingsGraph> {
            AddCustomSubstanceScreen(
                navigateBack = navController::popBackStack,
                navigateToCustomDurationScreen = { substanceId, substanceName ->
                    navController.navigate(CustomDurationRoute(substanceId, substanceName))
                },
                navigateToCategoriesPicker = { id ->
                    navController.navigate(CustomCategoriesPickerRoute(id))
                },
                navigateToInteractionsList = { id ->
                    navController.navigate(CustomInteractionsListRoute(id))
                },
                navigateToToleranceEditor = { id ->
                    navController.navigate(CustomToleranceEditorRoute(id))
                },
                navigateToRisksEditor = { id ->
                    navController.navigate(CustomRisksEditorRoute(id))
                },
            )
        }
        composableWithTransitions<SubstanceGroupsRoute> {
            SubstanceGroupsScreen(
                navigateToAddSubstanceGroup = { navController.navigate(AddSubstanceGroupRoute) },
                navigateToEditSubstanceGroup = { id -> navController.navigate(EditSubstanceGroupRoute(id)) },
            )
        }
        composableWithTransitions<AddSubstanceGroupRoute> {
            AddSubstanceGroupScreen(
                navigateBack = navController::popBackStack,
                navigateToAddItem = { groupId -> navController.navigate(AddGroupItemPickerRoute(groupId)) },
            )
        }
        composableWithTransitions<EditSubstanceGroupRoute> {
            EditSubstanceGroupScreen(
                navigateBack = navController::popBackStack,
                navigateToAddItem = { groupId -> navController.navigate(AddGroupItemPickerRoute(groupId)) },
            )
        }
        composableWithTransitions<AddGroupItemPickerRoute> { backStackEntry ->
            val pickerRoute = backStackEntry.toRoute<AddGroupItemPickerRoute>()
            AddGroupItemPickerScreen(
                navigateBack = navController::popBackStack,
                navigateToConfig = { substanceName, isCustom ->
                    navController.navigate(
                        AddGroupItemConfigRoute(
                            groupId = pickerRoute.groupId,
                            substanceName = substanceName,
                            isCustomSubstance = isCustom,
                        )
                    )
                },
            )
        }
        composableWithTransitions<AddGroupItemConfigRoute> { backStackEntry ->
            val configRoute = backStackEntry.toRoute<AddGroupItemConfigRoute>()
            AddGroupItemConfigScreen(
                navigateBack = navController::popBackStack,
                onSaved = {
                    navController.popBackStack(
                        route = AddGroupItemPickerRoute(configRoute.groupId),
                        inclusive = true,
                    )
                },
            )
        }
        composableWithTransitions<AiAssistantSettingsRoute> {
            AiAssistantSettingsScreen(navigateBack = navController::popBackStack)
        }
        composableWithTransitions<RemindersScreenRoute> {
            RemindersScreen(
                navigateBack = navController::popBackStack,
                navigateToEdit = { reminderId ->
                    navController.navigate(EditReminderRoute(reminderId))
                }
            )
        }
        composableWithTransitions<EditReminderRoute> {
            EditReminderScreen(navigateBack = navController::popBackStack)
        }
        composableWithTransitions<FAQRoute> { FAQScreen() }
        composableWithTransitions<FreakQueryShellRoute> {
            FreakQueryShellScreen(navigateBack = navController::popBackStack)
        }
        composableWithTransitions<AchievementsRoute> { AchievementsScreen() }
        composableWithTransitions<CombinationSettingsRoute> { CombinationSettingsScreen() }
        composableWithTransitions<SubstanceColorsRoute> { SubstanceColorsScreen() }
        composableWithTransitions<WebhooksListRoute> {
            WebhooksListScreen(
                navigateBack = navController::popBackStack,
                navigateToEditor = { id ->
                    navController.navigate(WebhookEditorRoute(id ?: -1))
                }
            )
        }
        composableWithTransitions<WebhookEditorRoute> {
            WebhookEditorScreen(navigateBack = navController::popBackStack)
        }
        composableWithTransitions<ExportBackupRoute> {
            foo.pilz.freaklog.ui.tabs.settings.exportbackup.ExportBackupScreen()
        }
        addIntakeLimitGraph(navController)
        composableWithTransitions<IntakeLimitsRoute> {
            foo.pilz.freaklog.ui.tabs.settings.intakelimits.IntakeLimitsScreen(
                navigateToAddIntakeLimit = { navController.navigate(AddIntakeLimitParentRoute) },
                navigateToEditIntakeLimit = { limitId ->
                    navController.navigate(EditIntakeLimitRoute(limitId = limitId))
                }
            )
        }
        composableWithTransitions<EditIntakeLimitRoute> {
            foo.pilz.freaklog.ui.tabs.settings.intakelimits.EditIntakeLimitScreen(
                navigateBack = {
                    navController.popBackStack(route = IntakeLimitsRoute, inclusive = false)
                }
            )
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
        composableWithTransitions<CustomFormulationsRoute> {
            CustomFormulationsScreen(
                navigateBack = navController::popBackStack,
                navigateToAdd = { navController.navigate(AddCustomFormulationRoute) }
            )
        }
        composableWithTransitions<AddCustomFormulationRoute> {
            AddCustomFormulationScreen(navigateBack = navController::popBackStack)
        }
    }
}

@Serializable
object SettingsScreenRoute

@Serializable
object FAQRoute

@Serializable
object FreakQueryShellRoute

@Serializable
object CombinationSettingsRoute

@Serializable
object SubstanceColorsRoute

@Serializable
object CustomUnitArchiveRoute

@Serializable
object CustomUnitsRoute

@Serializable
object ExportBackupRoute

@Serializable
object IntakeLimitsRoute

@Serializable
data class EditIntakeLimitRoute(
    val limitId: Int = -1,
    val substanceName: String = "",
)

@Serializable
object WebhooksListRoute

/**
 * Editor route for a single webhook. [webhookId] is `-1` when creating a new
 * webhook (Compose Navigation cannot serialize nullable primitives, so we use
 * a sentinel rather than a nullable Int).
 */
@Serializable
data class WebhookEditorRoute(val webhookId: Int)

@Serializable
object RemindersScreenRoute

@Serializable
data class EditReminderRoute(val reminderId: Int)

@Serializable
object AchievementsRoute

@Serializable
data class EditCustomUnitRoute(val customUnitId: Int)

@Serializable
object AiAssistantSettingsRoute

@Serializable
object CustomFormulationsRoute

@Serializable
object AddCustomFormulationRoute

@Serializable
object CustomSubstancesRoute

@Serializable
object AddCustomSubstanceRouteOnSettingsGraph

@Serializable
object SubstanceGroupsRoute

@Serializable
object AddSubstanceGroupRoute

@Serializable
data class EditSubstanceGroupRoute(val groupId: Int)

@Serializable
data class AddGroupItemPickerRoute(val groupId: Int)

@Serializable
data class AddGroupItemConfigRoute(
    val groupId: Int,
    val substanceName: String,
    val isCustomSubstance: Boolean,
)
