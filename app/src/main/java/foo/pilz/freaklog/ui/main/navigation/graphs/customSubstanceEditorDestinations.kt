package foo.pilz.freaklog.ui.main.navigation.graphs

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import foo.pilz.freaklog.ui.main.navigation.composableWithTransitions
import foo.pilz.freaklog.ui.tabs.search.custom.CustomCategoriesPickerScreen
import foo.pilz.freaklog.ui.tabs.search.custom.CustomCrossTolerancePickerScreen
import foo.pilz.freaklog.ui.tabs.search.custom.CustomInteractionsListScreen
import foo.pilz.freaklog.ui.tabs.search.custom.CustomInteractionsPickerScreen
import foo.pilz.freaklog.ui.tabs.search.custom.CustomRisksEditorScreen
import foo.pilz.freaklog.ui.tabs.search.custom.CustomToleranceEditorScreen
import foo.pilz.freaklog.ui.tabs.search.custom.EditCustomSubstanceScreen
import foo.pilz.freaklog.ui.tabs.search.custom.customdurations.CustomDurationEditorScreen
import foo.pilz.freaklog.ui.tabs.search.custom.customdurations.CustomDurationScreen
import foo.pilz.freaklog.ui.theme.LocalSharedTransitionScope

/**
 * The custom substance editor and its sub-screens. Registered in both the Drugs and the Settings
 * graph: navigation resolves a route in the current tab's graph first, so opened from Settings the
 * editor stays in the Settings tab (tab highlight, re-tap and back all behave) instead of jumping
 * into the Drugs tab.
 */
fun NavGraphBuilder.customSubstanceEditorDestinations(navController: NavController) {
  composableWithTransitions<EditCustomSubstanceRoute> {
    EditCustomSubstanceScreen(
        navigateBack = navController::popBackStack,
        navigateToCustomDurationScreen = { substanceId, substanceName ->
          navController.navigate(CustomDurationRoute(substanceId, substanceName))
        },
        navigateToCategoriesPicker = { customSubstanceId ->
          navController.navigate(CustomCategoriesPickerRoute(customSubstanceId))
        },
        navigateToInteractionsList = { customSubstanceId ->
          navController.navigate(CustomInteractionsListRoute(customSubstanceId))
        },
        navigateToToleranceEditor = { customSubstanceId ->
          navController.navigate(CustomToleranceEditorRoute(customSubstanceId))
        },
        navigateToRisksEditor = { customSubstanceId ->
          navController.navigate(CustomRisksEditorRoute(customSubstanceId))
        },
    )
  }
  composableWithTransitions<CustomRisksEditorRoute> {
    CustomRisksEditorScreen(navigateBack = navController::popBackStack)
  }
  composableWithTransitions<CustomToleranceEditorRoute> {
    CustomToleranceEditorScreen(
        navigateBack = navController::popBackStack,
        navigateToCrossTolerancePicker = { customSubstanceId ->
          navController.navigate(CustomCrossTolerancePickerRoute(customSubstanceId))
        },
    )
  }
  composableWithTransitions<CustomCrossTolerancePickerRoute> {
    CustomCrossTolerancePickerScreen(navigateBack = navController::popBackStack)
  }
  composableWithTransitions<CustomCategoriesPickerRoute> {
    CustomCategoriesPickerScreen(navigateBack = navController::popBackStack)
  }
  composableWithTransitions<CustomInteractionsListRoute> {
    CustomInteractionsListScreen(
        navigateBack = navController::popBackStack,
        navigateToPicker = { customSubstanceId, severity ->
          navController.navigate(CustomInteractionsPickerRoute(customSubstanceId, severity))
        },
    )
  }
  composableWithTransitions<CustomInteractionsPickerRoute> {
    CustomInteractionsPickerScreen(navigateBack = navController::popBackStack)
  }
  composableWithTransitions<CustomDurationRoute> {
    CustomDurationScreen(
        navigateBack = navController::popBackStack,
        navigateToEditor = { substanceId, substanceName, route ->
          navController.navigate(CustomDurationEditorRoute(substanceId, substanceName, route))
        },
        animatedVisibilityScope = this@composableWithTransitions,
        sharedTransitionScope = LocalSharedTransitionScope.current,
    )
  }
  composableWithTransitions<CustomDurationEditorRoute> {
    CustomDurationEditorScreen(
        navigateBack = navController::popBackStack,
        animatedVisibilityScope = this@composableWithTransitions,
        sharedTransitionScope = LocalSharedTransitionScope.current,
    )
  }
}
