package foo.pilz.freaklog.ui.tabs.search.custom.customdurations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDose
import foo.pilz.freaklog.data.room.experiences.entities.CustomRoaDuration
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.DurationRange
import foo.pilz.freaklog.data.substances.classes.roa.DurationUnits
import foo.pilz.freaklog.ui.graph.paint.paintScene
import foo.pilz.freaklog.ui.graph.scene.builders.RawIngestion
import foo.pilz.freaklog.ui.graph.scene.builders.TimelineGroup
import foo.pilz.freaklog.ui.graph.scene.builders.TimelineShape
import foo.pilz.freaklog.ui.graph.scene.builders.buildRawCurve
import foo.pilz.freaklog.ui.graph.scene.builders.buildTimelineScene
import foo.pilz.freaklog.ui.graph.scene.builders.normalizeCurve
import foo.pilz.freaklog.ui.graph.scene.builders.selectTimelineShape
import foo.pilz.freaklog.ui.graph.style.LocalGraphStyle
import foo.pilz.freaklog.ui.tabs.search.substance.roa.dose.DoseClassificationRow
import foo.pilz.freaklog.ui.theme.LocalSpacing

@Preview
@Composable
fun CustomDurationRowPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(true) {
            CustomDurationRow(
                route = AdministrationRoute.INSUFFLATED,
                duration = CustomRoaDuration(
                    route = AdministrationRoute.INSUFFLATED,
                    onset = DurationRange(min = 10.0f, max = null, units = DurationUnits.MINUTES),
                    comeup = DurationRange(min = 20.0f, max = null, units = DurationUnits.MINUTES),
                    peak = DurationRange(min = 25.0f, max = null, units = DurationUnits.MINUTES),
                    offset = DurationRange(min = 20.0f, max = null, units = DurationUnits.MINUTES),
                    total = DurationRange(min = 75.0f, max = null, units = DurationUnits.MINUTES),
                ),
                dose = CustomRoaDose(
                    route = AdministrationRoute.INSUFFLATED,
                    units = "mg",
                    lightMin = 20.0,
                    heavyMin = 120.0,
                ),
                onClick = { },
                animatedVisibilityScope = this@AnimatedVisibility,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
        }
    }
}

@Preview
@Composable
fun CustomDurationRowPreview2() {
    SharedTransitionLayout {
        AnimatedVisibility(true) {
            CustomDurationRow(
                route = AdministrationRoute.INSUFFLATED,
                duration = null,
                dose = null,
                onClick = { },
                animatedVisibilityScope = this@AnimatedVisibility,
                sharedTransitionScope = this@SharedTransitionLayout,
            )
        }
    }
}

@Composable
fun CustomDurationRow(
    route: AdministrationRoute,
    duration: CustomRoaDuration?,
    dose: CustomRoaDose?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val spacing = LocalSpacing.current
    val density = LocalDensity.current

    ListItem(
        modifier = modifier
            .clickable(onClick = onClick),
        headlineContent = { Text(route.displayText) },
        supportingContent = {
            if (duration != null || dose != null) {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density.density,
                        density.fontScale / 1.3f
                    )
                ) {
                    DoseClassificationRow(
                        lightMin = dose?.lightMin,
                        commonMin = dose?.commonMin,
                        strongMin = dose?.strongMin,
                        heavyMin = dose?.heavyMin,
                        unit = "", // for some deranged reason the unit line wraps if displayed
                        modifier = Modifier
                            .width(IntrinsicSize.Min),
                    )
                }
            } else {
                Text(route.description)
            }
        },
        trailingContent = {
            if (duration != null || dose != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (duration != null) {
                        DurationPreviewGraph(
                            duration,
                            modifier = Modifier
                                .width(60.dp)
                                .height(24.dp),
                            sharedTransitionScope,
                            animatedVisibilityScope,
                        )
                        Spacer(modifier = Modifier.width(spacing.md))
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        null,
                    )
                }
            } else {
                Icon(
                    Icons.Filled.Add,
                    null,
                )
            }
        }
    )
}

@Composable
fun DurationPreviewGraph(
    customDuration: CustomRoaDuration,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val duration = customDuration.toRoaDuration()
    val shape = selectTimelineShape(duration)
    if (shape is TimelineShape.None) return

    val isDark = isSystemInDarkTheme()
    val baseStyle = LocalGraphStyle.current
    val textMeasurer = rememberTextMeasurer()

    val ingestion = RawIngestion(startSeconds = 0f, endSeconds = null, horizontalWeight = 0.5f, height = 1f)
    val rawCurve = buildRawCurve(shape, listOf(ingestion))
    val widthSeconds = duration.totalDurationInSeconds(0.5f, false)
    val curve = normalizeCurve(rawCurve, rawCurve.nonNormalisedHeight, widthSeconds)

    val previewStyle = baseStyle.copy(
        strokeWidth = baseStyle.strokeWidth / 2,
        dotRadius = baseStyle.dotRadius / 2,
    )
    val group = TimelineGroup(
        color = customDuration.route.color.getComposeColor(isDark),
        curve = curve,
        timeRanges = emptyList(),
    )
    val scene = buildTimelineScene(listOf(group), widthSeconds, previewStyle)

    with(sharedTransitionScope) {
        Canvas(
            modifier = modifier.sharedBounds(
                rememberSharedContentState(key = "timeline-${customDuration.route.name}"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        ) {
            paintScene(scene, previewStyle, textMeasurer)
        }
    }
}
