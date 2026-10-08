package foo.pilz.freaklog.ui.tabs.search.custom.customdurations

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.ui.theme.LocalSpacing

@Preview
@Composable
@SuppressLint("UnusedContentLambdaTargetStateParameter")
fun CustomDurationScreenPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(true) {
            CustomDurationScreen(
                substanceName = "Ibuprofen",
                durations = listOf(
                    CustomRoaDuration(
                        route = AdministrationRoute.ORAL,
                        onset = DurationRange(min = 1f, max = null, units = DurationUnits.HOURS),
                        comeup = DurationRange(
                            min = 20f,
                            max = null,
                            units = DurationUnits.MINUTES
                        ),
                        peak = DurationRange(min = 1f, max = null, units = DurationUnits.HOURS),
                        offset = DurationRange(
                            min = 30f,
                            max = null,
                            units = DurationUnits.MINUTES
                        ),
                        total = null,
                    ),
                    CustomRoaDuration(
                        route = AdministrationRoute.INSUFFLATED,
                        onset = DurationRange(min = 15f, max = null, units = DurationUnits.MINUTES),
                        comeup = DurationRange(
                            min = 10f,
                            max = null,
                            units = DurationUnits.MINUTES
                        ),
                        peak = DurationRange(min = 1f, max = null, units = DurationUnits.HOURS),
                        offset = DurationRange(
                            min = 30f,
                            max = null,
                            units = DurationUnits.MINUTES
                        ),
                        total = null,
                    ),
                    CustomRoaDuration(
                        route = AdministrationRoute.BUCCAL,
                        onset = DurationRange(min = 20f, max = null, units = DurationUnits.MINUTES),
                        comeup = DurationRange(
                            min = 45f,
                            max = null,
                            units = DurationUnits.MINUTES
                        ),
                        peak = null,
                        offset = null,
                        total = DurationRange(min = 7f, max = null, units = DurationUnits.HOURS),
                    ),
                    CustomRoaDuration(
                        route = AdministrationRoute.RECTAL,
                        onset = null,
                        comeup = null,
                        peak = null,
                        offset = null,
                        total = DurationRange(min = 7f, max = null, units = DurationUnits.HOURS),
                    ),
                    CustomRoaDuration(
                        route = AdministrationRoute.SUBLINGUAL,
                        onset = DurationRange(min = 5f, max = null, units = DurationUnits.MINUTES),
                        comeup = DurationRange(
                            min = 10f,
                            max = null,
                            units = DurationUnits.MINUTES
                        ),
                        peak = DurationRange(min = 1f, max = null, units = DurationUnits.HOURS),
                        offset = null,
                        total = DurationRange(min = 2f, max = null, units = DurationUnits.HOURS),
                    ),
                    CustomRoaDuration(
                        route = AdministrationRoute.TRANSDERMAL,
                        onset = null,
                        comeup = null,
                        peak = DurationRange(min = 1f, max = null, units = DurationUnits.HOURS),
                        offset = null,
                        total = null,
                    ),
                ),
                doses = listOf(
                    CustomRoaDose(
                        route = AdministrationRoute.INSUFFLATED,
                        units = "mg",
                        lightMin = 20.0,
                    ),
                    CustomRoaDose(
                        route = AdministrationRoute.BUCCAL,
                        units = "mg",
                        lightMin = 50.0,
                        commonMin = 200.0,
                        strongMin = 600.0,
                        heavyMin = 1000.0,
                    ),
                    CustomRoaDose(
                        route = AdministrationRoute.RECTAL,
                        units = "mg",
                        strongMin = 15.0,
                    ),
                    CustomRoaDose(
                        route = AdministrationRoute.TRANSDERMAL,
                        units = "mg",
                        heavyMin = 500.0,
                    ),
                ),
                navigateBack = { },
                navigateToEditor = { },
                animatedVisibilityScope = this@AnimatedVisibility,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
        }
    }
}

@Composable
fun CustomDurationScreen(
    viewModel: CustomDurationViewModel = hiltViewModel(),
    navigateBack: () -> Unit,
    navigateToEditor: (substanceId: Int, substanceName: String, route: AdministrationRoute) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) = CustomDurationScreen(
    durations = viewModel.durations.collectAsStateWithLifecycle().value,
    doses = viewModel.doses.collectAsStateWithLifecycle().value,
    substanceName = viewModel.substanceName,
    navigateBack = navigateBack,
    navigateToEditor = { route: AdministrationRoute ->
        navigateToEditor(
            viewModel.substanceId,
            viewModel.substanceName,
            route,
        )
    },
    sharedTransitionScope = sharedTransitionScope,
    animatedVisibilityScope = animatedVisibilityScope,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDurationScreen(
    durations: List<CustomRoaDuration>,
    doses: List<CustomRoaDose>,
    substanceName: String,
    navigateBack: () -> Unit,
    navigateToEditor: (route: AdministrationRoute) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val spacing = LocalSpacing.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column() {
                        Text("Ingestion durations")
                        Text(substanceName, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.Default.ChevronLeft, "Go back")
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState),
        ) {
            ElevatedCard(modifier = Modifier.padding(horizontal = spacing.sm)) {
                Text(
                    text = "You can specify custom effect durations for an administration " +
                            "route here. You don't need to fill out all values, a best-effort " +
                            "duration graph is generated from the provided fields.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(spacing.sm)
                )
            }

            enumValues<AdministrationRoute>().forEach { route ->
                val duration = durations.find { it.route == route }
                val dose = doses.find { it.route == route }

                CustomDurationRow(
                    route = route,
                    duration = duration,
                    dose = dose,
                    onClick = { navigateToEditor(route) },
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }
}
