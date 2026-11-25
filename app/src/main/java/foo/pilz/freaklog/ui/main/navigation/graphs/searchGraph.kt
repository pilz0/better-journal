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
import androidx.navigation.toRoute
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.ui.main.navigation.DrugsTopLevelRoute
import foo.pilz.freaklog.ui.main.navigation.composableWithTransitions
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.ExplainTimelineScreen
import foo.pilz.freaklog.ui.tabs.safer.DoseExplanationScreen
import foo.pilz.freaklog.ui.tabs.safer.VolumetricDosingScreen
import foo.pilz.freaklog.ui.tabs.search.SearchScreen
import foo.pilz.freaklog.ui.tabs.search.custom.AddCustomSubstanceScreen
import foo.pilz.freaklog.ui.tabs.search.custom.CustomSubstanceScreen
import foo.pilz.freaklog.ui.tabs.search.substance.SubstanceScreen
import foo.pilz.freaklog.ui.tabs.search.substance.category.CategoryScreen
import kotlinx.serialization.Serializable

fun NavGraphBuilder.searchGraph(navController: NavHostController) {
  navigation<DrugsTopLevelRoute>(
      startDestination = DrugsScreenRoute,
  ) {
    composableWithTransitions<DrugsScreenRoute> {
      SearchScreen(
          onSubstanceTap = { substanceModel ->
            navController.navigate(SubstanceRoute(substanceName = substanceModel.name))
          },
          onCustomSubstanceTap = { customSubstanceId ->
            navController.navigate(CustomSubstanceRoute(customSubstanceId))
          },
          navigateToAddCustomSubstanceScreen = {
            navController.navigate(AddCustomSubstanceRouteOnSearchGraph)
          },
          navigateToAnodyneSearch = { query ->
            navController.navigate(AnodyneSearchRoute(query = query))
          },
      )
    }
    composableWithTransitions<AnodyneSearchRoute> {
      foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneSearchScreen(
          navigateBack = navController::popBackStack,
          navigateToSubstance = { title ->
            navController.navigate(AnodyneSubstanceRoute(title = title))
          },
      )
    }
    composableWithTransitions<AnodyneSubstanceRoute> {
      foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneSubstanceScreen(
          navigateBack = navController::popBackStack,
          navigateToPsychonautSubstance = { name ->
            navController.navigate(SubstanceRoute(substanceName = name))
          },
          navigateToCustomSubstance = { id ->
            navController.navigate(CustomSubstanceRoute(customSubstanceId = id))
          },
          navigateToUserNotes = { substanceName ->
            navController.navigate(AnodyneUserNotesRoute(substanceName = substanceName))
          },
      )
    }
    composableWithTransitions<AnodyneUserNotesRoute> {
      foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneUserNotesScreen(
          navigateBack = navController::popBackStack,
          navigateToNoteDetail = { user, substanceName ->
            navController.navigate(
                AnodyneUserNoteDetailRoute(user = user, substanceName = substanceName)
            )
          },
      )
    }
    composableWithTransitions<AnodyneUserNoteDetailRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<AnodyneUserNoteDetailRoute>()
      foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneUserNoteScreen(
          user = route.user,
          substanceName = route.substanceName,
          navigateBack = navController::popBackStack,
          navigateToAnodyneSubstance = { title ->
            navController.navigate(AnodyneSubstanceRoute(title = title))
          },
      )
    }
    composableWithTransitions<AnodyneLinksRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<AnodyneLinksRoute>()
      foo.pilz.freaklog.ui.tabs.search.anodyne.AnodyneLinksScreen(
          title = route.title,
          url = route.url,
          navigateBack = navController::popBackStack,
      )
    }
    composableWithTransitions<SubstanceRoute> {
      SubstanceScreen(
          navigateToDosageExplanationScreen = {
            navController.navigate(DosageExplanationRouteOnSearchTab)
          },
          navigateToSaferHallucinogensScreen = {
            navController.navigate(SaferHallucinogensRoute)
          },
          navigateToSaferStimulantsScreen = {
            navController.navigate(SaferStimulantsRoute)
          },
          navigateToExplainTimeline = {
            navController.navigate(ExplainTimelineOnSearchTabRoute)
          },
          navigateToCategoryScreen = { categoryName ->
            navController.navigate(CategoryRoute(categoryName))
          },
          navigateToVolumetricDosingScreen = {
            navController.navigate(VolumetricDosingOnSearchTabRoute)
          },
          navigateToUserNotes = { substanceName ->
            navController.navigate(AnodyneUserNotesRoute(substanceName = substanceName))
          },
      )
    }
    composableWithTransitions<CategoryRoute> {
      CategoryScreen()
    }
    composableWithTransitions<CustomSubstanceRoute> {
      CustomSubstanceScreen(
          navigateBack = navController::popBackStack,
          navigateToEdit = { id ->
            navController.navigate(EditCustomSubstanceRoute(id))
          },
          navigateToUserNotes = { substanceName ->
            navController.navigate(AnodyneUserNotesRoute(substanceName = substanceName))
          },
      )
    }
    customSubstanceEditorDestinations(navController)
    composableWithTransitions<AddCustomSubstanceRouteOnSearchGraph> {
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
    composableWithTransitions<VolumetricDosingOnSearchTabRoute> {
      VolumetricDosingScreen()
    }
    composableWithTransitions<ExplainTimelineOnSearchTabRoute> { ExplainTimelineScreen() }

    composableWithTransitions<DosageExplanationRouteOnSearchTab> { DoseExplanationScreen() }
  }
}

@Serializable object DrugsScreenRoute

@Serializable data class SubstanceRoute(val substanceName: String)

@Serializable data class CategoryRoute(val categoryName: String)

@Serializable data class EditCustomSubstanceRoute(val customSubstanceId: Int)

@Serializable data class CustomSubstanceRoute(val customSubstanceId: Int)

@Serializable data class CustomCategoriesPickerRoute(val customSubstanceId: Int)

@Serializable data class CustomInteractionsListRoute(val customSubstanceId: Int)

@Serializable
data class CustomInteractionsPickerRoute(
    val customSubstanceId: Int,
    val severity: CustomInteractionSeverity,
)

@Serializable data class CustomToleranceEditorRoute(val customSubstanceId: Int)

@Serializable data class CustomCrossTolerancePickerRoute(val customSubstanceId: Int)

@Serializable data class CustomRisksEditorRoute(val customSubstanceId: Int)

@Serializable data class CustomDurationRoute(val substanceId: Int, val substanceName: String)

@Serializable
data class CustomDurationEditorRoute(
    val substanceId: Int,
    val substanceName: String,
    val route: AdministrationRoute,
)

@Serializable object AddCustomSubstanceRouteOnSearchGraph

@Serializable object VolumetricDosingOnSearchTabRoute

@Serializable object ExplainTimelineOnSearchTabRoute

@Serializable object DosageExplanationRouteOnSearchTab

@Serializable data class AnodyneSearchRoute(val query: String = "")

@Serializable data class AnodyneSubstanceRoute(val title: String)

@Serializable data class AnodyneUserNotesRoute(val substanceName: String)

@Serializable data class AnodyneUserNoteDetailRoute(val user: String, val substanceName: String)

@Serializable data class AnodyneLinksRoute(val title: String, val url: String = "")
